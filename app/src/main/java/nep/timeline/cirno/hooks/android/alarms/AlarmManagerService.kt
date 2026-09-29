package nep.timeline.cirno.hooks.android.alarms

import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.utils.ForceAppStandbyListener

class AlarmManagerService(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.alarm.AlarmManagerService"
    }

    override fun getTargetMethod(): String {
        return "onBootPhase"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                ForceAppStandbyListener.instance = CakeReflection.getObjectField(callback.getThisObject(), "mForceAppStandbyListener")
            }
        }
    }
}
