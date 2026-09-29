package nep.timeline.cirno.hooks.android.process

import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.ProcessService

class ProcessAddHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.ProcessList"
    }

    override fun getTargetMethod(): String {
        return "addProcessNameLocked"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf("com.android.server.am.ProcessRecord")
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.AfterHookCallback) {
                val record = callback.getArgs()[0]
                if (record == null)
                    return
                ProcessService.addProcessRecord(record)
            }
        }
    }
}
