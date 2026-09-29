package nep.timeline.cirno.netlink

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Re:Kernel 的 Generic Netlink 协议（上游 11.x 默认协议，替代旧版固定 unit 的 raw netlink）。
 *
 * ABI（与 Re:Kernel 内核模块一致）：
 * - family: [FAMILY_NAME]（"rekernel"）
 * - 多播组: [MCGRP_NAME]（"events"），加入后通过 `genlmsg_multicast` 收到事件
 * - 事件: cmd=[REKERNEL_C_EVENT]，属性 [REKERNEL_A_MSG] 为字符串，内容沿用
 *   `type=Binder,bindertype=...,oneway=...,target=...;` 这种 key=value 格式
 * - 版本查询: cmd=[REKERNEL_C_GET_VERSION]，内核单播回复 [REKERNEL_A_MSG]
 *
 * 注意：family 解析与事件解析只依赖 [ByteBuffer]，不依赖任何 android.* 类，便于单元测试。
 */
object GenericNetlink {
    /** `struct nlmsghdr` */
    const val NLMSG_HDRLEN = 16

    /** `struct genlmsghdr` */
    const val GENL_HDRLEN = 4

    /** `struct nlattr` */
    const val NLA_HDRLEN = 4

    const val NLA_TYPE_MASK = 0x3FFF

    /** `NLM_F_REQUEST` */
    const val NLM_F_REQUEST = 0x01

    /** `NETLINK_GENERIC`，即 `OsConstants` 未公开的 netlink protocol 16 */
    const val NETLINK_GENERIC = 16

    /** `SOL_NETLINK` */
    const val SOL_NETLINK = 270

    /** `NETLINK_ADD_MEMBERSHIP` */
    const val NETLINK_ADD_MEMBERSHIP = 1

    /** `GENL_ID_CTRL`（nlctrl family 的 id） */
    const val GENL_ID_CTRL = 0x10

    /** `CTRL_CMD_GETFAMILY` */
    const val CTRL_CMD_GETFAMILY = 3
    const val CTRL_ATTR_FAMILY_ID = 1
    const val CTRL_ATTR_FAMILY_NAME = 2
    const val CTRL_ATTR_MCAST_GROUPS = 7
    const val CTRL_ATTR_MCAST_GRP_NAME = 1
    const val CTRL_ATTR_MCAST_GRP_ID = 2

    const val REKERNEL_C_EVENT = 1
    const val REKERNEL_C_GET_VERSION = 4
    const val REKERNEL_A_MSG = 1

    const val FAMILY_NAME = "rekernel"
    const val MCGRP_NAME = "events"
    const val GENL_VERSION = 1

    /** family 解析结果，[multicastGroupId] 为 0 表示内核未暴露 events 多播组。 */
    data class FamilyInfo(val id: Int, val multicastGroupId: Int)

    /** 内核回复：`genlmsghdr.cmd` 与可选的 `REKERNEL_A_MSG` 载荷。 */
    data class Reply(val command: Int, val message: String?)

    private fun align4(value: Int): Int = (value + 3) and 3.inv()

    /**
     * 构造 `CTRL_CMD_GETFAMILY` 查询报文（向 nlctrl 请求 family [FAMILY_NAME] 的 id 与多播组）。
     */
    fun getFamilyRequest(sequence: Int): ByteArray {
        val name = FAMILY_NAME.toByteArray(StandardCharsets.UTF_8)
        val nameAttrLength = NLA_HDRLEN + name.size + 1 // 属性里的字符串带结尾 NUL
        val buffer = ByteBuffer.allocate(NLMSG_HDRLEN + GENL_HDRLEN + align4(nameAttrLength)).order(ByteOrder.nativeOrder())

        buffer.putInt(buffer.capacity())                // nlmsg_len
        buffer.putShort(GENL_ID_CTRL.toShort())         // nlmsg_type
        buffer.putShort(NLM_F_REQUEST.toShort())        // nlmsg_flags
        buffer.putInt(sequence)                         // nlmsg_seq
        buffer.putInt(0)                                // nlmsg_pid，0 表示由内核分配
        buffer.put(CTRL_CMD_GETFAMILY.toByte())         // genlmsghdr.cmd
        buffer.put(GENL_VERSION.toByte())               // genlmsghdr.version
        buffer.putShort(0)                              // genlmsghdr.reserved
        buffer.putShort(nameAttrLength.toShort())       // nlattr.nla_len
        buffer.putShort(CTRL_ATTR_FAMILY_NAME.toShort())// nlattr.nla_type
        buffer.put(name)
        buffer.put(0)
        while (buffer.position() and 3 != 0)
            buffer.put(0)

        return buffer.array()
    }

    /**
     * 构造 `REKERNEL_C_GET_VERSION` 查询报文（内核以单播 [REKERNEL_A_MSG] 回复版本字符串）。
     */
    fun getVersionRequest(familyId: Int, sequence: Int): ByteArray {
        val buffer = ByteBuffer.allocate(NLMSG_HDRLEN + GENL_HDRLEN).order(ByteOrder.nativeOrder())

        buffer.putInt(buffer.capacity())
        buffer.putShort(familyId.toShort())
        buffer.putShort(NLM_F_REQUEST.toShort())
        buffer.putInt(sequence)
        buffer.putInt(0)
        buffer.put(REKERNEL_C_GET_VERSION.toByte())
        buffer.put(GENL_VERSION.toByte())
        buffer.putShort(0)

        return buffer.array()
    }

    /**
     * 解析 `CTRL_CMD_GETFAMILY` 回复，取出 family id 与 "events" 多播组 id。
     *
     * @return 解析成功返回 [FamilyInfo]，报文不合法或没有 family id 时返回 null
     */
    fun parseFamilyReply(byteBuffer: ByteBuffer): FamilyInfo? {
        val buffer = byteBuffer.duplicate().order(ByteOrder.nativeOrder())
        if (buffer.limit() < NLMSG_HDRLEN + GENL_HDRLEN)
            return null

        var familyId = -1
        var multicastGroupId = 0
        var offset = NLMSG_HDRLEN + GENL_HDRLEN

        while (offset + NLA_HDRLEN <= buffer.limit()) {
            val attrLength = buffer.getShort(offset).toInt() and 0xFFFF
            val attrType = buffer.getShort(offset + 2).toInt() and NLA_TYPE_MASK
            if (attrLength < NLA_HDRLEN || offset + attrLength > buffer.limit())
                break

            val dataOffset = offset + NLA_HDRLEN
            val dataLength = attrLength - NLA_HDRLEN

            when (attrType) {
                CTRL_ATTR_FAMILY_ID -> if (dataLength >= 2) familyId = buffer.getShort(dataOffset).toInt() and 0xFFFF
                CTRL_ATTR_MCAST_GROUPS -> multicastGroupId = parseMulticastGroups(buffer, dataOffset, dataLength)
            }

            offset += align4(attrLength)
        }

        if (familyId <= 0)
            return null

        return FamilyInfo(familyId, multicastGroupId)
    }

    /** 在 `CTRL_ATTR_MCAST_GROUPS` 嵌套属性里找 [MCGRP_NAME] 对应的组 id。 */
    private fun parseMulticastGroups(buffer: ByteBuffer, start: Int, length: Int): Int {
        var name: String? = null
        var offset = start

        while (offset + NLA_HDRLEN <= start + length && offset + NLA_HDRLEN <= buffer.limit()) {
            val attrLength = buffer.getShort(offset).toInt() and 0xFFFF
            if (attrLength < NLA_HDRLEN || offset + attrLength > start + length)
                break

            // 每个成员组自身也是嵌套属性，扁平地扫描其内部属性即可
            var inner = offset + NLA_HDRLEN
            while (inner + NLA_HDRLEN <= offset + attrLength) {
                val innerLength = buffer.getShort(inner).toInt() and 0xFFFF
                val innerType = buffer.getShort(inner + 2).toInt() and NLA_TYPE_MASK
                if (innerLength < NLA_HDRLEN || inner + innerLength > offset + attrLength)
                    break

                val innerDataOffset = inner + NLA_HDRLEN
                val innerDataLength = innerLength - NLA_HDRLEN

                when (innerType) {
                    CTRL_ATTR_MCAST_GRP_NAME -> name = decodeString(buffer, innerDataOffset, innerDataLength)
                    CTRL_ATTR_MCAST_GRP_ID -> if (innerDataLength >= 4 && name == MCGRP_NAME)
                        return buffer.getInt(innerDataOffset)
                }

                inner += align4(innerLength)
            }

            offset += align4(attrLength)
        }

        return 0
    }

    /**
     * 解析一个内核回复：`genlmsghdr.cmd` 与可选的 `REKERNEL_A_MSG` 字符串。
     *
     * @return 报文长度不足时返回 null
     */
    fun parseReply(byteBuffer: ByteBuffer): Reply? {
        val buffer = byteBuffer.duplicate().order(ByteOrder.nativeOrder())
        if (buffer.limit() < NLMSG_HDRLEN + GENL_HDRLEN)
            return null

        val command = buffer.get(NLMSG_HDRLEN).toInt() and 0xFF
        var message: String? = null
        var offset = NLMSG_HDRLEN + GENL_HDRLEN

        while (offset + NLA_HDRLEN <= buffer.limit()) {
            val attrLength = buffer.getShort(offset).toInt() and 0xFFFF
            val attrType = buffer.getShort(offset + 2).toInt() and NLA_TYPE_MASK
            if (attrLength < NLA_HDRLEN || offset + attrLength > buffer.limit())
                break

            if (attrType == REKERNEL_A_MSG) {
                message = decodeString(buffer, offset + NLA_HDRLEN, attrLength - NLA_HDRLEN)
                break
            }

            offset += align4(attrLength)
        }

        return Reply(command, message)
    }

    /** 读取属性里的 UTF-8 字符串，去掉结尾 NUL。 */
    private fun decodeString(buffer: ByteBuffer, offset: Int, length: Int): String {
        var end = offset + length
        while (end > offset && buffer.get(end - 1) == 0.toByte())
            end--

        val bytes = ByteArray(end - offset)
        for (index in bytes.indices)
            bytes[index] = buffer.get(offset + index)

        return String(bytes, StandardCharsets.UTF_8)
    }
}
