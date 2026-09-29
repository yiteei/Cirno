package nep.timeline.cirno.hooks.android.audio

import android.media.AudioPlaybackConfiguration
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.handlers.AudioHandler
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.AppService
import nep.timeline.cirno.threads.Handlers
import nep.timeline.cirno.virtuals.AudioPlaybackConfigurationReflect

class AudioStateHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return AudioPlaybackConfiguration::class.java.typeName
    }

    override fun getTargetMethod(): String {
        return "handleStateEvent"
    }

    override fun getTargetParam(): Array<out Any?> {
        return CakeReflection.findParameterTypesOrDefault(AudioPlaybackConfiguration::class.java, getTargetMethod(), Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.AfterHookCallback) {
                if (!(callback.result as Boolean))
                    return

                val event = callback.getArgs()[0] as Int
                if (AudioHandler.LISTEN_EVENT.contains(event)) {
                    val reflect = AudioPlaybackConfigurationReflect(callback.getThisObject() as AudioPlaybackConfiguration)

                    Handlers.audio.post {
                        val appRecords = AppService.getByUid(reflect.getClientUid())
                        if (appRecords == null)
                            return@post

                        val interfaceId = reflect.getPlayerInterfaceId()
                        for (appRecord in appRecords) {
                            if (appRecord == null)
                                continue

                            AudioHandler.call(appRecord, event, interfaceId)
                        }
                    }
                }
            }
        }
    }
}
