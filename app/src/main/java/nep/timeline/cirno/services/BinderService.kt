package nep.timeline.cirno.services

import android.system.ErrnoException
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.netlink.GenericNetlink
import nep.timeline.cirno.netlink.IoUtils
import nep.timeline.cirno.netlink.NetlinkClient
import nep.timeline.cirno.netlink.NetlinkSocketAddress
import nep.timeline.cirno.threads.Handlers
import nep.timeline.cirno.utils.StringUtils
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 接收 Re:Kernel 内核模块上报的 Binder 事件，用于在内核已判定同步 Binder 时临时解冻目标进程。
 *
 * 协议支持：
 * 1. generic netlink（上游 11.x 默认）：family "rekernel" + 多播组 "events"；
 * 2. legacy raw netlink（旧版内核模块）：固定 unit 22..25 + 用户端口 100 + /proc/rekernel 发现。
 *
 * 连接失败或中断后按退避重连，避免内核模块重载/重启后永久失效。
 */
object BinderService {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val sequence = AtomicInteger(1)

    @JvmField
    var received: Boolean = false

    private var isRunning = false

    /** legacy 协议：上游 `for (unit = 22; unit < 26; unit++)`，所以实际只会是 22..25 */
    private const val NETLINK_UNIT_MIN = 22
    private const val NETLINK_UNIT_MAX = 25
    private const val LEGACY_USER_PORT = 100
    private const val GENL_RESOLVE_TIMEOUT_MS = 1_000L
    private const val GENL_RESOLVE_ATTEMPTS = 3
    private const val RECONNECT_DELAY_MS = 5_000L
    private const val MAX_RECONNECT_DELAY_MS = 60_000L

    /** 解冻理由里保留的临时解冻时长 */
    private const val UNFREEZE_DURATION_MS = 3000L

    private fun parseParams(message: String): MutableMap<String, String> {
        val map: MutableMap<String, String> = HashMap()
        for (keyValue in message.split(",")) {
            val split = keyValue.split("=").toTypedArray()
            if (split.size == 2)
                map[split[0].trim()] = split[1].trim()
        }

        return map
    }

    @JvmStatic
    fun start(classLoader: ClassLoader?) {
        if (isRunning)
            return
        isRunning = true

        executorService.execute {
            var failures = 0
            var reported = false

            while (true) {
                var connected = false
                try {
                    connected = connectAndReceive(classLoader)
                } catch (e: ErrnoException) {
                    if (!reported)
                        Log.e("无法连接至ReKernel服务器", e)
                } catch (e: IOException) {
                    if (!reported)
                        Log.e("无法连接至ReKernel服务器", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                } catch (e: Throwable) {
                    if (!reported)
                        Log.e("ReKernel", e)
                }

                if (connected) {
                    if (failures > 0)
                        Log.i("已重新连接至ReKernel")
                    failures = 0
                    reported = false
                } else {
                    failures++
                    if (!reported) {
                        Log.e("无法连接至ReKernel服务器")
                        reported = true
                    }
                }

                // 退避重连：未连接时逐步拉长，连接过则立即（短暂等待后）重试
                val delay = if (connected) RECONNECT_DELAY_MS else (RECONNECT_DELAY_MS * failures).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                Thread.sleep(delay)
            }
        }
    }

    /**
     * 连接并接收事件。优先 generic netlink，失败后回退 legacy。
     *
     * @return 是否成功建立过连接（true 代表失败是"连接中断"而非"连不上"）
     */
    private fun connectAndReceive(classLoader: ClassLoader?): Boolean {
        // 用户显式指定了 unit，说明设备上是旧版模块，直接走 legacy
        val pinnedUnit = GlobalVars.globalSettings?.netlinkUnit ?: 0
        if (pinnedUnit in NETLINK_UNIT_MIN..NETLINK_UNIT_MAX)
            return receiveLegacy(classLoader, pinnedUnit)

        try {
            if (receiveGeneric(classLoader))
                return true
        } catch (e: ErrnoException) {
            // 内核不支持 generic netlink（或该 family 不存在）→ 回退 legacy
            Log.d("generic netlink 不可用，尝试 legacy 协议", e)
        } catch (e: IOException) {
            Log.d("generic netlink 不可用，尝试 legacy 协议", e)
        }

        val unit = resolveLegacyUnit()
        if (unit < 0)
            return false

        return receiveLegacy(classLoader, unit)
    }

    /** generic netlink 接收循环；family 不存在时返回 false。 */
    private fun receiveGeneric(classLoader: ClassLoader?): Boolean {
        NetlinkClient(classLoader, GenericNetlink.NETLINK_GENERIC).use { client ->
            if (!client.mDescriptor.valid())
                return false

            // port id 与多播组掩码都由内核分配/后续加入
            client.bind(NetlinkSocketAddress().toInstance() as SocketAddress)

            val family = resolveFamily(client) ?: return false

            if (family.multicastGroupId > 0) {
                IoUtils.setsockoptInt(
                    classLoader, client.mDescriptor,
                    GenericNetlink.SOL_NETLINK, GenericNetlink.NETLINK_ADD_MEMBERSHIP, family.multicastGroupId
                )
            } else {
                Log.w("ReKernel 未暴露 " + GenericNetlink.MCGRP_NAME + " 多播组，将收不到事件")
            }

            Log.i("已连接至ReKernel, generic netlink(family=" + GenericNetlink.FAMILY_NAME + "#" + family.id + ")")

            val versionRequest = GenericNetlink.getVersionRequest(family.id, sequence.getAndIncrement())
            client.sendMessage(versionRequest, 0, versionRequest.size)

            while (true) {
                val reply = GenericNetlink.parseReply(client.recvMessage()) ?: continue
                when (reply.command) {
                    GenericNetlink.REKERNEL_C_EVENT -> reply.message?.let { handleEvent(it) }
                    GenericNetlink.REKERNEL_C_GET_VERSION -> Log.i("ReKernel 内核模块版本: " + (reply.message ?: "未知"))
                }
            }
        }
    }

    /** 向 nlctrl 查询 Re:Kernel family 与其多播组。 */
    private fun resolveFamily(client: NetlinkClient): GenericNetlink.FamilyInfo? {
        val request = GenericNetlink.getFamilyRequest(sequence.getAndIncrement())
        client.sendMessage(request, 0, request.size)

        repeat(GENL_RESOLVE_ATTEMPTS) {
            val reply = try {
                client.recvMessage(GENL_RESOLVE_TIMEOUT_MS)
            } catch (e: InterruptedIOException) {
                return null
            }

            val family = GenericNetlink.parseFamilyReply(reply)
            if (family != null)
                return family
        }

        return null
    }

    /** legacy raw netlink 接收循环。 */
    private fun receiveLegacy(classLoader: ClassLoader?, unit: Int): Boolean {
        if (unit < NETLINK_UNIT_MIN || unit > NETLINK_UNIT_MAX)
            return false

        NetlinkClient(classLoader, unit).use { client ->
            if (!client.mDescriptor.valid())
                return false

            client.bind(NetlinkSocketAddress(LEGACY_USER_PORT).toInstance() as SocketAddress)

            Log.i("已连接至ReKernel, legacy netlink, " + unit + "#" + LEGACY_USER_PORT)

            while (true) {
                handleEvent(readString(client.recvMessage()) ?: continue)
            }
        }
    }

    private fun readString(byteBuffer: ByteBuffer): String? {
        if (byteBuffer.limit() <= 0)
            return null

        return String(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit(), StandardCharsets.UTF_8)
    }

    /**
     * 解析 `type=Binder,bindertype=...,oneway=...,target=...;` 形式的事件。
     *
     * 目前只使用 Binder 事件（Signal / Network 事件忽略），与既有行为保持一致。
     */
    private fun handleEvent(message: String) {
        val start = message.indexOf("type")
        if (start < 0)
            return

        // legacy 报文里可能带额外前后缀，截取到最后一个分隔符为止
        val end = message.lastIndexOf(';')
        val params = parseParams(if (end > start) message.substring(start, end) else message.substring(start))

        if (params.containsKey("type") && !received) {
            Log.i("成功接收到来自ReKernel的消息")
            received = true
        }

        Handlers.rekernel.post {
            if (params["type"] != "Binder")
                return@post

            val binderType = params["bindertype"] ?: return@post
            val oneway = StringUtils.StringToInteger(params["oneway"] ?: return@post)
            val targetUid = StringUtils.StringToInteger(params["target"] ?: return@post)

            if (oneway == 1 && binderType != "free_buffer_full")
                return@post

            val appRecords: MutableList<AppRecord?> = AppService.getByUid(targetUid) ?: return@post
            if (appRecords.isEmpty())
                return@post

            for (appRecord in appRecords) {
                if (appRecord == null)
                    continue

                FreezerService.temporaryUnfreezeIfNeed(
                    appRecord,
                    "内核Binder(" + (if (oneway == 1) "ASYNC" else "SYNC") + "), 类型: " + binderType,
                    UNFREEZE_DURATION_MS
                )
            }
        }
    }

    /**
     * 探测 legacy unit：/proc/rekernel 下同时存在 `<unit>` 与 `version` 两个文件，
     * 必须只接受纯数字文件名，否则会把 "version" 解析成非法 unit。
     *
     * @return 合法的 unit，找不到时返回 -1
     */
    private fun resolveLegacyUnit(): Int {
        val dir = File("/proc/rekernel")
        val files = dir.listFiles()
        if (files == null) {
            Log.d("没有 /proc/rekernel，跳过 legacy 协议")
            return -1
        }

        for (file in files) {
            val name = file.name
            if (name.isEmpty() || !name.all { it.isDigit() })
                continue

            val unit = name.toInt()
            if (unit in NETLINK_UNIT_MIN..NETLINK_UNIT_MAX)
                return unit
        }

        return -1
    }
}
