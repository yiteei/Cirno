package nep.timeline.cirno

import nep.timeline.cirno.configs.settings.ApplicationSettings
import nep.timeline.cirno.configs.settings.GlobalSettings

object GlobalVars {
    const val TAG = "Cirno"
    const val CONFIG = "Cirno"
    const val CONFIG_DIR = "/data/misc/" + CONFIG
    const val LOG_DIR = CONFIG_DIR + "/log"

    @JvmField
    var classLoader: ClassLoader? = null

    @JvmField
    var globalSettings: GlobalSettings? = null

    @JvmField
    var applicationSettings: ApplicationSettings? = null
}
