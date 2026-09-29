package nep.timeline.cirno.services

import java.lang.reflect.Array
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.reflect.CakeReflection

object NetworkManagementService {
    @JvmField
    @Volatile
    var instance: Any? = null

    private var mNetdService: Any? = null
    private var UidRangeParcel: Class<*>? = null

    @JvmStatic
    fun setInstance(obj: Any?, classLoader: ClassLoader?) {
        instance = obj
        mNetdService = CakeReflection.getObjectField(obj, "mNetdService")
        UidRangeParcel = CakeReflection.findClass("android.net.UidRangeParcel", classLoader)
    }

    @JvmStatic
    fun socketDestroy(appRecord: AppRecord) {
        val uidRangeParcels = Array.newInstance(UidRangeParcel, 1)
        val uid = appRecord.uid
        Array.set(uidRangeParcels, 0, CakeReflection.newInstance(UidRangeParcel!!, uid, uid))
        CakeReflection.callMethod(mNetdService!!, "socketDestroy", uidRangeParcels, IntArray(0))
        Log.d(appRecord.getPackageNameWithUser() + " 断开网络连接")
    }
}
