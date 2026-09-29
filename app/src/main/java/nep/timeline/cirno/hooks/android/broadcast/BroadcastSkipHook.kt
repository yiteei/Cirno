package nep.timeline.cirno.hooks.android.broadcast

import android.os.Build
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.ProcessService
import nep.timeline.cirno.utils.SystemChecker

class BroadcastSkipHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.BroadcastSkipPolicy"
    }

    override fun getTargetMethod(): String {
        return "shouldSkipMessage"
    }

    override fun getTargetParam(): Array<out Any?> {
        if (SystemChecker.isVivo(classLoader!!))
            return arrayOf("com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, "com.android.server.am.IVivoBroadcastQueueModern")
        return CakeReflection.findParameterTypesOrDefault(CakeReflection.findClassIfExists(getTargetClass(), classLoader), getTargetMethod(), "com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter")
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.AfterHookCallback) {
                if (callback.result != null)
                    return

                val filter = callback.getArgs()[1]
                if (filter == null)
                    return

                val receiver = CakeReflection.getObjectField(filter, "receiverList")
                if (receiver == null)
                    return

                val app = CakeReflection.getObjectField(receiver, "app")
                if (app == null)
                    return

                val processRecord = ProcessService.getProcessRecord(app)
                if (processRecord == null)
                    return

                if (processRecord.isFrozen)
                    callback.result = "Skipping deliver [Cirno]: frozen process"
            }
        }
    }

    override fun getMinVersion(): Int {
        return Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}
