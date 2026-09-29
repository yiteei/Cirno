package nep.timeline.cirno.threads

import android.os.Handler
import android.os.Message
import nep.timeline.cirno.entity.AppRecord

object FreezerHandler {
    @JvmField
    val handler: Handler = FreezerMessageHandler(Handlers.makeLooper("Freezer"))

    @JvmStatic
    fun removeAppMessage(appRecord: AppRecord) {
        if (handler.hasMessages(0, appRecord))
            handler.removeMessages(0, appRecord)
    }

    @JvmStatic
    fun sendFreezeMessage(appRecord: AppRecord, delay: Long) {
        if (handler.hasMessages(0, appRecord))
            return

        sendFreezeMessageIgnoreMessages(appRecord, delay)
    }

    @JvmStatic
    fun sendFreezeMessageIgnoreMessages(appRecord: AppRecord, delay: Long) {
        removeAppMessage(appRecord)

        val obtain: Message = handler.obtainMessage(0, appRecord)
        if (delay < 1)
            handler.sendMessage(obtain)
        else
            handler.sendMessageDelayed(obtain, delay)
    }
}
