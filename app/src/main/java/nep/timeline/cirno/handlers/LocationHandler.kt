package nep.timeline.cirno.handlers

import android.os.IBinder
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.services.FreezerService
import nep.timeline.cirno.threads.FreezerHandler

object LocationHandler {
    @JvmStatic
    fun call(appRecord: AppRecord, set: Set<IBinder?>) {
        if (appRecord.isSystem())
            return

        if (set.isEmpty()) {
            if (appRecord.appState.setLocation(false)) {
                Log.d("应用 " + appRecord.getPackageNameWithUser() + " 结束定位")
                FreezerHandler.sendFreezeMessage(appRecord, 3000)
            }
        } else if (appRecord.appState.setLocation(true)) {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 开始定位")
            FreezerService.thaw(appRecord)
        }
    }
}
