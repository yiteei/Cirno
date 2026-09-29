package nep.timeline.cirno.hooks.android.binder

import android.os.Build
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.BinderService
import nep.timeline.cirno.services.FreezerService
import nep.timeline.cirno.utils.SystemChecker

class MilletBinderTransHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.miui.server.greeze.GreezeManagerService"
    }

    override fun getTargetMethod(): String {
        return "reportBinderTrans"
    }

    override fun getTargetParam(): Array<out Any?> {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
        return arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                if (BinderService.received) {
                    unhook()
                    return
                }

                val isOneway = callback.getArgs()[5] as Boolean
                if (isOneway)
                    return

                val dstUid = callback.getArgs()[0] as Int

                FreezerService.temporaryUnfreezeIfNeed(dstUid, "Binder", 3000)
            }
        }
    }

    override fun isIgnoreError(): Boolean {
        return !SystemChecker.isXiaomi(classLoader!!)
    }
}
