package nep.timeline.cirno.utils

import nep.timeline.cirno.services.ProcessService

object AnrHelper {
    @JvmStatic
    fun blockANR(app: Any?): Boolean {
        if (app == null)
            return false
        val processRecord = ProcessService.getProcessRecord(app) ?: return false
        val appRecord = processRecord.getAppRecord() ?: return false
        return !appRecord.isSystem()
    }
}
