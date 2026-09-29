package nep.timeline.cirno.hooks.android.audio

import nep.timeline.cirno.log.Log
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.FreezerService
import java.lang.reflect.Method

class SendMediaButtonHook(classLoader: ClassLoader?) {
    init {
        hook(classLoader)
    }

    private fun hook(classLoader: ClassLoader?) {
        val targetClass = CakeReflection.findClassIfExists("com.android.server.media.MediaSessionRecord\$SessionCb", classLoader)
        if (targetClass == null)
            return

        var fieldName: String? = null

        for (field in targetClass.declaredFields) {
            if (field.type.name == "com.android.server.media.MediaSessionRecord") {
                fieldName = field.name
                break
            }
        }

        val finalFieldName: String = fieldName ?: run {
            Log.e("无法监听媒体按键!")
            return
        }

        val methods: MutableList<Method> = ArrayList()
        for (method in targetClass.declaredMethods) {
            val methodName = method.name
            if (methodName == "sendMediaButton" || methodName == "play" || methodName == "playFromMediaId" || methodName == "playFromSearch" || methodName == "playFromUri" || methodName == "next" || methodName == "previous" || methodName == "seekTo")
                methods.add(method)
        }

        for (method in methods) {
            try {
                CakeHooker.hookBefore(method) { callback ->
                    val record = CakeReflection.getObjectField(callback.getThisObject(), finalFieldName)
                    if (record == null)
                        return@hookBefore

                    FreezerService.temporaryUnfreezeIfNeed(CakeReflection.getIntField(record, "mOwnerUid"), "按下媒体按键", 3000)
                }
                Log.i(method.name + " -> 成功Hook完毕!")
            } catch (throwable: Throwable) {
                Log.e(method.name, throwable)
            }
        }
    }
}
