package nep.timeline.cirno.netlink

import nep.timeline.cirno.reflect.CakeReflection
import java.io.FileDescriptor

object IoUtils {
    @JvmStatic
    fun closeQuietly(classLoader: ClassLoader?, fileDescriptor: FileDescriptor?) {
        CakeReflection.callStaticMethod(CakeReflection.findClass("libcore.io.IoUtils", classLoader), "closeQuietly", fileDescriptor)
    }

    @JvmStatic
    fun setsockoptInt(classLoader: ClassLoader?, fileDescriptor: FileDescriptor?, level: Int, option: Int, value: Int) {
        val libcore = CakeReflection.findClass("libcore.io.Libcore", classLoader)
        val os = CakeReflection.getStaticObjectField(libcore, "os")
        CakeReflection.callMethod(os!!, "setsockoptInt", fileDescriptor, level, option, value)
    }
}
