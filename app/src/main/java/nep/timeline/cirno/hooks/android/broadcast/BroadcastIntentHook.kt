package nep.timeline.cirno.hooks.android.broadcast

import android.content.Intent
import android.os.Build
import nep.timeline.cirno.entity.AppRecord
import nep.timeline.cirno.log.Log
import nep.timeline.cirno.log.XposedLog
import nep.timeline.cirno.reflect.CakeHooker
import nep.timeline.cirno.reflect.CakeReflection
import nep.timeline.cirno.services.AppService
import nep.timeline.cirno.services.FreezerService
import java.lang.reflect.Method

class BroadcastIntentHook(classLoader: ClassLoader?) {
    init {
        hook(classLoader)
    }

    private fun hook(classLoader: ClassLoader?) {
        try {
            var clazz = CakeReflection.findClassIfExists("com.android.server.am.ActivityManagerService", classLoader)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val controller = CakeReflection.findClassIfExists("com.android.server.am.BroadcastController", classLoader)
                if (controller != null)
                    clazz = controller
            }

            if (clazz == null) {
                Log.e("无法监听广播意图!")
                return
            }

            var targetMethod: Method? = null
            for (method in clazz.declaredMethods)
                if (method.name == "broadcastIntentLocked" && (targetMethod == null || targetMethod.parameterTypes.size < method.parameterTypes.size))
                    targetMethod = method

            val target: Method = targetMethod ?: run {
                Log.e("无法监听广播意图!")
                return
            }

            CakeHooker.hookBefore(target) { callback ->
                val intentArgsIndex = 3

                var userIdIndex = 19
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2 && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    userIdIndex = 20
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU)
                    userIdIndex = 21

                val intent = callback.getArgs()[intentArgsIndex] as Intent?
                val userId = callback.getArgs()[userIdIndex] as Int
                if (intent != null) {
                    val action = intent.action

                    if (action == null || !action.endsWith(".android.c2dm.intent.RECEIVE") || action == "org.unifiedpush.android.connector.MESSAGE" || action == "com.meizu.flyme.push.intent.MESSAGE")
                        return@hookBefore

                    val packageName = (if (intent.component == null) intent.getPackage() else intent.component!!.packageName)

                    if (packageName == null)
                        return@hookBefore

                    val appRecord: AppRecord = AppService.get(packageName, userId) ?: return@hookBefore

                    FreezerService.temporaryUnfreezeIfNeed(appRecord, "MESSAGE PUSH", 3000)
                }
            }

            Log.i("监听广播意图")
        } catch (throwable: Throwable) {
            XposedLog.e("无法监听广播意图, 异常:", throwable)
            Log.e("监听广播意图失败", throwable)
        }
    }
}
