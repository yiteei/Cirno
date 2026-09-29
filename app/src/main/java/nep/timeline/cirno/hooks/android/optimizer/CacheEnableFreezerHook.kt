package nep.timeline.cirno.hooks.android.optimizer

import android.os.Build
import io.github.libxposed.api.XposedInterface
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.CachedAppOptimizer

class CacheEnableFreezerHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.CachedAppOptimizer"
    }

    override fun getTargetMethod(): String {
        return "enableFreezer"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(Boolean::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.ReplacementCallback {
            override fun call(chain: XposedInterface.Chain): Any? {
                synchronized(CachedAppOptimizer::class.java) {
                    val `object` = chain.thisObject
                    if (CachedAppOptimizer.instance == null)
                        CachedAppOptimizer.instance = `object`
                    if (CakeReflection.getBooleanField(`object`, "mUseFreezer"))
                        CakeReflection.setObjectField(`object`, "mUseFreezer", false)
                }
                return false
            }
        }
    }

    override fun getMinVersion(): Int {
        return Build.VERSION_CODES.S
    }
}
