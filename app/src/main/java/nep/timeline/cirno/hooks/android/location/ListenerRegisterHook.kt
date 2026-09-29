package nep.timeline.cirno.hooks.android.location

import android.location.LocationManager
import android.location.LocationRequest
import android.os.Binder
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.handlers.LocationHandler
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.AppService
import nep.timeline.cirno.threads.Handlers
import nep.timeline.cirno.utils.PKGUtils
import nep.timeline.cirno.virtuals.ILocationListener

class ListenerRegisterHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.location.LocationManagerService"
    }

    override fun getTargetMethod(): String {
        return "registerLocationListener"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(String::class.java, LocationRequest::class.java, "android.location.ILocationListener", String::class.java, String::class.java, String::class.java)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.AfterHookCallback) {
                val isGPS = LocationManager.GPS_PROVIDER == callback.getArgs()[0]
                if (!isGPS)
                    return

                val packageName = callback.getArgs()[3] as String?
                val uid = Binder.getCallingUid()

                val listener = ILocationListener(callback.getArgs()[2])

                Handlers.location.post {
                    val appRecord = AppService.get(packageName, PKGUtils.getUserId(uid))

                    if (appRecord == null)
                        return@post

                    val set = appRecord.appState.locationListeners
                    if (set.add(listener.asBinder()))
                        LocationHandler.call(appRecord, set)
                }
            }
        }
    }
}
