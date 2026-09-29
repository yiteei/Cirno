package nep.timeline.cirno.services

import android.content.pm.ApplicationInfo
import android.os.Process
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.utils.PKGUtils
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap

object AppService {
    private val APP_RECORD_MAP: ConcurrentHashMap<Int, ConcurrentHashMap<String, AppRecord>> = ConcurrentHashMap()
    private val UID_RECORD_MAP: ConcurrentHashMap<Int, MutableList<AppRecord?>?> = ConcurrentHashMap()

    @JvmStatic
    fun get(packageName: String?, userId: Int): AppRecord? {
        if (packageName == null || packageName == "android")
            return null

        val appRecords = APP_RECORD_MAP.computeIfAbsent(userId) { ConcurrentHashMap() }

        if (appRecords.containsKey(packageName))
            return appRecords[packageName]

        val applicationInfo: ApplicationInfo = ActivityManagerService.getApplicationInfo(packageName, userId) ?: return null

        return appRecords.put(packageName, AppRecord(applicationInfo))
    }

    @JvmStatic
    fun getByUid(uid: Int): MutableList<AppRecord?>? {
        try {
            if (!UID_RECORD_MAP.containsKey(uid))
                putAppToCacheByUid(uid)
            val records = UID_RECORD_MAP[uid]
            if (records == null)
                return ArrayList()
            return records
        } catch (ignored: Throwable) {

        }
        return null
    }

    @Synchronized
    private fun putAppToCacheByUid(uid: Int) {
        if (uid <= Process.SYSTEM_UID) {
            UID_RECORD_MAP[uid] = null
            return
        }

        val keys = ActivityManagerService.getPackagesForUid(uid)
        if (keys == null || keys.isEmpty()) {
            UID_RECORD_MAP[uid] = null
            return
        }

        val appRecords: MutableList<AppRecord?> = ArrayList()
        for (key in keys) {
            val split = key.split(":").toTypedArray()
            val userId = if (split.size == 1) PKGUtils.getUserId(uid) else split[1].trim().toInt()
            appRecords.add(get(split[0], userId))
        }

        UID_RECORD_MAP[uid] = appRecords
    }
}
