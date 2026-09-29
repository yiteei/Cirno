package nep.timeline.cirno.hooks.android.signal

import android.os.Process
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.ProcessService

class SendSignalQuietHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return Process::class.java.typeName
    }

    override fun getTargetMethod(): String {
        return "sendSignalQuiet"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val pid = callback.getArgs()[0] as Int
                val signal = callback.getArgs()[1] as Int
                if (signal != Process.SIGNAL_KILL)
                    return

                val processRecord = ProcessService.getProcessRecordByPid(pid)
                if (processRecord == null || processRecord.isDeathProcess())
                    return

                ProcessService.removeProcessRecord(processRecord)
            }
        }
    }
}
