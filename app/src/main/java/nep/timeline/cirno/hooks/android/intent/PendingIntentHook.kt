package nep.timeline.cirno.hooks.android.intent

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.entity.PendingIntentKey
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.AppService
import nep.timeline.cirno.services.FreezerService

class PendingIntentHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.PendingIntentRecord"
    }

    override fun getTargetMethod(): String {
        return "sendInner"
    }

    override fun getTargetParam(): Array<out Any?> {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU)
            return CakeReflection.findParameterTypesOrDefault(CakeReflection.findClassIfExists(getTargetClass(), classLoader), getTargetMethod(), "android.app.IApplicationThread", Int::class.javaPrimitiveType!!, Intent::class.java, String::class.java, IBinder::class.java, "android.content.IIntentReceiver", String::class.java, IBinder::class.java, String::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Bundle::class.java)
        return CakeReflection.findParameterTypesOrDefault(CakeReflection.findClassIfExists(getTargetClass(), classLoader), getTargetMethod(), Int::class.javaPrimitiveType!!, Intent::class.java, String::class.java, IBinder::class.java, "android.content.IIntentReceiver", String::class.java, IBinder::class.java, String::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Bundle::class.java)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                synchronized(CakeReflection.getObjectField(CakeReflection.getObjectField(callback.getThisObject(), "controller"), "mLock")!!) {
                    if (CakeReflection.getBooleanField(callback.getThisObject(), "canceled"))
                        return

                    val key = CakeReflection.getObjectField(callback.getThisObject(), "key")
                    if (key == null)
                        return

                    val pendingIntentKey = PendingIntentKey(key)

                    val appRecord = AppService.get(pendingIntentKey.packageName, pendingIntentKey.userId)

                    if (appRecord == null || !appRecord.isFrozen)
                        return

                    FreezerService.temporaryUnfreezeIfNeed(appRecord, "Intent", 3000)
                }
            }
        }
    }
}
