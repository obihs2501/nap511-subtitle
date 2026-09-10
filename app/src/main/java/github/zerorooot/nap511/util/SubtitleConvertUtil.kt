package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 轻量字幕解析工具：把 srt / ass / ssa / vtt 统一转换为 GSYSubtitleCue 列表
 *
 * GSY 13.2.1 内置字幕控制器只支持 SRT 与 WebVTT 两种文本格式，
 * 这里补充 ASS/SSA 的解析，并输出为兼容的临时 .srt 文件，
 * 使 GSY 内置渲染管线可以直接挂载。
 */
object SubtitleConvertUtil {

    /**
     * 把任意支持的字幕文件转换为 UTF-8 的 .srt 文件，返回转换后的文件
     * srt/vtt 可能是 GBK 编码，也统一重编码为 UTF-8 后输出
     */
    fun convertToSrt(source: File, outputDir: File): File? {
        return runCatching {
            val ext = source.extension.lowercase()
            when (ext) {
                "srt" -> reEncodeAsUtf8(source, outputDir)
                "vtt", "webvtt" -> reEncodeAsUtf8(source, outputDir)
                "ass", "ssa" -> convertAssToSrt(source, outputDir)
                else -> null
            }
        }.onFailure {
            XLog.e("SubtitleConvertUtil convertToSrt 失败: ${source.name}", it)
        }.getOrNull()
    }

    /**
     * 统一转存为 UTF-8 编码的 .srt 文件
     */
    private fun reEncodeAsUtf8(source: File, outputDir: File): File? {
        val text = source.readText(charset = detectCharset(source))
        if (text.isBlank()) return null
        if (!outputDir.exists()) outputDir.mkdirs()
        val target = File(outputDir, source.nameWithoutExtension + ".srt")
        target.writeText(text, Charsets.UTF_8)
        return target
    }

    /**
     * ASS/SSA -> SRT：
     * 1. 提取 [Events] 段中的 Dialogue 行
     * 2. 解析 Format 行拿到字段顺序（Layer,Start,End,Style,Name,MarginL,...,Text）
     * 3. 去除内联标签 {\...}，\N 与 \n 换行
     */
    private fun convertAssToSrt(source: File, outputDir: File): File? {
        val text = source.readText(charset = detectCharset(source))
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")

        var inEvents = false
        var formatFields: List<String> = emptyList()
        val cues = mutableListOf<AssCue>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[Events]") -> {
                    inEvents = true
                }
                trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                    inEvents = false
                }
                inEvents && trimmed.startsWith("Format:") -> {
                    formatFields = trimmed.removePrefix("Format:")
                        .split(",").map { it.trim().lowercase() }
                }
                inEvents && trimmed.startsWith("Dialogue:") -> {
                    val body = trimmed.removePrefix("Dialogue:")
                    if (formatFields.isEmpty()) {
                        // 无 Format 行时按 Aegisub 默认顺序
                        formatFields = listOf(
                            "layer", "start", "end", "style", "name",
                            "marginl", "marginr", "marginv", "effect", "text"
                        )
                    }
                    // 前 n-1 个字段按逗号切分，剩余全部并入 Text（Text 内可含逗号）
                    val parts = body.split(",", limit = formatFields.size)
                    if (parts.size == formatFields.size) {
                        val map = formatFields.mapIndexed { i, f -> f to parts[i] }.toMap()
                        val start = map["start"] ?: continue
                        val end = map["end"] ?: continue
                        val plainText = cleanAssText(parts.last())
                        if (plainText.isNotBlank()) {
                            cues.add(AssCue(start, end, plainText))
                        }
                    }
                }
            }
        }
        if (cues.isEmpty()) return null

        if (!outputDir.exists()) outputDir.mkdirs()
        val target = File(outputDir, source.nameWithoutExtension + ".srt")
        target.writeText(buildSrt(cues), Charsets.UTF_8)
        return target
    }

    private data class AssCue(val start: String, val end: String, val text: String)

    private fun cleanAssText(raw: String): String {
        return raw
            .replace(Regex("\\{[^}]*}"), "")   // 去除 {\...} 标签
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace(Regex("<[^>]*>"), "")      // 去除残留 HTML 标签
            .trim()
    }

    private fun buildSrt(cues: List<AssCue>): String {
        val sb = StringBuilder()
        cues.forEachIndexed { index, cue ->
            sb.append(index + 1).append('\n')
            sb.append(assTimeToSrtTime(cue.start)).append(" --> ")
                .append(assTimeToSrtTime(cue.end)).append('\n')
            sb.append(cue.text).append("\n\n")
        }
        return sb.toString()
    }

    /**
     * ASS 时间 H:MM:SS.cc (百分之一秒) -> SRT 时间 HH:MM:SS,mmm
     */
    private fun assTimeToSrtTime(assTime: String): String {
        val cleaned = assTime.trim()
        val parts = cleaned.split(":")
        if (parts.size != 3) return "00:00:00,000"
        val hours = parts[0].trim().padStart(2, '0')
        val minutes = parts[1].trim().padStart(2, '0')
        val secParts = parts[2].split(".")
        val seconds = secParts.getOrNull(0)?.trim()?.padStart(2, '0') ?: "00"
        val centis = secParts.getOrNull(1)?.trim()?.padEnd(2, '0')?.take(2) ?: "00"
        val millis = (centis + "0").take(3)
        return "$hours:$minutes:$seconds,$millis"
    }

    /**
     * 简易编码探测：中文环境常见 GBK 字幕，先尝试 UTF-8 严格解码，失败则回退 GBK
     */
    fun detectCharset(file: File): Charset {
        return runCatching {
            val bytes = file.readBytes()
            // UTF-8 BOM
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return Charsets.UTF_8
            }
            // 严格 UTF-8 解码测试
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            Charsets.UTF_8
        }.getOrElse {
            Charset.forName("GBK")
        }
    }
}
