package nep.timeline.cirno.framework

import io.github.libxposed.api.XposedInterface
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import java.lang.reflect.Executable

object XposedInstance {
    private var module: io.github.libxposed.api.XposedModule? = null

    @JvmStatic
    fun setModule(module: io.github.libxposed.api.XposedModule) {
        XposedInstance.module = module
    }

    @JvmStatic
    fun deoptimize(executable: Executable): Boolean {
        val module = this.module ?: return false
        return module.deoptimize(executable)
    }

    @JvmStatic
    fun log(priority: Int, tag: String?, msg: String) {
        val module = this.module ?: return
        module.log(priority, tag, msg)
    }

    @JvmStatic
    fun log(priority: Int, tag: String?, msg: String, tr: Throwable?) {
        val module = this.module ?: return
        module.log(priority, tag, msg, tr)
    }

    @JvmStatic
    fun getApiVersion(): Int {
        val module = this.module ?: return -1
        return module.apiVersion
    }

    @JvmStatic
    fun hook(executable: Executable?): XposedInterface.HookBuilder? {
        if (executable == null)
            return null
        return module!!.hook(executable)
    }
}
