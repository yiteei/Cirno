package nep.timeline.cirno.hooks.android.anr

import android.content.pm.ApplicationInfo
import android.os.Build
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.utils.AnrHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class ANRErrorStateHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.ProcessErrorStateRecord"
    }

    override fun getTargetMethod(): String {
        return "appNotResponding"
    }

    override fun getTargetParam(): Array<out Any?> {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU)
            return arrayOf(
                String::class.java,
                ApplicationInfo::class.java,
                String::class.java,
                "com.android.server.wm.WindowProcessController",
                Boolean::class.javaPrimitiveType!!,
                "com.android.internal.os.TimeoutRecord",
                ExecutorService::class.java,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Future::class.java
            )
        return arrayOf(
            String::class.java,
            ApplicationInfo::class.java,
            String::class.java,
            "com.android.server.wm.WindowProcessController",
            Boolean::class.javaPrimitiveType!!,
            String::class.java,
            Boolean::class.javaPrimitiveType!!
        )
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

    override fun getMinVersion(): Int {
        return Build.VERSION_CODES.S
    }
}
