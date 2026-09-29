package nep.timeline.cirno.utils

import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.UserHandle

object PKGUtils {
    @JvmStatic
    fun getUserId(uid: Int): Int {
        return UserHandle.getUserHandleForUid(uid).hashCode()
    }

    @JvmStatic
    fun isSystemApp(applicationInfo: ApplicationInfo?): Boolean {
        if (applicationInfo == null)
            return true

        return applicationInfo.uid < Process.FIRST_APPLICATION_UID || (applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    }
}
