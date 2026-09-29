package nep.timeline.cirno.services

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import nep.timeline.cirno.reflect.CakeReflection

object ActivityManagerService {
    @JvmStatic
    @Volatile
    var instance: Any? = null

    @JvmStatic
    fun getContext(): Context? {
        return CakeReflection.getObjectField(instance, "mContext") as Context?
    }

    @JvmStatic
    fun getApplicationInfo(packageName: String, userId: Int): ApplicationInfo? {
        try {
            val context = getContext() ?: return null
            val packageManager = context.packageManager ?: return null
            return CakeReflection.callMethod(
                packageManager,
                "getApplicationInfoAsUser",
                packageName,
                PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES,
                userId
            ) as ApplicationInfo?
        } catch (ignored: Throwable) {

        }
        return null
    }

    @JvmStatic
    fun getCurrentOrTargetUserId(): Int {
        return CakeReflection.callMethod(CakeReflection.getObjectField(instance, "mUserController")!!, "getCurrentOrTargetUserId") as Int
    }

    @JvmStatic
    fun getPackagesForUid(uid: Int): Array<String>? {
        val context = getContext() ?: return null

        val packageManager = context.packageManager
        return packageManager.getPackagesForUid(uid)
    }

    @JvmStatic
    fun getPidsSelfLocked(): Any? {
        return CakeReflection.getObjectField(instance, "mPidsSelfLocked")
    }
}
