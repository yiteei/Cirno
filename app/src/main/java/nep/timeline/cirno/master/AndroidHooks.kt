package nep.timeline.cirno.master

import android.os.Build
import android.os.FileObserver
import nep.timeline.cirno.configs.ConfigFileObserver
import nep.timeline.cirno.hooks.android.activity.ActivityManagerServiceHook
import nep.timeline.cirno.hooks.android.activity.ActivityStatsHook
import nep.timeline.cirno.hooks.android.alarms.AlarmManagerService
import nep.timeline.cirno.hooks.android.anr.ANRErrorStateHook
import nep.timeline.cirno.hooks.android.anr.ANRHelperHooks
import nep.timeline.cirno.hooks.android.anr.ANRHook
import nep.timeline.cirno.hooks.android.audio.AudioStateHook
import nep.timeline.cirno.hooks.android.audio.PlayerBanHook
import nep.timeline.cirno.hooks.android.audio.SendMediaButtonHook
import nep.timeline.cirno.hooks.android.binder.HansKernelUnfreezeHook
import nep.timeline.cirno.hooks.android.binder.MilletBinderTransHook
import nep.timeline.cirno.hooks.android.broadcast.BroadcastDeliveryHook
import nep.timeline.cirno.hooks.android.broadcast.BroadcastIntentHook
import nep.timeline.cirno.hooks.android.broadcast.BroadcastSkipHook
import nep.timeline.cirno.hooks.android.input.InputMethodManagerService
import nep.timeline.cirno.hooks.android.intent.PendingIntentHook
import nep.timeline.cirno.hooks.android.location.ListenerRegisterHook
import nep.timeline.cirno.hooks.android.location.ListenerUnregisterHook
import nep.timeline.cirno.hooks.android.network.NetworkManagerHook
import nep.timeline.cirno.hooks.android.optimizer.CacheEnableFreezerHook
import nep.timeline.cirno.hooks.android.optimizer.CacheUseFreezerHook
import nep.timeline.cirno.hooks.android.process.ProcessAddHook
import nep.timeline.cirno.hooks.android.process.ProcessRemoveHook
import nep.timeline.cirno.hooks.android.recorder.RecorderEventHook
import nep.timeline.cirno.hooks.android.recorder.ReleaseRecorderHook
import nep.timeline.cirno.hooks.android.signal.SendSignalHook
import nep.timeline.cirno.hooks.android.signal.SendSignalQuietHook
import nep.timeline.cirno.hooks.android.vpn.VpnStateHook
import nep.timeline.cirno.hooks.android.wakelock.WakeLockHook
import nep.timeline.cirno.services.BinderService

object AndroidHooks {
    @JvmStatic
    fun start(classLoader: ClassLoader?) {
        // Config
        val fileObserver: FileObserver = ConfigFileObserver()
        fileObserver.startWatching()

        // Cached
        CacheEnableFreezerHook(classLoader)
        CacheUseFreezerHook(classLoader)
        // ANR
        ANRHook(classLoader)
        ANRErrorStateHook(classLoader)
        ANRHelperHooks(classLoader)
        // Signal
        SendSignalHook(classLoader)
        SendSignalQuietHook(classLoader)
        // Audio
        AudioStateHook(classLoader)
        PlayerBanHook(classLoader)
        SendMediaButtonHook(classLoader)
        // Location
        ListenerRegisterHook(classLoader)
        ListenerUnregisterHook(classLoader)
        // InputMethod
        InputMethodManagerService(classLoader)
        // Network
        NetworkManagerHook(classLoader)
        // Alarms
        AlarmManagerService(classLoader)
        // Broadcast
        BroadcastIntentHook(classLoader)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
            BroadcastDeliveryHook(classLoader)
        else
            BroadcastSkipHook(classLoader)
        // WakeLock
        WakeLockHook(classLoader)
        // Activity
        ActivityManagerServiceHook(classLoader)
        ActivityStatsHook(classLoader)
        // Process
        ProcessAddHook(classLoader)
        ProcessRemoveHook(classLoader)
        // Binder
        HansKernelUnfreezeHook(classLoader)
        MilletBinderTransHook(classLoader)
        // Recorder
        RecorderEventHook(classLoader)
        ReleaseRecorderHook(classLoader)
        // Vpn
        VpnStateHook(classLoader)
        // Intent
        PendingIntentHook(classLoader)
        // ReKernel
        BinderService.start(classLoader)
    }
}
