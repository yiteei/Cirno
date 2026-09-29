package nep.timeline.cirno.entity

import android.os.IBinder
import java.util.HashSet

open class AppState(val parent: AppRecord) {
    private var visibleState = false
    private var locationState = false
    private var audioState = false
    private var recordingState = false
    private var vpnState = false
    val activities: MutableSet<IBinder?> = HashSet()
    val locationListeners: MutableSet<IBinder?> = HashSet()
    val interfaceIds: MutableSet<Int> = HashSet()
    val recodingIds: MutableSet<Int> = HashSet()

    fun isVisible(): Boolean = visibleState

    fun isLocation(): Boolean = locationState

    fun isAudio(): Boolean = audioState

    fun isRecording(): Boolean = recordingState

    fun isVpn(): Boolean = vpnState

    @Synchronized
    fun setVisible(value: Boolean): Boolean {
        if (visibleState == value)
            return false
        visibleState = value
        return true
    }

    @Synchronized
    fun setLocation(value: Boolean): Boolean {
        if (locationState == value)
            return false
        locationState = value
        return true
    }

    @Synchronized
    fun setAudio(value: Boolean): Boolean {
        if (audioState == value)
            return false
        audioState = value
        return true
    }

    @Synchronized
    fun setRecording(value: Boolean): Boolean {
        if (recordingState == value)
            return false
        recordingState = value
        return true
    }

    @Synchronized
    fun setVpn(value: Boolean): Boolean {
        if (vpnState == value)
            return false
        vpnState = value
        return true
    }
}
