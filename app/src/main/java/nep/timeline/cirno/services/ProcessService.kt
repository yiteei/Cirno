package nep.timeline.cirno.services

import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.threads.FreezerHandler
import nep.timeline.cirno.utils.FrozenRW
import nep.timeline.cirno.virtuals.ProcessRecord
import java.util.concurrent.ConcurrentHashMap

object ProcessService {
    private val PROCESS_NAME_MAP: ConcurrentHashMap<String?, ConcurrentHashMap<Int, ProcessRecord>> = ConcurrentHashMap()
    private val lock = Any()

    @JvmStatic
    fun addProcessRecord(record: Any?) {
        val processRecord = ProcessRecord(record)
        val appRecord = processRecord.getAppRecord() ?: return

        synchronized(lock) {
            PROCESS_NAME_MAP.computeIfAbsent(processRecord.processName) { ConcurrentHashMap() }[processRecord.runningUid] = processRecord
            appRecord.processRecords.add(processRecord)
        }

        FreezerHandler.sendFreezeMessage(appRecord, 3000)
    }

    @JvmStatic
    fun removeProcessRecord(processRecord: ProcessRecord) {
        removeProcessRecord(processRecord.processName, processRecord.runningUid)
    }

    @JvmStatic
    fun removeProcessRecord(name: String?, uid: Int) {
        synchronized(lock) {
            if (PROCESS_NAME_MAP.containsKey(name)) {
                val processRecord = PROCESS_NAME_MAP.computeIfAbsent(name) { ConcurrentHashMap() }.remove(uid) ?: return
                if (processRecord.isFrozen)
                    FrozenRW.thaw(processRecord.runningUid, processRecord.getPid())
                val appRecord = processRecord.getAppRecord() ?: return
                appRecord.processRecords.remove(processRecord)
                if (appRecord.processRecords.isEmpty())
                    appRecord.reset()
            }
        }
    }

    @JvmStatic
    fun getProcessRecord(record: Any?): ProcessRecord? {
        if (record == null)
            return null
        val processRecord = ProcessRecord(record)
        return getProcessRecord(processRecord.processName, processRecord.runningUid)
    }

    @JvmStatic
    fun getProcessRecord(processName: String?, uid: Int): ProcessRecord? {
        if (processName == null || processName.isEmpty())
            return null
        val map = PROCESS_NAME_MAP[processName] ?: return null
        return map[uid]
    }

    @JvmStatic
    fun getProcessRecordByPid(pid: Int): ProcessRecord? {
        val processRecord: ProcessRecord?
        val mPidsSelfLocked = ActivityManagerService.getPidsSelfLocked()
        synchronized(mPidsSelfLocked!!) {
            processRecord = getProcessRecord(CakeReflection.callMethod(mPidsSelfLocked, "get", pid))
        }
        return processRecord
    }
}
