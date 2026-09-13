package github.zerorooot.nap511.util

import java.util.Locale

object DownloadUtil {
    /** Restrict the name to a single safe path component, preserving a short extension. */
    fun destinationName(name: String, uniqueId: String): String {
        val cleaned = name.map { char ->
            if (char.code < 32 || char.code == 127 || char in "\\/:*?\"<>|") '_' else char
        }.joinToString("").trim().trim('.').ifBlank { "download" }
        val extension = cleaned.substringAfterLast('.', "")
            .takeIf { it.length in 1..12 && it.all { c -> c.isLetterOrDigit() } }
        val stem = if (extension == null) cleaned else cleaned.dropLast(extension.length + 1)
        val safeId = uniqueId.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.take(32)
        require(safeId.isNotEmpty())
        // FAT/Android path component limit is in bytes, not UTF-16 characters.
        val prefix = buildString {
            var bytes = 0
            for (point in stem.codePoints().toArray()) {
                val next = String(Character.toChars(point))
                val count = next.toByteArray(Charsets.UTF_8).size
                if (bytes + count > 160) break
                append(next)
                bytes += count
            }
        }.ifBlank { "download" }
        return "$prefix-$safeId" + (extension?.let { ".$it" } ?: "")
    }

    fun progress(downloaded: Long, total: Long): Float? =
        if (total <= 0) null else (downloaded.coerceAtLeast(0).toDouble() / total).coerceIn(0.0, 1.0).toFloat()

    fun size(bytes: Long): String {
        if (bytes < 0) return "大小未知"
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB", "PB", "EB")
        var value = bytes.toDouble() / 1024
        var index = 0
        while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
        return String.format(Locale.ROOT, "%.1f %s", value, units[index])
    }
}
