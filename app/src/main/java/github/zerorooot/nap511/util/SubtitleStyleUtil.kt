package github.zerorooot.nap511.util

import android.graphics.Color
import android.graphics.Typeface
import com.shuyu.gsyvideoplayer.subtitle.GSYSubtitleStyle
import github.zerorooot.nap511.bean.SubtitleStyleState

/**
 * 字幕样式：预设项、持久化、转换为 GSY 样式 / Typeface
 */
object SubtitleStyleUtil {
    const val DEFAULT_SIZE = 24
    const val MIN_SIZE = 12
    const val MAX_SIZE = 48
    const val DEFAULT_COLOR = -1 // Color.WHITE
    const val DEFAULT_FONT = "default"

    /** 字幕距底部距离 (dp)，略高于 56dp 的底部控制栏 */
    private const val BOTTOM_MARGIN_DP = 60

    /** 颜色预设：名称 -> ARGB */
    val COLOR_PRESETS: List<Pair<String, Int>> = listOf(
        "白色" to 0xFFFFFFFF.toInt(),
        "黄色" to 0xFFFFEB3B.toInt(),
        "青色" to 0xFF00E5FF.toInt(),
        "绿色" to 0xFF76FF03.toInt(),
        "橙色" to 0xFFFFA726.toInt(),
        "粉色" to 0xFFFF80AB.toInt()
    )
    val COLOR_NAMES: Array<String> = COLOR_PRESETS.map { it.first }.toTypedArray()

    /** 字体预设：key -> 显示名 */
    val FONT_PRESETS: List<Pair<String, String>> = listOf(
        "default" to "默认",
        "sans" to "无衬线",
        "serif" to "衬线",
        "mono" to "等宽"
    )
    val FONT_NAMES: Array<String> = FONT_PRESETS.map { it.second }.toTypedArray()

    /** 设置页字号档位 */
    val SIZE_ENTRIES: Array<String> = arrayOf("16 sp", "20 sp", "24 sp", "28 sp", "32 sp", "36 sp", "40 sp", "48 sp")

    fun colorName(argb: Int): String =
        COLOR_PRESETS.firstOrNull { it.second == argb }?.first ?: "自定义"

    fun colorFromName(name: String): Int =
        COLOR_PRESETS.firstOrNull { it.first == name }?.second ?: DEFAULT_COLOR

    fun fontName(key: String): String =
        FONT_PRESETS.firstOrNull { it.first == key }?.second ?: "默认"

    fun fontKeyFromName(name: String): String =
        FONT_PRESETS.firstOrNull { it.second == name }?.first ?: DEFAULT_FONT

    fun sizeFromEntry(entry: String): Int =
        entry.filter { it.isDigit() }.toIntOrNull()?.coerceIn(MIN_SIZE, MAX_SIZE) ?: DEFAULT_SIZE

    suspend fun load(): SubtitleStyleState = SubtitleStyleState(
        textSizeSp = DataStoreUtil.getDataSuspend(ConfigKeyUtil.SUBTITLE_TEXT_SIZE, DEFAULT_SIZE)
            .coerceIn(MIN_SIZE, MAX_SIZE),
        textColor = DataStoreUtil.getDataSuspend(ConfigKeyUtil.SUBTITLE_TEXT_COLOR, DEFAULT_COLOR),
        fontKey = DataStoreUtil.getDataSuspend(ConfigKeyUtil.SUBTITLE_FONT, DEFAULT_FONT),
        bold = DataStoreUtil.getDataSuspend(ConfigKeyUtil.SUBTITLE_BOLD, false),
        background = DataStoreUtil.getDataSuspend(ConfigKeyUtil.SUBTITLE_BACKGROUND, false)
    )

    suspend fun save(style: SubtitleStyleState) {
        DataStoreUtil.putDataSuspend(ConfigKeyUtil.SUBTITLE_TEXT_SIZE, style.textSizeSp)
        DataStoreUtil.putDataSuspend(ConfigKeyUtil.SUBTITLE_TEXT_COLOR, style.textColor)
        DataStoreUtil.putDataSuspend(ConfigKeyUtil.SUBTITLE_FONT, style.fontKey)
        DataStoreUtil.putDataSuspend(ConfigKeyUtil.SUBTITLE_BOLD, style.bold)
        DataStoreUtil.putDataSuspend(ConfigKeyUtil.SUBTITLE_BACKGROUND, style.background)
    }

    /** 转为 GSY 内置字幕样式（字体不在其中，见 [toTypeface]） */
    fun toGsyStyle(style: SubtitleStyleState): GSYSubtitleStyle =
        GSYSubtitleStyle.Builder()
            .setTextColor(style.textColor)
            .setTextSizeSp(style.textSizeSp.coerceIn(MIN_SIZE, MAX_SIZE).toFloat())
            .setBackgroundColor(if (style.background) 0x80000000.toInt() else Color.TRANSPARENT)
            .setBottomMarginDp(BOTTOM_MARGIN_DP)
            .build()

    fun toTypeface(style: SubtitleStyleState): Typeface {
        val base = when (style.fontKey) {
            "sans" -> Typeface.SANS_SERIF
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        return Typeface.create(base, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
