package nep.timeline.cirno.netlink

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import nep.timeline.cirno.log.Log
import java.io.Closeable
import java.io.FileDescriptor
import java.io.InterruptedIOException
import java.net.SocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NetlinkClient @Throws(ErrnoException::class) constructor(private val classLoader: ClassLoader?, nlProto: Int) : Closeable {
    companion object {
        private const val SOCKET_RECV_BUFSIZE = 64 * 1024
        private const val DEFAULT_RECV_BUFSIZE = 8 * 1024
    }

    val mDescriptor: FileDescriptor = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM, nlProto)

    @JvmField
    var mLastRecvTimeoutMs: Long = 0

    @JvmField
    var mLastSendTimeoutMs: Long = 0

    init {
        IoUtils.setsockoptInt(classLoader, mDescriptor, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, SOCKET_RECV_BUFSIZE)
    }

    @Throws(ErrnoException::class, SocketException::class)
    fun bind(localAddr: SocketAddress) {
        Os.bind(mDescriptor, localAddr)
    }

    /**
     * Wait indefinitely (or until underlying socket error) for a
     * netlink message of at most DEFAULT_RECV_BUFSIZE size.
     */
    @Throws(ErrnoException::class, InterruptedIOException::class)
    fun recvMessage(): ByteBuffer {
        return recvMessage(DEFAULT_RECV_BUFSIZE, 0)
    }

    /**
     * Wait up to |timeoutMs| (or until underlying socket error) for a
     * netlink message of at most DEFAULT_RECV_BUFSIZE size.
     */
    @Throws(ErrnoException::class, InterruptedIOException::class)
    fun recvMessage(timeoutMs: Long): ByteBuffer {
        return recvMessage(DEFAULT_RECV_BUFSIZE, timeoutMs)
    }

    private fun checkTimeout(timeoutMs: Long) {
        if (timeoutMs < 0) {
            throw IllegalArgumentException("Negative timeouts not permitted")
        }
    }

    /**
     * Wait up to |timeoutMs| (or until underlying socket error) for a
     * netlink message of at most |bufsize| size.
     *
     * Multi-threaded calls with different timeouts will cause unexpected results.
     */
    @Throws(ErrnoException::class, IllegalArgumentException::class, InterruptedIOException::class)
    fun recvMessage(bufsize: Int, timeoutMs: Long): ByteBuffer {
        checkTimeout(timeoutMs)
        synchronized(mDescriptor) {
            if (mLastRecvTimeoutMs != timeoutMs) {
                Os.setsockoptTimeval(
                    mDescriptor,
                    OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO,
                    StructTimeval.fromMillis(timeoutMs)
                )
                mLastRecvTimeoutMs = timeoutMs
            }
        }
        val byteBuffer = ByteBuffer.allocate(bufsize)
        val length = Os.read(mDescriptor, byteBuffer)
        if (length == bufsize) {
            Log.w("maximum read")
        }
        byteBuffer.position(0)
        byteBuffer.limit(length)
        byteBuffer.order(ByteOrder.nativeOrder())
        return byteBuffer
    }

    /**
     * Send a message to a peer to which this socket has previously connected.
     *
     * This blocks until completion or an error occurs.
     */
    @Throws(ErrnoException::class, InterruptedIOException::class)
    fun sendMessage(bytes: ByteArray, offset: Int, count: Int): Boolean {
        return sendMessage(bytes, offset, count, 0)
    }

    /**
     * Send a message to a peer to which this socket has previously connected,
     * waiting at most |timeoutMs| milliseconds for the send to complete.
     *
     * Multi-threaded calls with different timeouts will cause unexpected results.
     */
    @Throws(ErrnoException::class, IllegalArgumentException::class, InterruptedIOException::class)
    fun sendMessage(bytes: ByteArray, offset: Int, count: Int, timeoutMs: Long): Boolean {
        checkTimeout(timeoutMs)
        synchronized(mDescriptor) {
            if (mLastSendTimeoutMs != timeoutMs) {
                Os.setsockoptTimeval(
                    mDescriptor,
                    OsConstants.SOL_SOCKET, OsConstants.SO_SNDTIMEO,
                    StructTimeval.fromMillis(timeoutMs)
                )
                mLastSendTimeoutMs = timeoutMs
            }
        }
        return count == Os.write(mDescriptor, bytes, offset, count)
    }

    override fun close() {
        IoUtils.closeQuietly(classLoader, mDescriptor)
    }
}
