package nep.timeline.cirno.utils

object StringUtils {
    @JvmStatic
    fun getSubString(text: String, left: String?, right: String): String {
        val result: String
        var zLen: Int
        if (left == null || left.isEmpty()) {
            zLen = 0
        } else {
            zLen = text.indexOf(left)
            zLen = if (zLen > -1)
                zLen + left.length
            else
                0
        }
        var yLen = text.indexOf(right, zLen)
        if (yLen < 0 || right.isEmpty())
            yLen = text.length
        result = text.substring(zLen, yLen)
        return result
    }

    @JvmStatic
    fun StringToInteger(str: String): Int {
        val data = str.trim()
        val result = StringBuilder()
        for (i in data.indices) {
            val c = data[i]
            if (Character.isDigit(c))
                result.append(c)
        }

        return result.toString().toInt()
    }
}
