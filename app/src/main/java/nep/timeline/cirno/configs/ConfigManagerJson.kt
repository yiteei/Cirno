package nep.timeline.cirno.configs

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import com.topjohnwu.superuser.io.SuFile
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.configs.settings.ApplicationSettings
import nep.timeline.cirno.configs.settings.GlobalSettings
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.utils.RWUtils
import java.io.File
import java.io.IOException

class ConfigManagerJson {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private val globalSettingsName = "GlobalSettings.json"
    private val applicationSettingsName = "ApplicationSettings.json"

    fun readConfig() {
        try {
            val globalFile = File(GlobalVars.CONFIG_DIR, globalSettingsName)
            if (!globalFile.exists()) {
                GlobalVars.globalSettings = GlobalSettings()
                saveConfig()
            } else {
                val globalData = RWUtils.readConfig(GlobalVars.CONFIG_DIR + "/" + globalSettingsName)
                GlobalVars.globalSettings = gson.fromJson(globalData, GlobalSettings::class.java)
                if (GlobalVars.globalSettings == null) {
                    GlobalVars.globalSettings = GlobalSettings()
                    saveConfig()
                }
            }
            val applicationFile = File(GlobalVars.CONFIG_DIR, applicationSettingsName)
            if (!applicationFile.exists()) {
                GlobalVars.applicationSettings = ApplicationSettings()
                saveConfig()
            } else {
                val applicationData = RWUtils.readConfig(GlobalVars.CONFIG_DIR + "/" + applicationSettingsName)
                GlobalVars.applicationSettings = gson.fromJson(applicationData, ApplicationSettings::class.java)
                if (GlobalVars.applicationSettings == null) {
                    GlobalVars.applicationSettings = ApplicationSettings()
                    saveConfig()
                }
            }
        } catch (e: JsonSyntaxException) {
            resetConfig()
        } catch (e: JsonIOException) {
            resetConfig()
        }
    }

    fun saveConfig() {
        try {
            val globalSettings = GlobalVars.globalSettings
            if (globalSettings != null) {
                val globalConfigStr = gson.toJson(GlobalVars.globalSettings)
                RWUtils.writeStringToFile(File(GlobalVars.CONFIG_DIR, globalSettingsName), globalConfigStr)
            }
            val applicationSettings = GlobalVars.applicationSettings
            if (applicationSettings != null) {
                val applicationConfigStr = gson.toJson(GlobalVars.applicationSettings)
                RWUtils.writeStringToFile(File(GlobalVars.CONFIG_DIR, applicationSettingsName), applicationConfigStr)
            }
        } catch (e: IOException) {
            Log.e("Save Config", e)
        }
    }

    fun readConfigSU(): Boolean {
        var read = true
        try {
            val globalFile = SuFile(GlobalVars.CONFIG_DIR, globalSettingsName)
            if (!globalFile.exists()) {
                GlobalVars.globalSettings = GlobalSettings()
                read = false
            } else {
                val globalData = RWUtils.readConfig(globalFile)
                GlobalVars.globalSettings = gson.fromJson(globalData, GlobalSettings::class.java)
                if (GlobalVars.globalSettings == null) {
                    GlobalVars.globalSettings = GlobalSettings()
                    read = false
                }
            }
            val applicationFile = SuFile(GlobalVars.CONFIG_DIR, applicationSettingsName)
            if (!applicationFile.exists()) {
                GlobalVars.applicationSettings = ApplicationSettings()
                read = false
            } else {
                val applicationData = RWUtils.readConfig(applicationFile)
                GlobalVars.applicationSettings = gson.fromJson(applicationData, ApplicationSettings::class.java)
                if (GlobalVars.applicationSettings == null) {
                    GlobalVars.applicationSettings = ApplicationSettings()
                    read = false
                }
            }
        } catch (e: JsonSyntaxException) {
            resetConfig()
            return false
        } catch (e: JsonIOException) {
            resetConfig()
            return false
        }

        return read
    }

    fun saveConfigSU() {
        try {
            val globalSettings = GlobalVars.globalSettings
            if (globalSettings != null) {
                val globalConfigStr = gson.toJson(GlobalVars.globalSettings)
                RWUtils.writeStringToFileSU(SuFile(GlobalVars.CONFIG_DIR, globalSettingsName), globalConfigStr, false)
            }
            val applicationSettings = GlobalVars.applicationSettings
            if (applicationSettings != null) {
                val applicationConfigStr = gson.toJson(GlobalVars.applicationSettings)
                RWUtils.writeStringToFileSU(SuFile(GlobalVars.CONFIG_DIR, applicationSettingsName), applicationConfigStr, false)
            }
        } catch (e: IOException) {
            Log.e("Save Config", e)
        }
    }

    private fun resetConfig() {
        GlobalVars.globalSettings = GlobalSettings()
        GlobalVars.applicationSettings = ApplicationSettings()
        saveConfig()
    }
}
