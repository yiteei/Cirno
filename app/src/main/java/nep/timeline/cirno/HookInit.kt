package nep.timeline.cirno

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import nep.timeline.cirno.framework.XposedInstance
import nep.timeline.cirno.log.XposedLog
import nep.timeline.cirno.master.AndroidHooks
import nep.timeline.cirno.reflect.CakeHooker
import java.io.File

class HookInit : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        XposedInstance.setModule(this)
        CakeHooker.xposedModule = this
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader = param.classLoader
        CakeHooker.hostClassLoader = classLoader

        try {
            val source = File(GlobalVars.LOG_DIR, "current.log")
            val dest = File(GlobalVars.LOG_DIR, "last.log")
            val delete = dest.delete()
            val renameTo = source.renameTo(dest)
            AndroidHooks.start(classLoader)
        } catch (throwable: Throwable) {
            XposedLog.e("Hook failed:", throwable)
        }
    }
}
