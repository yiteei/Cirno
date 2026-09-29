package nep.timeline.cirno.utils

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import nep.timeline.cirno.log.Log
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

object RWUtils {
    @JvmStatic
    fun readConfig(file: SuFile): String? {
        try {
            return IOUtils.toString({ SuFileInputStream.open(file) }, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            Log.e("Read Config", e)
        }

        return null
    }

    @JvmStatic
    fun readConfig(name: String): String {
        try {
            return FileUtils.readLines(File(name), StandardCharsets.UTF_8).joinToString("\n")
        } catch (e: IOException) {
            Log.e("Read Config", e)
        }

        return ""
    }

    @JvmStatic
    @Throws(IOException::class)
    @JvmOverloads
    fun writeStringToFile(file: File, value: String, append: Boolean = false) {
        FileUtils.write(file, value + "\n", StandardCharsets.UTF_8, append)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeStringToFileSU(file: SuFile, value: String, append: Boolean) {
        PrintWriter(SuFileOutputStream.open(file), append).use { writer ->
            writer.write(value)
        }
    }

    @JvmStatic
    fun writeFrozen(path: String, value: Int): Boolean {
        try {
            PrintWriter(path).use { writer ->
                writer.write(value.toString())
                return true
            }
        } catch (ignored: FileNotFoundException) {
            Log.e(path + " | 文件不存在, 此进程可能已死亡, 或者你的设备不支持cgroup v2")
            return false
        }
    }
}
