package nep.timeline.cirno.hooks.android.activity

import android.app.usage.UsageEvents
import android.os.IBinder
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.framework.MethodHook
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.services.FreezerService
import nep.timeline.cirno.threads.FreezerHandler
import nep.timeline.cirno.virtuals.ActivityRecord
import java.util.concurrent.ConcurrentHashMap

class ActivityStatsHook(classLoader: ClassLoader?) : MethodHook(classLoader) {
    private val activityRecords: ConcurrentHashMap<IBinder?, ActivityRecord> = ConcurrentHashMap()
    private val events: List<Int> = listOf(
        UsageEvents.Event.ACTIVITY_RESUMED,
        UsageEvents.Event.ACTIVITY_PAUSED,
        UsageEvents.Event.ACTIVITY_STOPPED,
        UsageEvents.Event.ACTIVITY_STOPPED + 1
    )

    override fun getTargetClass(): String {
        return "com.android.server.wm.ActivityTaskManagerService"
    }

    override fun getTargetMethod(): String {
        return "updateActivityUsageStats"
    }

    override fun getTargetParam(): Array<out Any?> {
        return arrayOf("com.android.server.wm.ActivityRecord", Int::class.javaPrimitiveType!!)
    }

    override fun getTargetHook(): CakeHooker.Callback? {
        return object : CakeHooker.Callback {
            override fun call(callback: CakeHooker.BeforeHookCallback) {
                val event = callback.getArgs()[1] as Int

                if (!events.contains(event))
                    return

                val activityObject = callback.getArgs()[0]
                if (activityObject == null)
                    return

                val record = ActivityRecord(activityObject)

                val activityRecord: ActivityRecord? = if (event == UsageEvents.Event.ACTIVITY_RESUMED || event == UsageEvents.Event.ACTIVITY_PAUSED)
                    activityRecords.computeIfAbsent(record.token) { record }
                else
                    activityRecords.remove(record.token)

                if (activityRecord == null)
                    return

                val appRecord: AppRecord = activityRecord.toAppRecord() ?: return

                if (event == UsageEvents.Event.ACTIVITY_RESUMED)
                    appRecord.appState.activities.add(activityRecord.token)
                else
                    appRecord.appState.activities.remove(activityRecord.token)

                if (appRecord.appState.activities.isEmpty()) {
                    if (appRecord.appState.setVisible(false)) {
                        Log.d(appRecord.getPackageNameWithUser() + " 进入后台")
                        FreezerHandler.sendFreezeMessage(appRecord, 3000)
                    }
                } else if (appRecord.appState.setVisible(true)) {
                    Log.d(appRecord.getPackageNameWithUser() + " 进入前台")
                    FreezerService.thaw(appRecord)
                }
            }
        }
    }
}
