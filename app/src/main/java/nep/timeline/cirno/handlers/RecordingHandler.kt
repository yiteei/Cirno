package nep.timeline.cirno.handlers

import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.services.FreezerService
import nep.timeline.cirno.threads.FreezerHandler

object RecordingHandler {
    const val RECORD_RIID_INVALID: Int = -1
    const val RECORDER_STATE_STARTED: Int = 0
    const val RECORDER_STATE_STOPPED: Int = 1

    @JvmStatic
    fun call(appRecord: AppRecord, event: Int, riid: Int) {
        val set = appRecord.appState.recodingIds
        if (event == RECORDER_STATE_STARTED)
            set.add(riid)
        else
            set.remove(riid)

        if (set.isEmpty()) {
            if (appRecord.appState.setRecording(false)) {
                Log.d("应用 " + appRecord.getPackageNameWithUser() + " 停止录音")
                FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord, 3000)
            }
        } else if (appRecord.appState.setRecording(true)) {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 开始录音")
            FreezerService.thaw(appRecord)
        }
    }
}
