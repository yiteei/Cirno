package nep.timeline.cirno.utils

import android.view.inputmethod.InputMethodInfo
import nep.timeline.cirno.entity.AppRecord
import java.util.HashMap

object InputMethodData {
    @JvmField
    @Volatile
    var instance: Any? = null

    @JvmField
    var inputMethods: MutableMap<String, InputMethodInfo>? = HashMap()

    @JvmField
    var currentInputMethodInfo: InputMethodInfo? = null

    @JvmField
    var currentInputMethodApp: AppRecord? = null
}
