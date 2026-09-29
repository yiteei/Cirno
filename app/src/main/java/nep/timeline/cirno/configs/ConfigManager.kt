package nep.timeline.cirno.configs

object ConfigManager {
    @JvmField
    val manager: ConfigManagerJson = ConfigManagerJson()

    @JvmStatic
    fun readConfig() {
        manager.readConfig()
        manager.saveConfig()
    }

    @JvmStatic
    fun saveConfig() {
        manager.saveConfig()
        manager.readConfig()
    }
}
