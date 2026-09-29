package org.apache.commons.lang3.reflect

import java.lang.reflect.Constructor
import java.lang.reflect.Method

object MemberUtilsX {
    @JvmStatic
    fun compareConstructorFit(left: Constructor<*>, right: Constructor<*>, actual: Array<Class<*>?>): Int {
        return MemberUtils.compareConstructorFit(left, right, actual)
    }

    @JvmStatic
    fun compareMethodFit(left: Method, right: Method, actual: Array<Class<*>?>): Int {
        return MemberUtils.compareMethodFit(left, right, actual)
    }
}
