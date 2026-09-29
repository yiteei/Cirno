package nep.timeline.cirno.hooks.android.recorder

import android.os.Binder
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.handlers.RecordingHandler
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.AppService

class RecorderEventHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.audio.RecordingActivityMonitor"
    }

    override fun getTargetMethod(): String {
        return "recorderEvent"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val uid = Binder.getCallingUid()
                val riid = callback.getArgs()[0] as Int
                val event = callback.getArgs()[1] as Int

                if (riid == RecordingHandler.RECORD_RIID_INVALID)
                    return

                val appRecords = AppService.getByUid(uid)

                if (appRecords == null || appRecords.isEmpty())
                    return

                for (appRecord in appRecords) {
                    if (appRecord == null)
                        continue

                    RecordingHandler.call(appRecord, event, riid)
                }
            }
        }
    }
}
