package nep.timeline.cirno.utils

import android.os.Process
import java.nio.file.Files
import java.nio.file.Paths

object FrozenRW {
    @JvmField
    val cgroupV2 = "/sys/fs/cgroup"
    private val cgroupV2SysAppIsolated: Boolean

    init {
        val path = "/sys/fs/cgroup/uid_1000/cgroup.freeze"
        cgroupV2SysAppIsolated = !Files.exists(Paths.get(path))
    }

    private fun writeFrozen(uid: Int, pid: Int, frozenState: Int) {
        if (!cgroupV2SysAppIsolated) {
            RWUtils.writeFrozen(cgroupV2 + "/uid_" + uid + "/pid_" + pid + "/cgroup.freeze", frozenState)
            return
        }

        if (uid < Process.FIRST_APPLICATION_UID)
            RWUtils.writeFrozen(cgroupV2 + "/system/uid_" + uid + "/pid_" + pid + "/cgroup.freeze", frozenState)
        else
            RWUtils.writeFrozen(cgroupV2 + "/apps/uid_" + uid + "/pid_" + pid + "/cgroup.freeze", frozenState)
    }

    @JvmStatic
    fun frozen(uid: Int, pid: Int) {
        writeFrozen(uid, pid, 1)
    }

    @JvmStatic
    fun thaw(uid: Int, pid: Int) {
        writeFrozen(uid, pid, 0)
    }
}
