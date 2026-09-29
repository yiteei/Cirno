package nep.timeline.cirno.framework

import android.os.Build
import io.github.libxposed.api.XposedInterface
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import java.util.ArrayList

abstract class MethodHook(@JvmField val classLoader: ClassLoader?) {
    @JvmField
    val ANY_VERSION: Int = -1

    @JvmField
    var unhooker: XposedInterface.HookHandle? = null

    init {
        try {
            startHook()
        } catch (throwable: Throwable) {
            if (!isIgnoreError())
                Log.e(getTargetMethod() ?: "null", throwable)
        }
    }

    abstract fun getTargetClass(): String

    abstract fun getTargetMethod(): String?

    abstract fun getTargetParam(): Array<out Any?>

    abstract fun getTargetHook(): CakeHooker.Callback?

    open fun getMinVersion(): Int {
        return ANY_VERSION
    }

    open fun isIgnoreError(): Boolean {
        return false
    }

    open fun startHook() {
        val minVersion = getMinVersion()
        if (minVersion == ANY_VERSION || Build.VERSION.SDK_INT >= minVersion) {
            val targetParam = getTargetParam()
            val targetHook = getTargetHook() ?: return

            val targetMethod = getTargetMethod()
            val targetClass = getTargetClass()

            val param = ArrayList<Any?>(targetParam.toList())
            param.add(targetHook)
            if (targetMethod == null)
                unhooker = CakeReflection.findAndHookConstructor(targetClass, classLoader, *param.toArray())
            else
                unhooker = CakeReflection.findAndHookMethod(targetClass, classLoader, targetMethod, *param.toArray())
            Log.i(getTargetMethod() + " -> 成功Hook完毕!")
        }
    }

    open fun unhook() {
        val unhooker = unhooker ?: return

        unhooker.unhook()
        this.unhooker = null
    }
}
