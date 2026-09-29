package nep.timeline.cirno.hooks.android.anr

import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.utils.AnrHelper

class ANRHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.AnrHelper\$AnrRecord"
    }

    override fun getTargetMethod(): String {
        return "appNotResponding"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(Boolean::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val app = CakeReflection.getObjectField(callback.getThisObject(), "mApp")
                if (app == null)
                    return
                if (AnrHelper.blockANR(app))
                    callback.returnAndSkip(null)
            }
        }
    }
}
