package nep.timeline.cirno.hooks.android.network

import android.os.Build
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.NetworkManagementService

class NetworkManagerHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) "com.android.server.net.NetworkManagementService" else "com.android.server.NetworkManagementService"
    }

    override fun getTargetMethod(): String {
        return "systemReady"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf()
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.AfterHookCallback) {
                NetworkManagementService.setInstance(callback.getThisObject(), classLoader)
            }
        }
    }
}
