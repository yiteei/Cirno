package nep.timeline.cirno.hooks.android.wakelock

import android.os.Build
import android.os.IBinder
import android.os.WorkSource
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.AppService
import nep.timeline.cirno.utils.PKGUtils

class WakeLockHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.power.PowerManagerService"
    }

    override fun getTargetMethod(): String {
        return "acquireWakeLockInternal"
    }

    override fun getTargetParam(): Array<out Any?> {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
            return arrayOf(IBinder::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, String::class.java, String::class.java, WorkSource::class.java, String::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, "android.os.IWakeLockCallback")
        return arrayOf(IBinder::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, String::class.java, String::class.java, WorkSource::class.java, String::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val packageName = callback.getArgs()[4] as String?
                val uid = callback.getArgs()[7] as Int

                val appRecord = AppService.get(packageName, PKGUtils.getUserId(uid))
                if (appRecord == null)
                    return

                if (appRecord.isFrozen)
                    callback.returnAndSkip(null)
            }
        }
    }
}
