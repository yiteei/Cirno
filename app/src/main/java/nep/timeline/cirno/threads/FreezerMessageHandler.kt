package nep.timeline.cirno.threads

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.NonNull
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.services.FreezerService

class FreezerMessageHandler(looper: Looper) : Handler(looper) {
    override fun handleMessage(@NonNull message: Message) {
        super.handleMessage(message)
        try {
            FreezerService.freezer(message.obj as AppRecord)
        } catch (th: Throwable) {
            Log.e("Freezer", th)
        }
    }
}
