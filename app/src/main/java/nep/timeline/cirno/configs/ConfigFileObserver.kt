package nep.timeline.cirno.configs

import android.os.FileObserver
import android.os.Handler
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.threads.Handlers
import java.io.File

class ConfigFileObserver : FileObserver(GlobalVars.CONFIG_DIR, DELETE or DELETE_SELF or MODIFY or MOVE_SELF) {
    init {
        reInit()
        ConfigManager.readConfig()
    }

    override fun onEvent(event: Int, path: String?) {
        val handler: Handler = Handlers.config
        handler.removeCallbacksAndMessages(null)
        when (event and ALL_EVENTS) {
            DELETE, DELETE_SELF -> {
                handler.postDelayed({
                    ConfigManager.readConfig()
                    reInit()
                }, 2000)
            }

            MODIFY, MOVE_SELF -> {
                handler.postDelayed({ ConfigManager.readConfig() }, 2000)
            }
        }
    }

    fun reInit() {
        val configDir = File(GlobalVars.CONFIG_DIR)
        if (!configDir.exists())
            configDir.mkdir()
        val logDir = File(GlobalVars.LOG_DIR)
        if (!logDir.exists())
            logDir.mkdir()
    }
}
