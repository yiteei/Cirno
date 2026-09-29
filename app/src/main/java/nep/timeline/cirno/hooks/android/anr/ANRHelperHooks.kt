package nep.timeline.cirno.hooks.android.anr

import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.utils.AnrHelper

class ANRHelperHooks(classLoader: ClassLoader?) {
    init {
        hook(classLoader)
    }

    private fun hook(classLoader: ClassLoader?) {
        try {
            val targetClass = CakeReflection.findClassIfExists("com.android.server.am.AnrHelper", classLoader) ?: return

            for (method in targetClass.declaredMethods) {
                if ((method.name == "appNotResponding" || method.name == "deferAppNotResponding") && method.returnType == Void.TYPE) {
                    val index = findIndex(method.parameterTypes, "com.android.server.am.ProcessRecord")
                    if (index == null) { // Not found
                        val miuiRecordIndex = findIndex(method.parameterTypes, "com.android.server.am.AnrHelper\$AnrRecord")
                        if (miuiRecordIndex != null) {
                            CakeHooker.hookBefore(method) { callback ->
                                val anrRecord = callback.getArgs()[miuiRecordIndex]
                                if (anrRecord == null)
                                    return@hookBefore
                                val app = CakeReflection.getObjectField(anrRecord, "mApp")
                                if (app == null)
                                    return@hookBefore
                                if (AnrHelper.blockANR(app))
                                    callback.returnAndSkip(null)
                            }
                        }
                    } else {
                        CakeHooker.hookBefore(method) { callback ->
                            val record = callback.getArgs()[index]
                            if (record == null)
                                return@hookBefore
                            if (AnrHelper.blockANR(record))
                                callback.returnAndSkip(null)
                        }
                    }
                }
            }
        } catch (ignored: Throwable) {

        }
    }

    fun findIndex(parameterTypes: Array<Class<*>?>, clazz: String): Int? {
        for (i in parameterTypes.indices)
            if (clazz == parameterTypes[i]!!.name)
                return i
        return null
    }
}
