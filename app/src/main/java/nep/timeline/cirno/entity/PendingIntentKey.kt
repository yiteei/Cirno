package nep.timeline.cirno.entity

import nep.timeline.cirno.reflect.CakeReflection

open class PendingIntentKey(key: Any?) {
    val instance: Any? = key
    val packageName: String? = CakeReflection.getObjectField(key, "packageName") as String?
    val userId: Int = CakeReflection.getIntField(key, "userId")
}
