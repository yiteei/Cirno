package nep.timeline.cirno.hooks.android.broadcast

import android.os.Build
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.ProcessService
import nep.timeline.cirno.utils.SystemChecker
import nep.timeline.cirno.virtuals.BroadcastRecord

class BroadcastDeliveryHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) "com.android.server.am.BroadcastQueueImpl" else "com.android.server.am.BroadcastQueue"
    }

    override fun getTargetMethod(): String {
        return "deliverToRegisteredReceiverLocked"
    }

    override fun getTargetParam(): Array<out Any?> {
        if (SystemChecker.isHuawei(classLoader!!))
            return arrayOf("com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, "com.android.server.am.BroadcastRecordEx")

        return arrayOf("com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val record = callback.getArgs()[0]
                if (record == null)
                    return

                val broadcastRecord = BroadcastRecord(record)

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

                if (processRecord.isFrozen) {
                    broadcastRecord.skippedDelivery(callback.getArgs()[3] as Int)
                    callback.returnAndSkip(null)
                }
            }
        }
    }
}
