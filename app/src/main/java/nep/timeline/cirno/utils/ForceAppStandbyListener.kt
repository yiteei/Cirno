package nep.timeline.cirno.utils

import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.reflect.CakeReflection

object ForceAppStandbyListener {
    @JvmStatic
    var instance: Any? = null

    @JvmStatic
    fun removeAlarmsForUid(appRecord: AppRecord) {
        val instance = instance ?: return

        CakeReflection.callMethod(instance, "removeAlarmsForUid", appRecord.uid)
        Log.d(appRecord.getPackageNameWithUser() + " 移除Alarms")
    }
}
