package nep.timeline.cirno.services

import android.system.ErrnoException
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
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

object BinderService {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    @JvmField
    var received: Boolean = false

    private var isRunning = false
    private const val NETLINK_UNIT_DEFAULT = 22
    private const val NETLINK_UNIT_MAX = 26

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

        executorService.execute {
            try {
                val netlinkUnit: Int
                val configNetlinkUnit = GlobalVars.globalSettings!!.netlinkUnit
                if (configNetlinkUnit >= NETLINK_UNIT_DEFAULT && configNetlinkUnit <= NETLINK_UNIT_MAX) {
                    netlinkUnit = configNetlinkUnit
                } else {
                    val dir = File("/proc/rekernel")
                    if (dir.exists()) {
                        val files = dir.listFiles()
                        if (files == null) {
                            Log.e("找不到ReKernel单元")
                            return@execute
                        }
                        val unitFile = files[0]
                        netlinkUnit = StringUtils.StringToInteger(unitFile.name)
                    } else netlinkUnit = NETLINK_UNIT_DEFAULT
                }

                NetlinkClient(classLoader, netlinkUnit).use { netlinkClient ->
                    if (!netlinkClient.mDescriptor.valid()) {
                        Log.e("无法连接至ReKernel服务器")
                        return@use
                    }

                    netlinkClient.bind(NetlinkSocketAddress(100).toInstance() as SocketAddress)

                    isRunning = true

                    Log.i("已连接至ReKernel, " + netlinkUnit + "#100")

                    while (true) {
                        try {
                            val byteBuffer = netlinkClient.recvMessage()
                            val data = String(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit(), StandardCharsets.UTF_8)
                            if (data.isNotEmpty()) {
                                val params = parseParams(data.substring(data.indexOf("type"), data.lastIndexOf(";")))
                                if (params.containsKey("type") && !received) {
                                    Log.i("成功接收到来自ReKernel的消息")
                                    received = true
                                }
                                Handlers.rekernel.post {
                                    val type = params["type"]
                                    if (type!! == "Binder") {
                                        val bindertype = params["bindertype"]
                                        val oneway = StringUtils.StringToInteger(params["oneway"]!!)
                                        val targetUid = StringUtils.StringToInteger(params["target"]!!)
                                        if (oneway == 1 && bindertype!! != "free_buffer_full")
                                            return@post

                                        val appRecords: MutableList<AppRecord?>? = AppService.getByUid(targetUid)
                                        if (appRecords == null || appRecords.isEmpty())
                                            return@post
                                        for (appRecord in appRecords) {
                                            if (appRecord == null)
                                                continue

                                            FreezerService.temporaryUnfreezeIfNeed(appRecord, "内核Binder(" + (if (oneway == 1) "ASYNC" else "SYNC") + "), 类型: " + bindertype, 3000)
                                        }
                                    }
                                }
                            }
                        } catch (ignored: ErrnoException) {

                        } catch (ignored: InterruptedIOException) {

                        } catch (ignored: NumberFormatException) {

                        } catch (e: Exception) {
                            Log.e("ReKernel", e)
                        }
                    }
                }
            } catch (e: ErrnoException) {
                Log.e("无法连接至ReKernel服务器")
            } catch (e: IOException) {
                Log.e("无法连接至ReKernel服务器")
            } catch (throwable: Throwable) {
                Log.e("ReKernel", throwable)
            }
        }
    }
}
