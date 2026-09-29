package nep.timeline.cirno.handlers

import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.services.FreezerService
import nep.timeline.cirno.threads.FreezerHandler

object AudioHandler {
    const val PLAYER_STATE_RELEASED: Int = 0
    const val PLAYER_STATE_IDLE: Int = 1
    const val PLAYER_STATE_STARTED: Int = 2
    const val PLAYER_STATE_PAUSED: Int = 3
    const val PLAYER_STATE_STOPPED: Int = 4

    @JvmField
    val LISTEN_EVENT: Set<Int> = setOf(PLAYER_STATE_RELEASED, PLAYER_STATE_IDLE, PLAYER_STATE_STARTED, PLAYER_STATE_PAUSED, PLAYER_STATE_STOPPED)

    @JvmStatic
    fun call(appRecord: AppRecord, event: Int, interfaceId: Int) {
        val set = appRecord.appState.interfaceIds
        if (event == PLAYER_STATE_STARTED)
            set.add(interfaceId)
        else
            set.remove(interfaceId)

        if (set.isEmpty()) {
            if (appRecord.appState.setAudio(false)) {
                Log.d("应用 " + appRecord.getPackageNameWithUser() + " 停止播放音频")
                FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord, 6000)
            }
        } else if (appRecord.appState.setAudio(true)) {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 开始播放音频")
            FreezerService.thaw(appRecord)
        }
    }
}
