package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * 为 GSY 准备 UTF-8 字幕：ASS/SSA 转成 SRT，SRT/WebVTT 保留各自的格式。
 * 不覆盖原文件，避免重编码或同名字幕转换影响正在播放的字幕。
 */
object SubtitleConvertUtil {
    private val defaultAssFields = listOf(
        "layer", "start", "end", "style", "name",
        "marginl", "marginr", "marginv", "effect", "text"
    )
    private val assEventsHeader = Regex("""(?im)^\s*\[events\]\s*$""")
    private val srtTiming = Regex("""(?m)^\s*\d+:\d{2}:\d{2}[,.]\d+\s+-->""")
    private val assTime = Regex("""(\d+):(\d{1,2}):(\d{1,2})(?:[.,](\d+))?""")
    // Android 的 ICU 要求字面量右花括号也转义；JVM 正则的宽松语法会掩盖此错误。
    private val assOverride = Regex("""\{[^}]*\}""")
    private val drawingMode = Regex("""\\p(\d+)""")

    /**
     * 文件名和在线接口的扩展名可能缺失或不准确，优先识别实际内容。
     * 返回可供播放器加载的文件；空文件、无有效台词或不支持的格式返回 null。
     */
    fun prepareForPlayback(source: File, outputDir: File): File? = runCatching {
        val bytes = source.readBytes()
        val text = bytes.toString(detectCharset(bytes))
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        if (text.isBlank()) return@runCatching null

        val extension = when {
            assEventsHeader.containsMatchIn(text) -> "ass"
            text.trimStart().startsWith("WEBVTT") -> "vtt"
            srtTiming.containsMatchIn(text) -> "srt"
            else -> source.extension.lowercase(Locale.ROOT)
        }
        val (outputText, outputExtension) = when (extension) {
            "ass", "ssa" -> (convertAssToSrt(text) ?: return@runCatching null) to "srt"
            "srt" -> text to "srt"
            // WebVTT 不能只改名成 .srt，否则 GSY 会选错解析器。
            "vtt", "webvtt" -> text to "vtt"
            else -> return@runCatching null
        }
        if (!outputDir.exists()) check(outputDir.mkdirs()) { "无法创建字幕缓存目录" }
        File.createTempFile("subtitle_", ".$outputExtension", outputDir).apply {
            writeText(outputText, Charsets.UTF_8)
        }
    }.onFailure {
        XLog.e("SubtitleConvertUtil prepareForPlayback 失败: ${source.name}", it)
    }.getOrNull()

    private data class AssCue(val startMs: Long, val endMs: Long, val text: String)

    private fun convertAssToSrt(text: String): String? {
        var inEvents = false
        var fields = defaultAssFields
        val cues = mutableListOf<AssCue>()

        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inEvents = trimmed.equals("[Events]", ignoreCase = true)
                fields = defaultAssFields
                continue
            }
            if (!inEvents || ':' !in trimmed) continue
            val record = trimmed.substringBefore(':').trim()
            val body = trimmed.substringAfter(':').trimStart()
            when {
                record.equals("Format", ignoreCase = true) -> {
                    fields = body.split(',').map { it.trim().lowercase(Locale.ROOT) }
                }
                record.equals("Dialogue", ignoreCase = true) -> {
                    val startIndex = fields.indexOf("start")
                    val endIndex = fields.indexOf("end")
                    val textIndex = fields.indexOf("text")
                    if (startIndex < 0 || endIndex < 0 || textIndex < 0) continue
                    val parts = splitAssFields(body, fields.size, textIndex) ?: continue
                    val start = parseAssTime(parts[startIndex]) ?: continue
                    val end = parseAssTime(parts[endIndex]) ?: continue
                    if (end <= start) continue
                    val plainText = cleanAssText(parts[textIndex])
                    if (plainText.isNotBlank()) cues.add(AssCue(start, end, plainText))
                }
            }
        }
        if (cues.isEmpty()) return null

        // ASS 事件不一定按时间排列。同一时段的双语台词合并，避免只显示其中一条。
        val orderedCues = cues.groupBy { it.startMs to it.endMs }.values.map { group ->
            group.first().copy(text = group.map { it.text }.distinct().joinToString("\n"))
        }.sortedWith(compareBy<AssCue> { it.startMs }.thenBy { it.endMs })
        return buildString {
            orderedCues.forEachIndexed { index, cue ->
                append(index + 1).append('\n')
                append(srtTime(cue.startMs)).append(" --> ").append(srtTime(cue.endMs))
                append('\n').append(cue.text).append("\n\n")
            }
        }
    }

    /** Text 中允许逗号；即使 Format 调整字段顺序，也只把多出的逗号归入 Text。 */
    private fun splitAssFields(body: String, fieldCount: Int, textIndex: Int): List<String>? {
        val parts = body.split(',')
        if (parts.size < fieldCount) return null
        val textEnd = textIndex + parts.size - fieldCount + 1
        return parts.take(textIndex) +
            parts.subList(textIndex, textEnd).joinToString(",") + parts.drop(textEnd)
    }

    private fun cleanAssText(raw: String): String {
        // \p1 等绘图指令后的坐标不是台词，直到 \p0 才恢复文本模式。
        var drawing = false
        var position = 0
        val plain = buildString {
            for (tag in assOverride.findAll(raw)) {
                if (!drawing) append(raw.substring(position, tag.range.first))
                drawingMode.findAll(tag.value).lastOrNull()?.let {
                    drawing = it.groupValues[1].any { digit -> digit != '0' }
                }
                position = tag.range.last + 1
            }
            if (!drawing) append(raw.substring(position))
        }
        return plain.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** 支持 ASS 百分秒及常见毫秒写法；坏时间戳只丢弃该条，不使整份字幕转换失败。 */
    private fun parseAssTime(value: String): Long? {
        val match = assTime.matchEntire(value.trim()) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        if (minutes !in 0L..59L || seconds !in 0L..59L ||
            hours > (Long.MAX_VALUE - 3_599_999L) / 3_600_000L
        ) return null
        val millis = match.groupValues[4].take(3).padEnd(3, '0').toLong()
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1000L + millis
    }

    private fun srtTime(ms: Long): String = String.format(
        Locale.ROOT, "%02d:%02d:%02d,%03d",
        ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000
    )

    fun detectCharset(file: File): Charset = detectCharset(file.readBytes())

    /** 先检查 BOM，再严格解码 UTF-8，最后用兼容 GBK/GB2312 的 GB18030。 */
    private fun detectCharset(bytes: ByteArray): Charset {
        fun startsWith(vararg prefix: Int): Boolean = bytes.size >= prefix.size &&
            prefix.indices.all { (bytes[it].toInt() and 0xFF) == prefix[it] }

        return when {
            // UTF-32 LE 的 BOM 包含 UTF-16 LE 的前缀，必须先检查。
            startsWith(0xFF, 0xFE, 0x00, 0x00) -> Charset.forName("UTF-32LE")
            startsWith(0x00, 0x00, 0xFE, 0xFF) -> Charset.forName("UTF-32BE")
            startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
            startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
            startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8
            else -> runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                Charsets.UTF_8
            }.getOrElse { Charset.forName("GB18030") }
        }
    }
}
