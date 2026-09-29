package nep.timeline.cirno.hooks.android.activity

import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.ActivityManagerService

class ActivityManagerServiceHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.ActivityManagerService"
    }

    override fun getTargetMethod(): String {
        return "setSystemProcess"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf()
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                ActivityManagerService.instance = callback.getThisObject()
            }
        }
    }
}
