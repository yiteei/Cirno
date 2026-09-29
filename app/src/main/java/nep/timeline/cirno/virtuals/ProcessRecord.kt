package nep.timeline.cirno.virtuals

import android.content.pm.ApplicationInfo
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.AppService

class ProcessRecord(val instance: Any?) {
    val userId: Int = CakeReflection.getIntField(instance, "userId")
    val runningUid: Int = CakeReflection.getIntField(instance, "uid")
    val applicationInfo: ApplicationInfo = CakeReflection.getObjectField(instance, "info") as ApplicationInfo
    val uid: Int = applicationInfo.uid
    val packageName: String? = applicationInfo.packageName
    val processName: String? = CakeReflection.getObjectField(instance, "processName") as String?
    var isFrozen: Boolean = false
    private var appRecord: AppRecord? = null

    init {
        this.appRecord = AppService.get(packageName, userId)
    }

    fun getPid(): Int {
        return CakeReflection.getObjectField(instance, "mPid") as Int
    }

    fun isDeathProcess(): Boolean {
        return getPid() <= 0
    }

    fun getAppRecord(): AppRecord? {
        if (appRecord == null)
            appRecord = AppService.get(packageName, userId)
        return appRecord
    }
}
