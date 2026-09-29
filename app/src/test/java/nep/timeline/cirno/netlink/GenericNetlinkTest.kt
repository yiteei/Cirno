package nep.timeline.cirno.netlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * GenericNetlink 报文解析单元测试：手写内核侧会发出的 netlink 字节流，验证偏移与对齐处理。
 */
class GenericNetlinkTest {
    private fun bufferOf(vararg bytes: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(bytes.size).order(ByteOrder.nativeOrder())
        for (byte in bytes)
            buffer.put(byte.toByte())
        buffer.position(0)
        return buffer
    }

    /** 写入一个 nlattr（含 4 字节头，并按 NLA_ALIGN 补齐） */
    private fun putAttr(buffer: ByteBuffer, type: Int, data: ByteArray) {
        buffer.putShort((GenericNetlink.NLA_HDRLEN + data.size).toShort())
        buffer.putShort(type.toShort())
        buffer.put(data)
        while (buffer.position() and 3 != 0)
            buffer.put(0)
    }

    private fun putAttrU16(buffer: ByteBuffer, type: Int, value: Int) {
        val data = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder()).putShort(value.toShort()).array()
        putAttr(buffer, type, data)
    }

    private fun putAttrU32(buffer: ByteBuffer, type: Int, value: Int) {
        val data = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(value).array()
        putAttr(buffer, type, data)
    }

    /** 构造 CTRL_CMD_GETFAMILY 回复：family id 42，多播组 events=5 */
    private fun familyReply(familyId: Int, groupName: String?, groupId: Int): ByteBuffer {
        val groups = ByteBuffer.allocate(64).order(ByteOrder.nativeOrder())
        if (groupName != null) {
            val inner = ByteBuffer.allocate(64).order(ByteOrder.nativeOrder())
            putAttrU16(inner, GenericNetlink.CTRL_ATTR_MCAST_GRP_NAME, 0) // 占位，下面重建
            inner.clear()
            putAttr(inner, GenericNetlink.CTRL_ATTR_MCAST_GRP_NAME, groupName.toByteArray(StandardCharsets.UTF_8))
            putAttrU32(inner, GenericNetlink.CTRL_ATTR_MCAST_GRP_ID, groupId)
            inner.flip()
            val nested = ByteArray(inner.remaining())
            inner.get(nested)
            // 组自身是带 NLA_F_NESTED 的嵌套属性
            putAttr(groups, 1 or 0x8000, nested)
        }
        groups.flip()
        val nestedGroups = ByteArray(groups.remaining())
        groups.get(nestedGroups)

        val buffer = ByteBuffer.allocate(256).order(ByteOrder.nativeOrder())
        buffer.putInt(0)                                 // nlmsg_len，最后回填
        buffer.putShort(GenericNetlink.GENL_ID_CTRL.toShort())
        buffer.putShort(0)
        buffer.putInt(1)                                 // nlmsg_seq
        buffer.putInt(0)                                 // nlmsg_pid
        buffer.put(GenericNetlink.CTRL_CMD_GETFAMILY.toByte())
        buffer.put(GenericNetlink.GENL_VERSION.toByte())
        buffer.putShort(0)
        putAttrU16(buffer, GenericNetlink.CTRL_ATTR_FAMILY_ID, familyId)
        putAttr(buffer, GenericNetlink.CTRL_ATTR_MCAST_GROUPS, nestedGroups)

        val length = buffer.position()
        buffer.putInt(0, length)
        buffer.position(0)
        buffer.limit(length)
        return buffer
    }

    /** 构造 REKERNEL_C_EVENT 回复，REKERNEL_A_MSG 内容为 payload */
    private fun eventReply(command: Int, payload: String?): ByteBuffer {
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.nativeOrder())
        buffer.putInt(0)
        buffer.putShort(2)                               // family id
        buffer.putShort(0)
        buffer.putInt(7)
        buffer.putInt(0)
        buffer.put(command.toByte())
        buffer.put(GenericNetlink.GENL_VERSION.toByte())
        buffer.putShort(0)
        if (payload != null)
            putAttr(buffer, GenericNetlink.REKERNEL_A_MSG, (payload + "\u0000").toByteArray(StandardCharsets.UTF_8))

        val length = buffer.position()
        buffer.putInt(0, length)
        buffer.position(0)
        buffer.limit(length)
        return buffer
    }

    @Test
    fun parseFamilyReply_readsFamilyIdAndEventsGroup() {
        val family = GenericNetlink.parseFamilyReply(familyReply(42, GenericNetlink.MCGRP_NAME, 5))

        assertEquals(42, family!!.id)
        assertEquals(5, family.multicastGroupId)
    }

    @Test
    fun parseFamilyReply_ignoresOtherMulticastGroups() {
        val family = GenericNetlink.parseFamilyReply(familyReply(42, "other", 9))

        assertEquals(42, family!!.id)
        assertEquals(0, family.multicastGroupId)
    }

    @Test
    fun parseFamilyReply_returnsNullWithoutFamilyId() {
        val buffer = bufferOf(
            20, 0, 0, 0, 0x10, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0,
            3, 1, 0, 0
        )

        assertNull(GenericNetlink.parseFamilyReply(buffer))
    }

    @Test
    fun parseReply_readsEventMessage() {
        val reply = GenericNetlink.parseReply(
            eventReply(GenericNetlink.REKERNEL_C_EVENT, "type=Binder,bindertype=transaction,oneway=0,target=10123;")
        )

        assertEquals(GenericNetlink.REKERNEL_C_EVENT, reply!!.command)
        assertEquals("type=Binder,bindertype=transaction,oneway=0,target=10123;", reply.message)
    }

    @Test
    fun parseReply_decodesNulOnlyPayloadAsEmptyString() {
        val reply = GenericNetlink.parseReply(eventReply(GenericNetlink.REKERNEL_C_EVENT, ""))

        assertEquals(GenericNetlink.REKERNEL_C_EVENT, reply!!.command)
        assertEquals("", reply.message)
    }

    @Test
    fun parseReply_returnsNullMessageWithoutAttribute() {
        val reply = GenericNetlink.parseReply(eventReply(GenericNetlink.REKERNEL_C_GET_VERSION, null))

        assertEquals(GenericNetlink.REKERNEL_C_GET_VERSION, reply!!.command)
        assertNull(reply.message)
    }

    @Test
    fun parseReply_handlesTruncatedBuffer() {
        assertNull(GenericNetlink.parseReply(bufferOf(4, 0, 0, 0)))
    }

    @Test
    fun getFamilyRequest_hasExpectedHeader() {
        val request = GenericNetlink.getFamilyRequest(3)
        val buffer = ByteBuffer.wrap(request).order(ByteOrder.nativeOrder())

        assertEquals(request.size, buffer.getInt(0))
        assertEquals(GenericNetlink.GENL_ID_CTRL, buffer.getShort(4).toInt())
        assertEquals(GenericNetlink.NLM_F_REQUEST, buffer.getShort(6).toInt())
        assertEquals(3, buffer.getInt(8))
        assertEquals(GenericNetlink.CTRL_CMD_GETFAMILY.toByte(), buffer.get(16))
        assertEquals((GenericNetlink.NLA_HDRLEN + GenericNetlink.FAMILY_NAME.length + 1).toShort(), buffer.getShort(20))
        assertEquals(GenericNetlink.CTRL_ATTR_FAMILY_NAME.toShort(), buffer.getShort(22))
        assertEquals(
            GenericNetlink.FAMILY_NAME,
            String(request, 24, GenericNetlink.FAMILY_NAME.length, StandardCharsets.UTF_8)
        )
    }

    @Test
    fun getVersionRequest_targetsFamily() {
        val request = GenericNetlink.getVersionRequest(42, 9)
        val buffer = ByteBuffer.wrap(request).order(ByteOrder.nativeOrder())

        assertEquals(request.size, buffer.getInt(0))
        assertEquals(42, buffer.getShort(4).toInt())
        assertEquals(9, buffer.getInt(8))
        assertEquals(GenericNetlink.REKERNEL_C_GET_VERSION.toByte(), buffer.get(16))
    }
}
