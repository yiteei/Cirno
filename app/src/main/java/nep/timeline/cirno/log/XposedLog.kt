package nep.timeline.cirno.log

import android.util.Log
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.framework.XposedInstance

object XposedLog {
    @JvmStatic
    fun e(msg: String, throwable: Throwable) {
        XposedInstance.log(Log.ERROR, GlobalVars.TAG, msg, throwable)
    }

    @JvmStatic
    fun e(msg: String) {
        XposedInstance.log(Log.ERROR, GlobalVars.TAG, msg)
    }

    @JvmStatic
    fun i(msg: String) {
        XposedInstance.log(Log.INFO, GlobalVars.TAG, msg)
    }

    @JvmStatic
    fun w(msg: String, throwable: Throwable) {
        XposedInstance.log(Log.WARN, GlobalVars.TAG, msg, throwable)
    }

    @JvmStatic
    fun w(msg: String) {
        XposedInstance.log(Log.WARN, GlobalVars.TAG, msg)
    }

    @JvmStatic
    fun d(msg: String, throwable: Throwable) {
        XposedInstance.log(Log.DEBUG, GlobalVars.TAG, msg, throwable)
    }

    @JvmStatic
    fun d(msg: String) {
        XposedInstance.log(Log.DEBUG, GlobalVars.TAG, msg)
    }
}
