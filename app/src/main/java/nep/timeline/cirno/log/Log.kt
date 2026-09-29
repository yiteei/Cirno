package nep.timeline.cirno.log

import android.os.Build
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.threads.Handlers
import nep.timeline.cirno.utils.RWUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Log {
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
    private val currentLog = File(GlobalVars.LOG_DIR, "current.log")

    init {
        i("设备Android SDK: " + Build.VERSION.SDK_INT)
    }

    @JvmStatic
    fun d(msg: String) {
        execute("调试", msg)
    }

    @JvmStatic
    fun d(msg: String, throwable: Throwable) {
        d(msg + " 失败: " + throwable.message)
    }

    @JvmStatic
    fun i(msg: String) {
        execute("信息", msg)
    }

    @JvmStatic
    fun w(msg: String) {
        execute("警告", msg)
    }

    @JvmStatic
    fun w(msg: String, throwable: Throwable) {
        w(msg + " 失败: " + throwable.message)
    }

    @JvmStatic
    fun e(msg: String) {
        execute("错误", msg)
    }

    @JvmStatic
    fun e(msg: String, throwable: Throwable) {
        e(msg + " 失败: " + throwable.message)
    }

    @JvmStatic
    fun execute(level: String, msg: String) {
        Handlers.log.post { fileLog(simpleDateFormat.format(Date()) + " " + level.uppercase(Locale.JAPAN) + " -> " + msg) }
    }

    @JvmStatic
    fun fileLog(msg: String) {
        try {
            RWUtils.writeStringToFile(currentLog, msg, true)
        } catch (e: IOException) {
            Log.e("Log write failed! msg: " + msg, e)
        }
    }
}
