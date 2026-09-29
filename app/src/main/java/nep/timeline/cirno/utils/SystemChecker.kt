package nep.timeline.cirno.utils

import nep.timeline.cirno.reflect.CakeReflection

object SystemChecker {
    @JvmStatic
    fun isSamsung(classLoader: ClassLoader): Boolean {
        return CakeReflection.findClassIfExists("com.android.server.am.FreecessController", classLoader) != null
    }

    @JvmStatic
    fun isXiaomi(classLoader: ClassLoader): Boolean {
        return CakeReflection.findClassIfExists("com.miui.server.greeze.GreezeManagerService", classLoader) != null
    }

    @JvmStatic
    fun isOplus(classLoader: ClassLoader): Boolean {
        return CakeReflection.findClassIfExists("com.android.server.am.OplusHansManager", classLoader) != null
    }

    @JvmStatic
    fun isHuawei(classLoader: ClassLoader): Boolean {
        return CakeReflection.findClassIfExists("com.huawei.turbozone.ITurboService", classLoader) != null
    }

    @JvmStatic
    fun isVivo(classLoader: ClassLoader): Boolean {
        return CakeReflection.findClassIfExists("com.android.server.am.IVivoBroadcastQueueModern", classLoader) != null
    }

    @JvmStatic
    fun isNubia(classLoader: ClassLoader): Boolean {
        return CakeReflection.findClassIfExists("cn.nubia.server.appmgmt.ApplicationControllerUtils", classLoader) != null
    }
}
