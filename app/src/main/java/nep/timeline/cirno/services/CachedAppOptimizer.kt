package nep.timeline.cirno.services

import android.os.Build
import android.os.RemoteException
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.reflect.CakeReflection

object CachedAppOptimizer {
    @JvmStatic
    @Volatile
    var instance: Any? = null

    const val UID_FROZEN_STATE_FROZEN: Int = 1
    const val UID_FROZEN_STATE_UNFROZEN: Int = 2

    @JvmStatic
    fun reportOneUidFrozenStateChanged(uid: Int, frozenState: Boolean) {
        val uids = IntArray(1)
        val frozenStates = IntArray(1)

        uids[0] = uid
        frozenStates[0] = if (frozenState) UID_FROZEN_STATE_FROZEN else UID_FROZEN_STATE_UNFROZEN

        reportUidFrozenStateChanged(uids, frozenStates)
    }

    @JvmStatic
    fun reportUidFrozenStateChanged(uids: IntArray, frozenStates: IntArray) {
        if (instance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return

        val mUidFrozenStateChangedCallbackList = CakeReflection.getObjectField(CakeReflection.getObjectField(instance, "mAm"), "mUidFrozenStateChangedCallbackList")
        synchronized(mUidFrozenStateChangedCallbackList!!) {
            val n = CakeReflection.callMethod(mUidFrozenStateChangedCallbackList, "beginBroadcast") as Int
            for (i in 0 until n) {
                try {
                    CakeReflection.callMethod(
                        CakeReflection.callMethod(mUidFrozenStateChangedCallbackList, "getBroadcastItem", i)!!,
                        "onUidFrozenStateChanged",
                        uids,
                        frozenStates
                    )
                } catch (e: CakeReflection.InvocationTargetError) {
                    /*
                     * The process at the other end has died or otherwise gone away.
                     * According to spec, RemoteCallbackList will take care of unregistering any
                     * object associated with that process - we are safe to ignore the exception
                     * here.
                     */
                    if (e.cause !is RemoteException) {
                        Log.e("reportUidFrozenStateChanged", e)
                    }
                }
            }
            CakeReflection.callMethod(mUidFrozenStateChangedCallbackList, "finishBroadcast")
        }
    }
}
