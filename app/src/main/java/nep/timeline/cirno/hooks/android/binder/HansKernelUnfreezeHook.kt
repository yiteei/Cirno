package nep.timeline.cirno.hooks.android.binder

import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.BinderService
import nep.timeline.cirno.services.FreezerService
import nep.timeline.cirno.utils.SystemChecker

class HansKernelUnfreezeHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.OplusHansManager"
    }

    override fun getTargetMethod(): String {
        return "unfreezeForKernel"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, String::class.java, Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                if (BinderService.received) {
                    unhook()
                    return
                }

                val type = callback.getArgs()[0] as Int
                if (type != 1) // Sync binder
                    return
                val target = callback.getArgs()[4] as Int

                FreezerService.temporaryUnfreezeIfNeed(target, "Binder", 3000)
            }
        }
    }

    override fun isIgnoreError(): Boolean {
        return !SystemChecker.isOplus(classLoader!!)
    }
}
