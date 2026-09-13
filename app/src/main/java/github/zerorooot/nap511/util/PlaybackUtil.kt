package github.zerorooot.nap511.util

import github.zerorooot.nap511.bean.FileBean
import java.util.Locale

/** 不依赖播放器实例的播放列表、倍速与进度规则。 */
object PlaybackUtil {
    val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)
    val HOLD_SPEEDS = listOf(2f, 3f)
    val SEEK_STEPS = listOf(10L, 15L, 30L)
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts",
        "mts", "rm", "rmvb", "mpg", "mpeg", "vob", "asf", "3gp"
    )

    fun episodes(files: List<FileBean>): List<FileBean> = files.filter {
        it.fileId.isNotBlank() && it.pickCode.isNotBlank() &&
            (it.isVideo == 1 || it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in videoExtensions)
    }.distinctBy { it.pickCode }.sortedWith { a, b -> compareNames(a.name, b.name) }

    /** 数字按数值而不是字典序排序：E2 在 E10 前；长数字不转换成整数，避免溢出。 */
    fun compareNames(left: String, right: String): Int {
        val a = left.lowercase(Locale.ROOT)
        val b = right.lowercase(Locale.ROOT)
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i] in '0'..'9' && b[j] in '0'..'9') {
                val startI = i
                val startJ = j
                while (i < a.length && a[i] in '0'..'9') i++
                while (j < b.length && b[j] in '0'..'9') j++
                val numberA = a.substring(startI, i).trimStart('0').ifEmpty { "0" }
                val numberB = b.substring(startJ, j).trimStart('0').ifEmpty { "0" }
                val comparison = numberA.length.compareTo(numberB.length).takeIf { it != 0 }
                    ?: numberA.compareTo(numberB)
                if (comparison != 0) return comparison
            } else {
                val comparison = a[i].compareTo(b[j])
                if (comparison != 0) return comparison
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j).takeIf { it != 0 }
            ?: left.compareTo(right)
    }

    fun adjacentIndex(size: Int, current: Int, direction: Int): Int? {
        if (current !in 0 until size || direction !in listOf(-1, 1)) return null
        return (current + direction).takeIf { it in 0 until size }
    }

    @JvmStatic
    fun seekPosition(current: Long, delta: Long, duration: Long): Long {
        if (duration <= 0) return 0L
        val position = current.coerceIn(0L, duration)
        return when {
            delta > 0 && delta > duration - position -> duration
            delta < 0 && delta < -position -> 0L
            else -> position + delta
        }
    }

    fun resumePosition(positionMs: Long, durationMs: Long): Long =
        if (positionMs > 0 && durationMs > 0 && positionMs < durationMs - 10_000) positionMs else 0L

    @JvmStatic
    fun speedLabel(speed: Float): String =
        String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.') + "×"

    fun timerLabel(seconds: Long): String = String.format(
        Locale.ROOT, "%02d:%02d", seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60
    )
}
