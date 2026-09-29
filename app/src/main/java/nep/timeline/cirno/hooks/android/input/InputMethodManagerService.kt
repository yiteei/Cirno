package nep.timeline.cirno.hooks.android.input

import android.os.Build
import android.view.inputmethod.InputMethodInfo
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.ActivityManagerService
import nep.timeline.cirno.services.AppService
import nep.timeline.cirno.services.FreezerService
import nep.timeline.cirno.threads.FreezerHandler
import nep.timeline.cirno.utils.InputMethodData

class InputMethodManagerService(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.inputmethod.InputMethodManagerService"
    }

    override fun getTargetMethod(): String {
        return "setInputMethodLocked"
    }

    override fun getTargetParam(): Array<out Any?> {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM)
            return CakeReflection.findParameterTypesOrDefault(CakeReflection.findClassIfExists(getTargetClass(), classLoader), getTargetMethod(), String::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
        return CakeReflection.findParameterTypesOrDefault(CakeReflection.findClassIfExists(getTargetClass(), classLoader), getTargetMethod(), String::class.java, Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val id = callback.getArgs()[0] as String?
                if (id == null)
                    return

                val userId = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) callback.getArgs()[3] as Int else ActivityManagerService.getCurrentOrTargetUserId()
                val settings = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) CakeReflection.callStaticMethod(CakeReflection.findClassIfExists("com.android.server.inputmethod.InputMethodSettingsRepository", classLoader)!!, "get", userId) else CakeReflection.getObjectField(callback.getThisObject(), "mSettings")

                synchronized(InputMethodData::class.java) {
                    if (InputMethodData.instance == null) {
                        InputMethodData.instance = callback.getThisObject()
                        if (settings != null) {
                            val map = CakeReflection.getObjectField(settings, "mMethodMap")
                            if (map != null) {
                                if (map.javaClass.typeName == "com.android.server.inputmethod.InputMethodMap")
                                    InputMethodData.inputMethods = CakeReflection.getObjectField(map, "mMap") as MutableMap<String, InputMethodInfo>?
                                else
                                    InputMethodData.inputMethods = map as MutableMap<String, InputMethodInfo>?
                            } else
                                InputMethodData.inputMethods = null
                        } else
                            InputMethodData.inputMethods = null
                    }

                    val inputMethodMap = InputMethodData.inputMethods
                    if (inputMethodMap == null)
                        return

                    val inputMethodInfo = inputMethodMap[id]

                    if (inputMethodInfo != null && inputMethodInfo != InputMethodData.currentInputMethodInfo) {
                        InputMethodData.currentInputMethodInfo = inputMethodInfo
                        val appRecord: AppRecord? = AppService.get(inputMethodInfo.packageName, userId)
                        if (appRecord !== InputMethodData.currentInputMethodApp) {
                            val oldApp = InputMethodData.currentInputMethodApp
                            InputMethodData.currentInputMethodApp = appRecord
                            if (appRecord != null)
                                FreezerService.thaw(appRecord)
                            if (oldApp != null)
                                FreezerHandler.sendFreezeMessage(oldApp, 3000)
                        }
                    }
                }
            }
        }
    }
}
