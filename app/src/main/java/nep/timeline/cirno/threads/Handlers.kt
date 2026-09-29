package nep.timeline.cirno.threads

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.log.Log

object Handlers {
    @JvmField
    val alarms: Handler = makeHandler("Alarms")

    @JvmField
    val network: Handler = makeHandler("Network")

    @JvmField
    val audio: Handler = makeHandler("Audio")

    @JvmField
    val location: Handler = makeHandler("Location")

    @JvmField
    val rekernel: Handler = makeHandler("ReKernel")

    @JvmField
    val log: Handler = makeHandlerBackground("Log")

    @JvmField
    val config: Handler = makeHandlerBackground("Config")

    @JvmStatic
    @JvmOverloads
    fun makeHandlerForeground(str: String, async: Boolean = false): Handler {
        return if (async)
            Handler.createAsync(makeLooperForeground(str))
        else
            Handler(makeLooperForeground(str))
    }

    @JvmStatic
    @JvmOverloads
    fun makeHandler(str: String, async: Boolean = false): Handler {
        return if (async)
            Handler.createAsync(makeLooper(str))
        else
            Handler(makeLooper(str))
    }

    @JvmStatic
    @JvmOverloads
    fun makeHandlerBackground(str: String, async: Boolean = false): Handler {
        return if (async)
            Handler.createAsync(makeLooperBackground(str))
        else
            Handler(makeLooperBackground(str))
    }

    @JvmStatic
    fun makeLooperForeground(str: String): Looper {
        val handlerThread = HandlerThread(GlobalVars.TAG + "-" + str, Process.THREAD_PRIORITY_FOREGROUND)
        handlerThread.setUncaughtExceptionHandler { t, e -> Log.e("线程 " + t.name + " 出现异常: " + e) }
        handlerThread.start()
        return handlerThread.looper
    }

    @JvmStatic
    fun makeLooperBackground(str: String): Looper {
        val handlerThread = HandlerThread(GlobalVars.TAG + "-" + str, Process.THREAD_PRIORITY_BACKGROUND)
        handlerThread.setUncaughtExceptionHandler { t, e -> Log.e("线程 " + t.name + " 出现异常: " + e) }
        handlerThread.start()
        return handlerThread.looper
    }

    @JvmStatic
    fun makeLooper(str: String): Looper {
        val handlerThread = HandlerThread(GlobalVars.TAG + "-" + str)
        handlerThread.setUncaughtExceptionHandler { t, e -> Log.e("线程 " + t.name + " 出现异常: " + e) }
        handlerThread.start()
        return handlerThread.looper
    }
}
