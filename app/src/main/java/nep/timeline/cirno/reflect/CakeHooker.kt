package nep.timeline.cirno.reflect

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

object CakeHooker {
    @JvmStatic
    var xposedModule: XposedModule? = null

    @JvmStatic
    var hostClassLoader: ClassLoader? = null

    fun interface BeforeCallback {
        fun call(callback: BeforeHookCallback)
    }

    fun interface AfterCallback {
        fun call(callback: AfterHookCallback)
    }

    interface Callback {
        fun call(callback: BeforeHookCallback) {}

        fun call(callback: AfterHookCallback) {}
    }

    interface ReplacementCallback : Callback {
        @Throws(Throwable::class)
        fun call(chain: XposedInterface.Chain): Any?
    }

    class BeforeHookCallback(private val chain: XposedInterface.Chain) {
        var isSkipped: Boolean = false
            private set

        var skipResult: Any? = null
            private set

        var isThrown: Boolean = false
            private set

        var throwable: Throwable? = null
            private set

        fun getThisObject(): Any? {
            return chain.thisObject
        }

        fun getArgs(): Array<Any?> {
            return chain.args.toTypedArray()
        }

        @Throws(Throwable::class)
        fun invokeOriginalMethod(): Any? {
            return chain.proceed()
        }

        fun getExecutable(): Executable {
            return chain.executable
        }

        fun returnAndSkip(result: Any?) {
            isSkipped = true
            skipResult = result
        }

        fun throwAndSkip(result: Throwable) {
            isThrown = true
            throwable = result
        }
    }

    class AfterHookCallback(private val chain: XposedInterface.Chain, result: Any?, throwable: Throwable?) {
        @JvmField
        var result: Any? = result

        @JvmField
        var throwable: Throwable? = throwable

        fun getThisObject(): Any? {
            return chain.thisObject
        }

        fun getArgs(): Array<Any?> {
            return chain.args.toTypedArray()
        }
    }

    class CustomHooker : XposedInterface.Hooker {
        private val beforeCallback: BeforeCallback?
        private val afterCallback: AfterCallback?
        private var callback: Callback? = null
        private val useCallback: Boolean

        constructor(beforeCallback: BeforeCallback?, afterCallback: AfterCallback?) {
            this.beforeCallback = beforeCallback ?: BeforeCallback { }
            this.afterCallback = afterCallback ?: AfterCallback { }
            this.useCallback = false
        }

        constructor(callback: Callback) {
            this.beforeCallback = null
            this.afterCallback = null
            this.callback = callback
            this.useCallback = true
        }

        @Throws(Throwable::class)
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val callback = this.callback
            if (useCallback && callback is ReplacementCallback)
                return callback.call(chain)

            var result: Any? = null
            var throwable: Throwable? = null
            var skipped = false
            var thrown = false

            val bcb = BeforeHookCallback(chain)
            if (useCallback)
                callback?.call(bcb)
            else
                beforeCallback?.call(bcb)

            if (bcb.isSkipped) {
                result = bcb.skipResult
                skipped = true
            }

            if (bcb.isThrown) {
                throwable = bcb.throwable
                thrown = true
            }

            if (!skipped && !thrown) {
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                }
            }

            val acb = AfterHookCallback(chain, result, throwable)
            if (useCallback)
                callback?.call(acb)
            else
                afterCallback?.call(acb)

            result = acb.result
            throwable = acb.throwable

            if (throwable != null)
                throw throwable

            return result
        }
    }

    @JvmStatic
    fun hookBefore(member: Executable, callback: BeforeCallback): XposedInterface.HookHandle {
        return xposedModule!!
            .hook(member)
            .intercept(CustomHooker(callback, null))
    }

    @JvmStatic
    fun hookAfter(executable: Executable, callback: AfterCallback): XposedInterface.HookHandle {
        return xposedModule!!
            .hook(executable)
            .intercept(CustomHooker(null, callback))
    }

    @JvmStatic
    fun hook(executable: Executable, callback: Callback): XposedInterface.HookHandle {
        return xposedModule!!
            .hook(executable)
            .intercept(CustomHooker(callback))
    }
}
