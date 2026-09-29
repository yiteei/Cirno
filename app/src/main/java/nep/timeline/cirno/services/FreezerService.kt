package nep.timeline.cirno.services

import android.os.Build
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.threads.FreezerHandler
import nep.timeline.cirno.threads.Handlers
import nep.timeline.cirno.utils.ForceAppStandbyListener
import nep.timeline.cirno.utils.FrozenRW

object FreezerService {
    @JvmStatic
    fun freezer(appRecord: AppRecord) {
        if (appRecord.isFrozen || appRecord.isSystem() || appRecord.appState.isVisible() || appRecord.appState.isLocation() || appRecord.appState.isAudio() || appRecord.appState.isRecording() || appRecord.appState.isVpn())
            return

        for (processRecord in appRecord.processRecords) {
            if (processRecord.isDeathProcess() || processRecord.isFrozen)
                continue

            FrozenRW.frozen(processRecord.runningUid, processRecord.getPid())
            processRecord.isFrozen = true
        }

        Handlers.alarms.post { ForceAppStandbyListener.removeAlarmsForUid(appRecord) }
        Handlers.network.post {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                CachedAppOptimizer.reportOneUidFrozenStateChanged(appRecord.uid, true)
                return@post
            }
            NetworkManagementService.socketDestroy(appRecord)
        }
        appRecord.isFrozen = true
    }

    @JvmStatic
    fun thaw(appRecord: AppRecord) {
        FreezerHandler.removeAppMessage(appRecord)

        if (!appRecord.isFrozen)
            return

        for (processRecord in appRecord.processRecords) {
            if (processRecord.isDeathProcess() || !processRecord.isFrozen)
                continue

            FrozenRW.thaw(processRecord.runningUid, processRecord.getPid())
            processRecord.isFrozen = false
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM)
            Handlers.network.post { CachedAppOptimizer.reportOneUidFrozenStateChanged(appRecord.uid, false) }
        appRecord.isFrozen = false
    }

    @JvmStatic
    fun temporaryUnfreezeIfNeed(uid: Int, reason: String, interval: Long) {
        val appRecords = AppService.getByUid(uid)

        if (appRecords == null || appRecords.isEmpty())
            return

        for (appRecord in appRecords) {
            if (appRecord == null)
                continue

            temporaryUnfreezeIfNeed(appRecord, reason, interval)
        }
    }

    @JvmStatic
    fun temporaryUnfreezeIfNeed(packageName: String?, userId: Int, reason: String, interval: Long) {
        temporaryUnfreezeIfNeed(AppService.get(packageName, userId), reason, interval)
    }

    @JvmStatic
    fun temporaryUnfreezeIfNeed(appRecord: AppRecord?, reason: String, interval: Long) {
        if (appRecord == null || appRecord.isSystem())
            return

        if (appRecord.isFrozen)
            Log.i(appRecord.getPackageNameWithUser() + " " + reason)

        thaw(appRecord)
        FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord, interval)
    }
}
