package nep.timeline.cirno.netlink

import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.reflect.CakeReflection
import java.net.SocketAddress
import java.util.Objects

class NetlinkSocketAddress @JvmOverloads constructor(
    /**
     * port ID
     *
     * @hide
     */
    private val nlPortId: Int = 0,
    /**
     * multicast groups mask
     *
     * @hide
     */
    private val nlGroupsMask: Int = 0
) : SocketAddress() {
    /**
     * Returns this address's port id.
     *
     * @return port id
     *
     * @hide
     */
    fun getPortId(): Int {
        return nlPortId
    }

    /**
     * Returns this address's groups multicast mask.
     *
     * @return groups mask
     *
     * @hide
     */
    fun getGroupsMask(): Int {
        return nlGroupsMask
    }

    /**
     * @hide
     */
    override fun toString(): String {
        return Objects.toString(this)
    }

    fun toInstance(): Any? {
        return CakeReflection.newInstance(CakeReflection.findClass("android.system.NetlinkSocketAddress", GlobalVars.classLoader), nlPortId, nlGroupsMask)
    }
}
