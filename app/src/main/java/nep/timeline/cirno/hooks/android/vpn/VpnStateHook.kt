package nep.timeline.cirno.hooks.android.vpn

import android.net.NetworkInfo
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.AppService
import nep.timeline.cirno.services.FreezerService
import nep.timeline.cirno.threads.FreezerHandler
import nep.timeline.cirno.utils.PKGUtils

class VpnStateHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.connectivity.Vpn"
    }

    override fun getTargetMethod(): String {
        return "updateState"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(NetworkInfo.DetailedState::class.java, String::class.java)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val state = callback.getArgs()[0].toString()
                val uid = CakeReflection.getIntField(callback.getThisObject(), "mOwnerUID")
                val packageName = CakeReflection.getObjectField(callback.getThisObject(), "mPackage") as String?

                val appRecord = AppService.get(packageName, PKGUtils.getUserId(uid))
                if (appRecord != null) {
                    if (appRecord.isSystem())
                        return

                    if ("CONNECTED" == state && appRecord.appState.setVpn(true)) {
                        Log.d(appRecord.getPackageNameWithUser() + " 连接至VPN")
                        FreezerService.thaw(appRecord)
                    }

                    if (("DISCONNECTED" == state || "FAILED" == state) && appRecord.appState.setVpn(false)) {
                        Log.d(appRecord.getPackageNameWithUser() + " 从VPN断开连接")
                        FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord, 3000)
                    }
                }
            }
        }
    }
}
