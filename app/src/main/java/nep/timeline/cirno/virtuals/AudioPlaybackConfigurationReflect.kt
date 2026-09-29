package nep.timeline.cirno.virtuals

import android.media.AudioPlaybackConfiguration
import nep.timeline.cirno.reflect.CakeReflection

class AudioPlaybackConfigurationReflect(val instance: AudioPlaybackConfiguration) {
    fun getPlayerInterfaceId(): Int {
        return CakeReflection.callMethod(instance, "getPlayerInterfaceId") as Int
    }

    fun getClientUid(): Int {
        return CakeReflection.callMethod(instance, "getClientUid") as Int
    }

    fun getClientPid(): Int {
        return CakeReflection.callMethod(instance, "getClientPid") as Int
    }

    fun getPlayerType(): Int {
        return CakeReflection.callMethod(instance, "getPlayerType") as Int
    }
}
