package nep.timeline.cirno.entity

import android.content.pm.ApplicationInfo
import nep.timeline.cirno.CommonConstants
import nep.timeline.cirno.configs.checkers.AppConfigs
import nep.timeline.cirno.utils.InputMethodData
import nep.timeline.cirno.utils.PKGUtils
import nep.timeline.cirno.virtuals.ProcessRecord
import java.util.concurrent.CopyOnWriteArrayList

open class AppRecord(val applicationInfo: ApplicationInfo) {
    val packageName: String? = applicationInfo.packageName
    val userId: Int = PKGUtils.getUserId(applicationInfo.uid)
    val uid: Int = applicationInfo.uid
    var appState: AppState = AppState(this)
    var isFrozen: Boolean = false
    val processRecords: MutableList<ProcessRecord> = CopyOnWriteArrayList()

    fun isSystem(): Boolean {
        return packageName == null || this == InputMethodData.currentInputMethodApp || PKGUtils.isSystemApp(applicationInfo) || AppConfigs.isWhiteApp(packageName, userId) || CommonConstants.isWhitelistApps(packageName)
    }

    fun getPackageNameWithUser(): String? {
        if (userId == 0)
            return packageName
        return packageName + ":" + userId
    }

    fun reset() {
        this.isFrozen = false
        this.appState = AppState(this)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false
        if (other === this)
            return true
        if (other is AppRecord)
            return userId == other.userId && packageName == other.packageName
        return false
    }

    override fun hashCode(): Int {
        val userId = this.userId
        val packageName = this.packageName
        return ((userId.hashCode() + 59) * 59) + (packageName?.hashCode() ?: 43)
    }
}
