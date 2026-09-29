package nep.timeline.cirno.virtuals

import android.os.IBinder
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.AppService

class ActivityRecord(val instance: Any?) {
    val packageName: String? = CakeReflection.getObjectField(instance, "packageName") as String?
    val userId: Int = CakeReflection.getIntField(instance, "mUserId")
    val token: IBinder? = CakeReflection.getObjectField(instance, "token") as IBinder?

    fun toAppRecord(): AppRecord? {
        return AppService.get(packageName, userId)
    }
}
