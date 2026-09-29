package nep.timeline.cirno.configs.checkers

import nep.timeline.cirno.GlobalVars

object AppConfigs {
    @JvmStatic
    fun isWhiteApp(pkg: String?, userId: Int): Boolean {
        return GlobalVars.applicationSettings!!.whiteApps.contains(pkg + "#" + userId)
    }
}
