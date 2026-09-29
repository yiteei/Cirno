package nep.timeline.cirno.hooks.android.audio

import android.media.AudioPlaybackConfiguration
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.handlers.AudioHandler
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.AppService
import nep.timeline.cirno.threads.Handlers
import nep.timeline.cirno.virtuals.AudioPlaybackConfigurationReflect

class PlayerBanHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    override fun getTargetClass(): String {
        return "com.android.server.audio.PlaybackActivityMonitor"
    }

    override fun getTargetMethod(): String {
        return "checkBanPlayer"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf(AudioPlaybackConfiguration::class.java, Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.AfterHookCallback) {
                if (callback.result as Boolean) {
                    val configuration = callback.getArgs()[0]
                    if (configuration == null)
                        return

                    val reflect = AudioPlaybackConfigurationReflect(configuration as AudioPlaybackConfiguration)

                    Handlers.audio.post {
                        val appRecords = AppService.getByUid(reflect.getClientUid())
                        if (appRecords == null || appRecords.isEmpty())
                            return@post

                        for (appRecord in appRecords) {
                            if (appRecord == null)
                                continue

                            val interfaceId = reflect.getPlayerInterfaceId()
                            AudioHandler.call(appRecord, AudioHandler.PLAYER_STATE_PAUSED, interfaceId)
                        }
                    }
                }
            }
        }
    }
}
