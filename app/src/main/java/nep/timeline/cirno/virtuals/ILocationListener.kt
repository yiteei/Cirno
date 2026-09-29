package nep.timeline.cirno.virtuals

import android.os.IBinder
import nep.timeline.cirno.reflect.CakeReflection

class ILocationListener(val instance: Any?) {
    fun asBinder(): IBinder? {
        if (instance == null)
            return null

        return CakeReflection.callMethod(instance, "asBinder") as IBinder?
    }
}
