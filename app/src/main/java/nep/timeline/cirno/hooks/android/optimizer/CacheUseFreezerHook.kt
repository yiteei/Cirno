package nep.timeline.cirno.hooks.android.optimizer

import android.os.Build
import io.github.libxposed.api.XposedInterface
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.CachedAppOptimizer

class CacheUseFreezerHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.am.CachedAppOptimizer"
    }

    override fun getTargetMethod(): String {
        return "useFreezer"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf()
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.ReplacementCallback {
            override fun call(chain: XposedInterface.Chain): Any? {
                if (CachedAppOptimizer.instance == null)
                    synchronized(CachedAppOptimizer::class.java) {
                        CachedAppOptimizer.instance = chain.thisObject
                    }

                return false
            }
        }
    }

    override fun getMinVersion(): Int {
        return Build.VERSION_CODES.R
    }
}
