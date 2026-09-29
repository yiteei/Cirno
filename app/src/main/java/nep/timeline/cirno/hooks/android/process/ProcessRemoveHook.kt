package nep.timeline.cirno.hooks.android.process

import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.ProcessService

class ProcessRemoveHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.ProcessList"
    }

    override fun getTargetMethod(): String {
        return "removeProcessNameLocked"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(String::class.java, Int::class.javaPrimitiveType!!, "com.android.server.am.ProcessRecord")
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val name = callback.getArgs()[0] as String?
                val uid = callback.getArgs()[1] as Int
                ProcessService.removeProcessRecord(name, uid)
            }
        }
    }
}
