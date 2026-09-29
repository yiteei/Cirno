package nep.timeline.cirno.reflect

import android.content.res.Resources
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import io.github.libxposed.api.XposedInterface
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.log.XposedLog
import org.apache.commons.lang3.ClassUtils
import org.apache.commons.lang3.reflect.MemberUtilsX
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.LinkedList
import java.util.Objects
import java.util.Optional
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helpers that simplify hooking and calling methods/constructors, getting and settings fields, ...
 */
object CakeReflection {
    private val fieldCache: ConcurrentHashMap<MemberCacheKey.Field, Optional<Field>> = ConcurrentHashMap()
    private val methodCache: ConcurrentHashMap<MemberCacheKey.Method, Optional<Method>> = ConcurrentHashMap()
    private val constructorCache: ConcurrentHashMap<MemberCacheKey.Constructor, Optional<Constructor<*>>> = ConcurrentHashMap()
    private val additionalFields: WeakHashMap<Any, HashMap<String, Any?>> = WeakHashMap()
    private val sMethodDepth: HashMap<String, ThreadLocal<AtomicInteger>> = HashMap()

    /**
     * Note that we use object key instead of string here, because string calculation will lose all
     * the benefits of 'HashMap', this is basically the solution of performance traps.
     *
     * So in fact we only need to use the structural comparison results of the reflection object.
     */
    private abstract class MemberCacheKey(private val hash: Int) {
        abstract override fun equals(other: Any?): Boolean

        final override fun hashCode(): Int {
            return hash
        }

        class Constructor(val clazz: Class<*>, val parameters: Array<Class<*>?>, val isExact: Boolean) :
            MemberCacheKey(31 * Objects.hash(clazz, isExact) + Arrays.hashCode(parameters)) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Constructor) return false
                return isExact == other.isExact && Objects.equals(clazz, other.clazz) && parameters.contentEquals(other.parameters)
            }

            @NonNull
            override fun toString(): String {
                val str = clazz.name + getParametersString(*parameters)
                return if (isExact) {
                    "$str#exact"
                } else {
                    str
                }
            }
        }

        class Field(val clazz: Class<*>, val name: String) : MemberCacheKey(Objects.hash(clazz, name)) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Field) return false
                return Objects.equals(clazz, other.clazz) && Objects.equals(name, other.name)
            }

            @NonNull
            override fun toString(): String {
                return clazz.name + "#" + name
            }
        }

        class Method(val clazz: Class<*>, val name: String, val parameters: Array<Class<*>?>, val isExact: Boolean) :
            MemberCacheKey(31 * Objects.hash(clazz, name, isExact) + Arrays.hashCode(parameters)) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Method) return false
                return isExact == other.isExact && Objects.equals(clazz, other.clazz) && Objects.equals(name, other.name) && parameters.contentEquals(other.parameters)
            }

            @NonNull
            override fun toString(): String {
                val str = clazz.name + '#' + name + getParametersString(*parameters)
                return if (isExact) {
                    "$str#exact"
                } else {
                    str
                }
            }
        }
    }

    /**
     * Look up a class with the specified class loader.
     *
     * @param className   The class name.
     * @param classLoader The class loader, or `null` for the boot class loader.
     * @return A reference to the class.
     * @throws ClassNotFoundError In case the class was not found.
     */
    @JvmStatic
    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        var loader = classLoader
        if (loader == null)
            loader = GlobalVars.classLoader
        try {
            return ClassUtils.getClass(loader, className, false)
        } catch (e: ClassNotFoundException) {
            throw ClassNotFoundError(e)
        }
    }

    /**
     * Look up and return a class if it exists.
     * Like [findClass], but doesn't throw an exception if the class doesn't exist.
     */
    @JvmStatic
    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        try {
            return findClass(className, classLoader)
        } catch (e: ClassNotFoundError) {
            return null
        }
    }

    /**
     * Look up a field in a class and set it to accessible.
     *
     * @throws NoSuchFieldError In case the field was not found.
     */
    @JvmStatic
    fun findField(clazz: Class<*>, fieldName: String): Field {
        val key = MemberCacheKey.Field(clazz, fieldName)

        return fieldCache.computeIfAbsent(key) { k ->
            try {
                val newField = findFieldRecursiveImpl(k.clazz, k.name)
                newField.isAccessible = true
                Optional.of(newField)
            } catch (e: NoSuchFieldException) {
                Optional.empty()
            }
        }.orElseThrow { NoSuchFieldError(key.toString()) }
    }

    /**
     * Look up and return a field if it exists.
     * Like [findField], but doesn't throw an exception if the field doesn't exist.
     */
    @JvmStatic
    fun findFieldIfExists(clazz: Class<*>, fieldName: String): Field? {
        try {
            return findField(clazz, fieldName)
        } catch (e: NoSuchFieldError) {
            return null
        }
    }

    /**
     * Returns the first field of the given type in a class.
     * Might be useful for Proguard'ed classes to identify fields with unique types.
     *
     * @throws NoSuchFieldError In case no matching field was not found.
     */
    @JvmStatic
    fun findFirstFieldByExactType(clazz: Class<*>, type: Class<*>): Field {
        var clz: Class<*>? = clazz
        do {
            for (field in clz!!.declaredFields) {
                if (field.type == type) {
                    field.isAccessible = true
                    return field
                }
            }
            clz = clz.superclass
        } while (clz != null)

        throw NoSuchFieldError("Field of type " + type.name + " in class " + clazz.name)
    }

    /**
     * Look up a method and hook it. The last argument must be the callback for the hook.
     *
     * @throws NoSuchMethodError  In case the method was not found.
     * @throws ClassNotFoundError In case the target class or one of the parameter types couldn't be resolved.
     */
    @JvmStatic
    fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): XposedInterface.HookHandle {
        if (parameterTypesAndCallback.isEmpty() || parameterTypesAndCallback[parameterTypesAndCallback.size - 1] !is CakeHooker.Callback)
            throw IllegalArgumentException("no callback defined")

        val callback = parameterTypesAndCallback[parameterTypesAndCallback.size - 1] as CakeHooker.Callback

        val m = findMethodExact(clazz, methodName, *getParameterClasses(clazz.classLoader, parameterTypesAndCallback))

        return CakeHooker.hook(m, callback)
    }

    @JvmStatic
    fun findAndHookMethod(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback: Any?): XposedInterface.HookHandle {
        return findAndHookMethod(findClass(className, classLoader), methodName, *parameterTypesAndCallback)
    }

    /**
     * Look up a method in a class and set it to accessible.
     */
    @JvmStatic
    fun findMethodExact(clazz: Class<*>, methodName: String, vararg parameterTypes: Any?): Method {
        return findMethodExact(clazz, methodName, *getParameterClasses(clazz.classLoader, parameterTypes))
    }

    /**
     * Look up and return a method if it exists.
     */
    @JvmStatic
    fun findMethodExactIfExists(clazz: Class<*>, methodName: String, vararg parameterTypes: Any?): Method? {
        try {
            return findMethodExact(clazz, methodName, *parameterTypes)
        } catch (e: ClassNotFoundError) {
            return null
        } catch (e: NoSuchMethodError) {
            return null
        }
    }

    /**
     * Look up a method in a class and set it to accessible.
     * The method must be declared or overridden in the given class.
     *
     * @throws NoSuchMethodError  In case the method was not found.
     * @throws ClassNotFoundError In case the target class or one of the parameter types couldn't be resolved.
     */
    @JvmStatic
    fun findMethodExact(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypes: Any?): Method {
        return findMethodExact(findClass(className, classLoader), methodName, *getParameterClasses(classLoader, parameterTypes))
    }

    /**
     * Look up and return a method if it exists.
     * Like [findMethodExact], but doesn't throw an exception if the method doesn't exist.
     */
    @JvmStatic
    fun findMethodExactIfExists(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypes: Any?): Method? {
        try {
            return findMethodExact(className, classLoader, methodName, *parameterTypes)
        } catch (e: ClassNotFoundError) {
            return null
        } catch (e: NoSuchMethodError) {
            return null
        }
    }

    /**
     * Look up a method in a class and set it to accessible.
     *
     * This variant requires that you already have reference to all the parameter types.
     */
    @JvmStatic
    fun findMethodExact(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>?): Method {
        val key = MemberCacheKey.Method(clazz, methodName, arrayOf(*parameterTypes), true)

        return methodCache.computeIfAbsent(key) { k ->
            try {
                val method = k.clazz.getDeclaredMethod(k.name, *k.parameters)
                method.isAccessible = true
                Optional.of(method)
            } catch (e: NoSuchMethodException) {
                Optional.empty()
            }
        }.orElseThrow { NoSuchMethodError(key.toString()) }
    }

    /**
     * Returns an array of all methods declared/overridden in a class with the specified parameter types.
     *
     * The return type is optional, it will not be compared if it is `null`.
     * Use `void.class` if you want to search for methods returning nothing.
     */
    @JvmStatic
    fun findMethodsByExactParameters(clazz: Class<*>, returnType: Class<*>?, vararg parameterTypes: Class<*>?): Array<Method> {
        val result: MutableList<Method> = LinkedList()
        for (method in clazz.declaredMethods) {
            if (returnType != null && returnType != method.returnType)
                continue

            val methodParameterTypes = method.parameterTypes
            if (parameterTypes.size != methodParameterTypes.size)
                continue

            var match = true
            for (i in parameterTypes.indices) {
                if (parameterTypes[i] !== methodParameterTypes[i]) {
                    match = false
                    break
                }
            }

            if (!match)
                continue

            method.isAccessible = true
            result.add(method)
        }
        return result.toTypedArray()
    }

    /**
     * Look up a method in a class and set it to accessible.
     *
     * This does'nt only look for exact matches, but for the best match. All considered candidates
     * must be compatible with the given parameter types, i.e. the parameters must be assignable
     * to the method's formal parameters. Inherited methods are considered here.
     *
     * @throws NoSuchMethodError In case no suitable method was found.
     */
    @JvmStatic
    fun findMethodBestMatch(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>?): Method {
        // find the exact matching method first
        try {
            return findMethodExact(clazz, methodName, *parameterTypes)
        } catch (ignored: NoSuchMethodError) {
        }

        // then find the best match
        val key = MemberCacheKey.Method(clazz, methodName, arrayOf(*parameterTypes), false)

        return methodCache.computeIfAbsent(key) { k ->
            var bestMatch: Method? = null
            var clz: Class<*>? = k.clazz
            var considerPrivateMethods = true
            do {
                for (method in clz!!.declaredMethods) {
                    // don't consider private methods of superclasses
                    if (!considerPrivateMethods && Modifier.isPrivate(method.modifiers))
                        continue

                    // compare name and parameters
                    if (method.name == k.name && ClassUtils.isAssignable(
                            k.parameters,
                            method.parameterTypes,
                            true
                        )
                    ) {
                        // get accessible version of method
                        if (bestMatch == null || MemberUtilsX.compareMethodFit(
                                method,
                                bestMatch,
                                k.parameters
                            ) < 0
                        ) {
                            bestMatch = method
                        }
                    }
                }
                considerPrivateMethods = false
                clz = clz.superclass
            } while (clz != null)

            if (bestMatch != null) {
                bestMatch.isAccessible = true
                Optional.of(bestMatch)
            } else {
                Optional.empty()
            }
        }.orElseThrow { NoSuchMethodError(key.toString()) }
    }

    /**
     * Look up a method in a class and set it to accessible.
     *
     * See [findMethodBestMatch] for details. This variant
     * determines the parameter types from the classes of the given objects.
     */
    @JvmStatic
    fun findMethodBestMatch(clazz: Class<*>, methodName: String, vararg args: Any?): Method {
        return findMethodBestMatch(clazz, methodName, *getParameterTypes(*args))
    }

    /**
     * Look up a method in a class and set it to accessible.
     *
     * See [findMethodBestMatch] for details. This variant
     * determines the parameter types from the classes of the given objects. For any item that is
     * `null`, the type is taken from `parameterTypes` instead.
     */
    @JvmStatic
    fun findMethodBestMatch(clazz: Class<*>, methodName: String, parameterTypes: Array<Class<*>?>, args: Array<Any?>): Method {
        var argsClasses: Array<Class<*>?>? = null
        for (i in parameterTypes.indices) {
            if (parameterTypes[i] != null)
                continue
            if (argsClasses == null)
                argsClasses = getParameterTypes(*args)
            parameterTypes[i] = argsClasses[i]
        }
        return findMethodBestMatch(clazz, methodName, *parameterTypes)
    }

    /**
     * Returns an array with the classes of the given objects.
     */
    @JvmStatic
    fun getParameterTypes(vararg args: Any?): Array<Class<*>?> {
        val clazzes = arrayOfNulls<Class<*>>(args.size)
        for (i in args.indices) {
            clazzes[i] = if (args[i] != null) args[i]!!.javaClass else null
        }
        return clazzes
    }

    /**
     * Returns an array of the given classes.
     */
    @JvmStatic
    fun getClassesAsArray(vararg clazzes: Class<*>?): Array<out Class<*>?> {
        return clazzes
    }

    /**
     * Look up a constructor of a class and set it to accessible.
     */
    @JvmStatic
    fun findConstructorExact(clazz: Class<*>, vararg parameterTypes: Any?): Constructor<*> {
        return findConstructorExact(clazz, *getParameterClasses(clazz.classLoader, parameterTypes))
    }

    /**
     * Look up and return a constructor if it exists.
     */
    @JvmStatic
    fun findConstructorExactIfExists(clazz: Class<*>, vararg parameterTypes: Any?): Constructor<*>? {
        try {
            return findConstructorExact(clazz, *parameterTypes)
        } catch (e: ClassNotFoundError) {
            return null
        } catch (e: NoSuchMethodError) {
            return null
        }
    }

    /**
     * Look up a constructor of a class and set it to accessible.
     */
    @JvmStatic
    fun findConstructorExact(className: String, classLoader: ClassLoader?, vararg parameterTypes: Any?): Constructor<*> {
        return findConstructorExact(findClass(className, classLoader), *getParameterClasses(classLoader, parameterTypes))
    }

    /**
     * Look up and return a constructor if it exists.
     */
    @JvmStatic
    fun findConstructorExactIfExists(className: String, classLoader: ClassLoader?, vararg parameterTypes: Any?): Constructor<*>? {
        try {
            return findConstructorExact(className, classLoader, *parameterTypes)
        } catch (e: ClassNotFoundError) {
            return null
        } catch (e: NoSuchMethodError) {
            return null
        }
    }

    /**
     * Look up a constructor of a class and set it to accessible.
     */
    @JvmStatic
    fun findConstructorExact(clazz: Class<*>, vararg parameterTypes: Class<*>?): Constructor<*> {
        val key = MemberCacheKey.Constructor(clazz, arrayOf(*parameterTypes), true)

        return constructorCache.computeIfAbsent(key) { k ->
            try {
                val constructor = k.clazz.getDeclaredConstructor(*k.parameters)
                constructor.isAccessible = true
                Optional.of(constructor)
            } catch (e: NoSuchMethodException) {
                Optional.empty()
            }
        }.orElseThrow { NoSuchMethodError(key.toString()) }
    }

    /**
     * Look up a constructor and hook it.
     */
    @JvmStatic
    fun findAndHookConstructor(clazz: Class<*>, vararg parameterTypesAndCallback: Any?): XposedInterface.HookHandle {
        if (parameterTypesAndCallback.isEmpty() || parameterTypesAndCallback[parameterTypesAndCallback.size - 1] !is CakeHooker.Callback)
            throw IllegalArgumentException("no callback defined")

        val callback = parameterTypesAndCallback[parameterTypesAndCallback.size - 1] as CakeHooker.Callback

        val m = findConstructorExact(clazz, *getParameterClasses(clazz.classLoader, parameterTypesAndCallback))

        return CakeHooker.hook(m, callback)
    }

    /**
     * Look up a constructor and hook it.
     */
    @JvmStatic
    fun findAndHookConstructor(className: String, classLoader: ClassLoader?, vararg parameterTypesAndCallback: Any?): XposedInterface.HookHandle {
        return findAndHookConstructor(findClass(className, classLoader), *parameterTypesAndCallback)
    }

    /**
     * Look up a constructor in a class and set it to accessible.
     *
     * See [findMethodBestMatch] for details.
     */
    @JvmStatic
    fun findConstructorBestMatch(clazz: Class<*>, vararg parameterTypes: Class<*>?): Constructor<*> {
        // find the exact matching constructor first
        try {
            return findConstructorExact(clazz, *parameterTypes)
        } catch (ignored: NoSuchMethodError) {
        }

        // then find the best match
        val key = MemberCacheKey.Constructor(clazz, arrayOf(*parameterTypes), false)

        return constructorCache.computeIfAbsent(key) { k ->
            var bestMatch: Constructor<*>? = null
            val constructors = k.clazz.declaredConstructors
            for (constructor in constructors) {
                // compare name and parameters
                if (ClassUtils.isAssignable(
                        k.parameters,
                        constructor.parameterTypes,
                        true
                    )
                ) {
                    // get accessible version of method
                    if (bestMatch == null || MemberUtilsX.compareConstructorFit(
                            constructor,
                            bestMatch,
                            k.parameters
                        ) < 0
                    ) {
                        bestMatch = constructor
                    }
                }
            }

            if (bestMatch != null) {
                bestMatch.isAccessible = true
                Optional.of(bestMatch)
            } else {
                Optional.empty()
            }
        }.orElseThrow { NoSuchMethodError(key.toString()) }
    }

    /**
     * Look up a constructor in a class and set it to accessible.
     *
     * See [findMethodBestMatch] for details. This variant
     * determines the parameter types from the classes of the given objects.
     */
    @JvmStatic
    fun findConstructorBestMatch(clazz: Class<*>, vararg args: Any?): Constructor<*> {
        return findConstructorBestMatch(clazz, *getParameterTypes(*args))
    }

    /**
     * Look up a constructor in a class and set it to accessible.
     *
     * See [findMethodBestMatch] for details. This variant
     * determines the parameter types from the classes of the given objects. For any item that is
     * `null`, the type is taken from `parameterTypes` instead.
     */
    @JvmStatic
    fun findConstructorBestMatch(clazz: Class<*>, parameterTypes: Array<Class<*>?>, args: Array<Any?>): Constructor<*> {
        var argsClasses: Array<Class<*>?>? = null
        for (i in parameterTypes.indices) {
            if (parameterTypes[i] != null)
                continue
            if (argsClasses == null)
                argsClasses = getParameterTypes(*args)
            parameterTypes[i] = argsClasses[i]
        }
        return findConstructorBestMatch(clazz, *parameterTypes)
    }

    /**
     * Thrown when a class loader is unable to find a class. Unlike [ClassNotFoundException],
     * callers are not forced to explicitly catch this. If uncaught, the error will be passed to the
     * next caller in the stack.
     */
    class ClassNotFoundError : Error {
        constructor(cause: Throwable?) : super(cause)

        constructor(detailMessage: String?, cause: Throwable?) : super(detailMessage, cause)

        companion object {
            private const val serialVersionUID: Long = -1070936889459514628L
        }
    }

    /**
     * Returns the index of the first parameter declared with the given type.
     *
     * @throws NoSuchFieldError if there is no parameter with that type.
     */
    @JvmStatic
    fun getFirstParameterIndexByType(method: Member, type: Class<*>): Int {
        val classes = if (method is Method) method.parameterTypes else (method as Constructor<*>).parameterTypes
        for (i in classes.indices) {
            if (classes[i] == type) {
                return i
            }
        }
        throw NoSuchFieldError("No parameter of type " + type + " found in " + method)
    }

    /**
     * Returns the index of the parameter declared with the given type, ensuring that there is exactly one such parameter.
     *
     * @throws NoSuchFieldError if there is no or more than one parameter with that type.
     */
    @JvmStatic
    fun getParameterIndexByType(method: Member, type: Class<*>): Int {
        val classes = if (method is Method) method.parameterTypes else (method as Constructor<*>).parameterTypes
        var idx = -1
        for (i in classes.indices) {
            if (classes[i] == type) {
                if (idx == -1) {
                    idx = i
                } else {
                    throw NoSuchFieldError("More than one parameter of type " + type + " found in " + method)
                }
            }
        }
        if (idx != -1) {
            return idx
        } else {
            throw NoSuchFieldError("No parameter of type " + type + " found in " + method)
        }
    }

    //#################################################################################################

    /**
     * Sets the value of an object field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setObjectField(obj: Any?, fieldName: String, value: Any?) {
        try {
            findField(obj!!.javaClass, fieldName).set(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a `boolean` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setBooleanField(obj: Any?, fieldName: String, value: Boolean) {
        try {
            findField(obj!!.javaClass, fieldName).setBoolean(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a `byte` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setByteField(obj: Any?, fieldName: String, value: Byte) {
        try {
            findField(obj!!.javaClass, fieldName).setByte(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a `char` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setCharField(obj: Any?, fieldName: String, value: Char) {
        try {
            findField(obj!!.javaClass, fieldName).setChar(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a `double` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setDoubleField(obj: Any?, fieldName: String, value: Double) {
        try {
            findField(obj!!.javaClass, fieldName).setDouble(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a `float` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setFloatField(obj: Any?, fieldName: String, value: Float) {
        try {
            findField(obj!!.javaClass, fieldName).setFloat(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of an `int` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setIntField(obj: Any?, fieldName: String, value: Int) {
        try {
            findField(obj!!.javaClass, fieldName).setInt(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a `long` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setLongField(obj: Any?, fieldName: String, value: Long) {
        try {
            findField(obj!!.javaClass, fieldName).setLong(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a `short` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun setShortField(obj: Any?, fieldName: String, value: Short) {
        try {
            findField(obj!!.javaClass, fieldName).setShort(obj, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    //#################################################################################################

    /**
     * Returns the value of an object field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getObjectField(obj: Any?, fieldName: String): Any? {
        try {
            return findField(obj!!.javaClass, fieldName).get(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * For inner classes, returns the surrounding instance, i.e. the `this` reference of the surrounding class.
     */
    @JvmStatic
    fun getSurroundingThis(obj: Any?): Any? {
        return getObjectField(obj, "this$0")
    }

    /**
     * Returns the value of a `boolean` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getBooleanField(obj: Any?, fieldName: String): Boolean {
        try {
            return findField(obj!!.javaClass, fieldName).getBoolean(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a `byte` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getByteField(obj: Any?, fieldName: String): Byte {
        try {
            return findField(obj!!.javaClass, fieldName).getByte(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a `char` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getCharField(obj: Any?, fieldName: String): Char {
        try {
            return findField(obj!!.javaClass, fieldName).getChar(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a `double` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getDoubleField(obj: Any?, fieldName: String): Double {
        try {
            return findField(obj!!.javaClass, fieldName).getDouble(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a `float` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getFloatField(obj: Any?, fieldName: String): Float {
        try {
            return findField(obj!!.javaClass, fieldName).getFloat(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of an `int` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getIntField(obj: Any?, fieldName: String): Int {
        try {
            return findField(obj!!.javaClass, fieldName).getInt(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a `long` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getLongField(obj: Any?, fieldName: String): Long {
        try {
            return findField(obj!!.javaClass, fieldName).getLong(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a `short` field in the given object instance. A class reference is not sufficient! See also [findField].
     */
    @JvmStatic
    fun getShortField(obj: Any?, fieldName: String): Short {
        try {
            return findField(obj!!.javaClass, fieldName).getShort(obj)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    //#################################################################################################

    /**
     * Sets the value of a static object field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticObjectField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            findField(clazz, fieldName).set(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a static `boolean` field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticBooleanField(clazz: Class<*>, fieldName: String, value: Boolean) {
        try {
            findField(clazz, fieldName).setBoolean(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a static `byte` field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticByteField(clazz: Class<*>, fieldName: String, value: Byte) {
        try {
            findField(clazz, fieldName).setByte(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a static `char` field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticCharField(clazz: Class<*>, fieldName: String, value: Char) {
        try {
            findField(clazz, fieldName).setChar(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a static `double` field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticDoubleField(clazz: Class<*>, fieldName: String, value: Double) {
        try {
            findField(clazz, fieldName).setDouble(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a static `float` field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticFloatField(clazz: Class<*>, fieldName: String, value: Float) {
        try {
            findField(clazz, fieldName).setFloat(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a static `int` field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticIntField(clazz: Class<*>, fieldName: String, value: Int) {
        try {
            findField(clazz, fieldName).setInt(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a static `long` field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticLongField(clazz: Class<*>, fieldName: String, value: Long) {
        try {
            findField(clazz, fieldName).setLong(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Sets the value of a static `short` field in the given class. See also [findField].
     */
    @JvmStatic
    fun setStaticShortField(clazz: Class<*>, fieldName: String, value: Short) {
        try {
            findField(clazz, fieldName).setShort(null, value)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    //#################################################################################################

    /**
     * Returns the value of a static object field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? {
        try {
            return findField(clazz, fieldName).get(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a static `boolean` field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticBooleanField(clazz: Class<*>, fieldName: String): Boolean {
        try {
            return findField(clazz, fieldName).getBoolean(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a static `byte` field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticByteField(clazz: Class<*>, fieldName: String): Byte {
        try {
            return findField(clazz, fieldName).getByte(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a static `char` field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticCharField(clazz: Class<*>, fieldName: String): Char {
        try {
            return findField(clazz, fieldName).getChar(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a static `double` field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticDoubleField(clazz: Class<*>, fieldName: String): Double {
        try {
            return findField(clazz, fieldName).getDouble(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a static `float` field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticFloatField(clazz: Class<*>, fieldName: String): Float {
        try {
            return findField(clazz, fieldName).getFloat(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a static `int` field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticIntField(clazz: Class<*>, fieldName: String): Int {
        try {
            return findField(clazz, fieldName).getInt(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a static `long` field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticLongField(clazz: Class<*>, fieldName: String): Long {
        try {
            return findField(clazz, fieldName).getLong(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    /**
     * Returns the value of a static `short` field in the given class. See also [findField].
     */
    @JvmStatic
    fun getStaticShortField(clazz: Class<*>, fieldName: String): Short {
        try {
            return findField(clazz, fieldName).getShort(null)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        }
    }

    //#################################################################################################

    /**
     * Calls an instance or static method of the given object.
     * The method is resolved using [findMethodBestMatch].
     *
     * @throws NoSuchMethodError     In case no suitable method was found.
     * @throws InvocationTargetError In case an exception was thrown by the invoked method.
     */
    @JvmStatic
    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        try {
            return findMethodBestMatch(obj.javaClass, methodName, *args).invoke(obj, *args)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        } catch (e: InvocationTargetException) {
            throw InvocationTargetError(e.cause)
        }
    }

    /**
     * Calls an instance or static method of the given object.
     * See [callMethod].
     *
     * This variant allows you to specify parameter types, which can help in case there are multiple
     * methods with the same name, especially if you call it with `null` parameters.
     */
    @JvmStatic
    fun callMethod(obj: Any, methodName: String, parameterTypes: Array<Class<*>?>, vararg args: Any?): Any? {
        try {
            return findMethodBestMatch(obj.javaClass, methodName, parameterTypes, args).invoke(obj, *args)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        } catch (e: InvocationTargetException) {
            throw InvocationTargetError(e.cause)
        }
    }

    /**
     * Calls a static method of the given class.
     * The method is resolved using [findMethodBestMatch].
     *
     * @throws NoSuchMethodError     In case no suitable method was found.
     * @throws InvocationTargetError In case an exception was thrown by the invoked method.
     */
    @JvmStatic
    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        try {
            return findMethodBestMatch(clazz, methodName, *args).invoke(null, *args)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        } catch (e: InvocationTargetException) {
            throw InvocationTargetError(e.cause)
        }
    }

    /**
     * Calls a static method of the given class.
     * See [callStaticMethod].
     *
     * This variant allows you to specify parameter types, which can help in case there are multiple
     * methods with the same name, especially if you call it with `null` parameters.
     */
    @JvmStatic
    fun callStaticMethod(clazz: Class<*>, methodName: String, parameterTypes: Array<Class<*>?>, vararg args: Any?): Any? {
        try {
            return findMethodBestMatch(clazz, methodName, parameterTypes, args).invoke(null, *args)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        } catch (e: InvocationTargetException) {
            throw InvocationTargetError(e.cause)
        }
    }

    /**
     * This class provides a wrapper for an exception thrown by a method invocation.
     *
     * @see callMethod
     * @see callStaticMethod
     * @see newInstance
     */
    class InvocationTargetError : Error {
        constructor(cause: Throwable?) : super(cause)

        companion object {
            private const val serialVersionUID: Long = -1070936889459514628L
        }
    }

    //#################################################################################################

    /**
     * Creates a new instance of the given class.
     * The constructor is resolved using [findConstructorBestMatch].
     *
     * @throws NoSuchMethodError     In case no suitable constructor was found.
     * @throws InvocationTargetError In case an exception was thrown by the invoked method.
     * @throws InstantiationError    In case the class cannot be instantiated.
     */
    @JvmStatic
    fun newInstance(clazz: Class<*>, vararg args: Any?): Any? {
        try {
            return findConstructorBestMatch(clazz, *args).newInstance(*args)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        } catch (e: InvocationTargetException) {
            throw InvocationTargetError(e.cause)
        } catch (e: InstantiationException) {
            throw InstantiationError(e.message)
        }
    }

    /**
     * Creates a new instance of the given class.
     * See [newInstance].
     *
     * This variant allows you to specify parameter types, which can help in case there are multiple
     * constructors with the same name, especially if you call it with `null` parameters.
     */
    @JvmStatic
    fun newInstance(clazz: Class<*>, parameterTypes: Array<Class<*>?>, vararg args: Any?): Any? {
        try {
            return findConstructorBestMatch(clazz, parameterTypes, args).newInstance(*args)
        } catch (e: IllegalAccessException) {
            // should not happen
            XposedLog.e("Reflection", e)
            throw IllegalAccessError(e.message)
        } catch (e: InvocationTargetException) {
            throw InvocationTargetError(e.cause)
        } catch (e: InstantiationException) {
            throw InstantiationError(e.message)
        }
    }

    //#################################################################################################

    /**
     * Attaches any value to an object instance. This simulates adding an instance field.
     * The value can be retrieved again with [getAdditionalInstanceField].
     *
     * @return The previously stored value for this instance/key combination, or `null` if there was none.
     */
    @JvmStatic
    fun setAdditionalInstanceField(obj: Any?, key: String?, value: Any?): Any? {
        if (obj == null)
            throw NullPointerException("object must not be null")
        if (key == null)
            throw NullPointerException("key must not be null")

        var objectFields: HashMap<String, Any?>
        synchronized(additionalFields) {
            objectFields = additionalFields.computeIfAbsent(obj) { HashMap() }
        }

        synchronized(objectFields) {
            return objectFields.put(key, value)
        }
    }

    /**
     * Returns a value which was stored with [setAdditionalInstanceField].
     *
     * @return The stored value for this instance/key combination, or `null` if there is none.
     */
    @JvmStatic
    fun getAdditionalInstanceField(obj: Any?, key: String?): Any? {
        if (obj == null)
            throw NullPointerException("object must not be null")
        if (key == null)
            throw NullPointerException("key must not be null")

        var objectFields: HashMap<String, Any?>
        synchronized(additionalFields) {
            objectFields = additionalFields[obj] ?: return null
        }

        synchronized(objectFields) {
            return objectFields[key]
        }
    }

    /**
     * Removes and returns a value which was stored with [setAdditionalInstanceField].
     *
     * @return The previously stored value for this instance/key combination, or `null` if there was none.
     */
    @JvmStatic
    fun removeAdditionalInstanceField(obj: Any?, key: String?): Any? {
        if (obj == null)
            throw NullPointerException("object must not be null")
        if (key == null)
            throw NullPointerException("key must not be null")

        var objectFields: HashMap<String, Any?>
        synchronized(additionalFields) {
            objectFields = additionalFields[obj] ?: return null
        }

        synchronized(objectFields) {
            return objectFields.remove(key)
        }
    }

    /**
     * Like [setAdditionalInstanceField], but the value is stored for the class of `obj`.
     */
    @JvmStatic
    fun setAdditionalStaticField(obj: Any?, key: String?, value: Any?): Any? {
        return setAdditionalInstanceField(obj!!.javaClass, key, value)
    }

    /**
     * Like [getAdditionalInstanceField], but the value is returned for the class of `obj`.
     */
    @JvmStatic
    fun getAdditionalStaticField(obj: Any?, key: String?): Any? {
        return getAdditionalInstanceField(obj!!.javaClass, key)
    }

    /**
     * Like [removeAdditionalInstanceField], but the value is removed and returned for the class of `obj`.
     */
    @JvmStatic
    fun removeAdditionalStaticField(obj: Any?, key: String?): Any? {
        return removeAdditionalInstanceField(obj!!.javaClass, key)
    }

    /**
     * Like [setAdditionalInstanceField], but the value is stored for `clazz`.
     */
    @JvmStatic
    fun setAdditionalStaticField(clazz: Class<*>, key: String?, value: Any?): Any? {
        return setAdditionalInstanceField(clazz, key, value)
    }

    /**
     * Like [setAdditionalInstanceField], but the value is returned for `clazz`.
     */
    @JvmStatic
    fun getAdditionalStaticField(clazz: Class<*>, key: String?): Any? {
        return getAdditionalInstanceField(clazz, key)
    }

    /**
     * Like [removeAdditionalInstanceField], but the value is removed and returned for `clazz`.
     */
    @JvmStatic
    fun removeAdditionalStaticField(clazz: Class<*>, key: String?): Any? {
        return removeAdditionalInstanceField(clazz, key)
    }

    //#################################################################################################

    /**
     * Loads an asset from a resource object and returns the content as `byte` array.
     *
     * @return The content of the asset.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun assetAsByteArray(res: Resources, path: String): ByteArray {
        return inputStreamToByteArray(res.assets.open(path))
    }

    @Throws(IOException::class)
    internal fun inputStreamToByteArray(`is`: InputStream): ByteArray {
        val buf = ByteArrayOutputStream()
        val temp = ByteArray(1024)
        var read: Int

        while (`is`.read(temp).also { read = it } > 0) {
            buf.write(temp, 0, read)
        }
        `is`.close()
        return buf.toByteArray()
    }

    /**
     * Returns the lowercase hex string representation of a file's MD5 hash sum.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getMD5Sum(file: String): String {
        try {
            val digest = MessageDigest.getInstance("MD5")
            val `is` = FileInputStream(file)
            val buffer = ByteArray(8192)
            var read: Int
            while (`is`.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
            `is`.close()
            val md5sum = digest.digest()
            val bigInt = BigInteger(1, md5sum)
            return bigInt.toString(16)
        } catch (e: NoSuchAlgorithmException) {
            return ""
        }
    }

    //#################################################################################################

    /**
     * Increments the depth counter for the given method.
     *
     * @param method The method name. Should be prefixed with a unique, module-specific string.
     * @return The updated depth.
     */
    @JvmStatic
    fun incrementMethodDepth(method: String): Int {
        return getMethodDepthCounter(method).get().incrementAndGet()
    }

    /**
     * Decrements the depth counter for the given method.
     * See [incrementMethodDepth] for details.
     *
     * @param method The method name. Should be prefixed with a unique, module-specific string.
     * @return The updated depth.
     */
    @JvmStatic
    fun decrementMethodDepth(method: String): Int {
        return getMethodDepthCounter(method).get().decrementAndGet()
    }

    /**
     * Returns the current depth counter for the given method.
     * See [incrementMethodDepth] for details.
     *
     * @param method The method name. Should be prefixed with a unique, module-specific string.
     * @return The updated depth.
     */
    @JvmStatic
    fun getMethodDepth(method: String): Int {
        return getMethodDepthCounter(method).get().get()
    }

    private fun getMethodDepthCounter(method: String): ThreadLocal<AtomicInteger> {
        synchronized(sMethodDepth) {
            var counter = sMethodDepth[method]
            if (counter == null) {
                counter = ThreadLocal.withInitial { AtomicInteger() }
                sMethodDepth[method] = counter
            }
            return counter
        }
    }

    @JvmStatic
    fun findParameterTypesFromClass(classLoader: ClassLoader?, clazzName: String, methodName: String): Array<Class<*>?>? {
        for (method in findClassIfExists(clazzName, classLoader)!!.declaredMethods)
            if (method.name == methodName)
                return method.parameterTypes
        return null
    }

    @JvmStatic
    fun findMethodFromClass(classLoader: ClassLoader?, clazzName: String, methodName: String): Method? {
        return findMethodFromClass(findClassIfExists(clazzName, classLoader), methodName)
    }

    @JvmStatic
    fun findMethodFromClass(clazz: Class<*>?, methodName: String): Method? {
        if (clazz == null)
            return null
        for (method in clazz.declaredMethods)
            if (method.name == methodName)
                return method
        return null
    }

    @JvmStatic
    fun findParameterTypesOrDefault(clazz: Class<*>?, methodName: String, vararg parameter: Any?): Array<out Any?> {
        try {
            for (method in clazz!!.declaredMethods) {
                if (method.name == methodName) {
                    val parameterTypes = method.parameterTypes
                    if (parameter.size <= parameterTypes.size) {
                        var isDone = true
                        for (i in parameter.indices) {
                            val obj = parameter[i]
                            if (!Objects.equals(obj, if (obj is String) parameterTypes[i].name else parameterTypes[i])) {
                                isDone = false
                            }
                        }
                        if (isDone)
                            return parameterTypes
                    }
                }
            }
        } catch (ignored: Throwable) {
        }
        return parameter
    }

    @JvmStatic
    fun findParameterTypesOrDefaultReturnClass(clazz: Class<*>?, methodName: String, vararg parameter: Any?): Array<Class<*>> {
        try {
            for (method in clazz!!.declaredMethods) {
                if (method.name == methodName) {
                    val parameterTypes = method.parameterTypes
                    if (parameter.size <= parameterTypes.size) {
                        var isDone = true
                        for (i in parameter.indices) {
                            val obj = parameter[i]
                            if (!Objects.equals(obj, if (obj is String) parameterTypes[i].name else parameterTypes[i])) {
                                isDone = false
                            }
                        }
                        if (isDone)
                            return parameterTypes
                    }
                }
            }
        } catch (ignored: Throwable) {
        }

        val classes: MutableList<Class<*>> = ArrayList()
        for (originalParam in parameter) {
            classes.add(originalParam!!.javaClass)
        }
        return classes.toTypedArray()
    }

    @JvmStatic
    fun findMethodBestMatchIfExists(clazz: Class<*>?, methodName: String, vararg parameterTypesAndCallback: Any?): Method? {
        if (clazz == null)
            return null

        try {
            return findMethodBestMatch(clazz, methodName, *parameterTypesAndCallback)
        } catch (ignored: Throwable) {
            return null
        }
    }

    @JvmStatic
    fun findConstructorParameterTypesOrDefault(clazz: Class<*>?, vararg parameter: Any?): Array<out Any?> {
        try {
            for (constructor in clazz!!.declaredConstructors) {
                val parameterTypes = constructor.parameterTypes
                if (parameter.size <= parameterTypes.size) {
                    var isDone = true
                    for (i in parameter.indices) {
                        val obj = parameter[i]
                        if (!Objects.equals(obj, if (obj is String) parameterTypes[i].name else parameterTypes[i])) {
                            isDone = false
                        }
                    }
                    if (isDone)
                        return parameterTypes
                }
            }
        } catch (ignored: Throwable) {
        }
        return parameter
    }
}

private fun findFieldRecursiveImpl(clazz: Class<*>, fieldName: String): Field {
    try {
        return clazz.getDeclaredField(fieldName)
    } catch (e: NoSuchFieldException) {
        var current: Class<*> = clazz
        while (true) {
            current = current.superclass ?: break
            if (current == Any::class.java)
                break

            try {
                return current.getDeclaredField(fieldName)
            } catch (ignored: NoSuchFieldException) {
            }
        }
        throw e
    }
}

/**
 * Retrieve classes from an array, where each element might either be a Class
 * already, or a String with the full class name.
 */
@Suppress("UNCHECKED_CAST")
private fun getParameterClasses(classLoader: ClassLoader?, parameterTypesAndCallback: Array<out Any?>): Array<Class<*>> {
    var parameterClasses: Array<Class<*>?>? = null
    for (i in parameterTypesAndCallback.indices.reversed()) {
        val type = parameterTypesAndCallback[i]
        if (type == null)
            throw CakeReflection.ClassNotFoundError("parameter type must not be null", null)

        // ignore trailing callback
        if (type is CakeHooker.Callback || type is CakeHooker.BeforeCallback || type is CakeHooker.AfterCallback || type is XposedInterface.Hooker)
            continue

        if (parameterClasses == null)
            parameterClasses = arrayOfNulls(i + 1)

        parameterClasses[i] = if (type is Class<*>)
            type
        else if (type is String)
            CakeReflection.findClass(type, classLoader)
        else
            throw CakeReflection.ClassNotFoundError("parameter type must either be specified as Class or String", null)
    }

    // if there are no arguments for the method
    if (parameterClasses == null)
        parameterClasses = arrayOfNulls(0)

    return parameterClasses as Array<Class<*>>
}

private fun getParametersString(vararg clazzes: Class<*>?): String {
    val sb = StringBuilder("(")
    var first = true
    for (clazz in clazzes) {
        if (first)
            first = false
        else
            sb.append(",")

        if (clazz != null)
            sb.append(clazz.canonicalName)
        else
            sb.append("null")
    }
    sb.append(")")
    return sb.toString()
}
