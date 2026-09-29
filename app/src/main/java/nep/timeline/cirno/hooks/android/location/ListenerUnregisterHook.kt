package nep.timeline.cirno.hooks.android.location

import android.os.Binder
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.handlers.LocationHandler
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.ProcessService
import nep.timeline.cirno.threads.Handlers
import nep.timeline.cirno.virtuals.ILocationListener

class ListenerUnregisterHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.location.LocationManagerService"
    }

    override fun getTargetMethod(): String {
        return "unregisterLocationListener"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf("android.location.ILocationListener")
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.AfterHookCallback) {
                val listener = ILocationListener(callback.getArgs()[0])
                val pid = Binder.getCallingPid()

                Handlers.location.post {
                    val processRecord = ProcessService.getProcessRecordByPid(pid)

                    if (processRecord == null)
                        return@post

                    val appRecord = processRecord.getAppRecord()

                    if (appRecord == null)
                        return@post

                    val set = appRecord.appState.locationListeners
                    if (set.remove(listener.asBinder()))
                        LocationHandler.call(appRecord, set)
                }
            }
        }
    }
}
