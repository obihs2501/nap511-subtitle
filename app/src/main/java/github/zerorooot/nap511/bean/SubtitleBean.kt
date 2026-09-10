package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName

/**
 * 迅雷字幕搜索接口 (https://api-shoulei-ssl.xunlei.com/oracle/subtitle?name=xxx) 返回的单条字幕
 * 实测响应：{"code":0,"data":[{...}],"result":"ok"}，解析时做防御式处理兼容裸数组形态
 */
data class XunleiSubtitleBean(
    @SerializedName("gcid") val gcid: String = "",
    @SerializedName("cid") val cid: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("ext") val ext: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("duration") val duration: Long = 0,
    @SerializedName("languages") val languages: List<String> = emptyList(),
    @SerializedName("source") val source: Int = 0,
    @SerializedName("score") val score: Double = 0.0,
    @SerializedName("fingerprintf_score") val fingerprintScore: Double = 0.0,
    @SerializedName("extra_name") val extraName: String = ""
)

/**
 * 外挂字幕显示样式（持久化到 DataStore）
 */
data class SubtitleStyleState(
    /** 字号 sp */
    val textSizeSp: Int = 24,
    /** 文字颜色 ARGB */
    val textColor: Int = 0xFFFFFFFF.toInt(),
    /** 字体 key: default / sans / serif / mono */
    val fontKey: String = "default",
    /** 粗体 */
    val bold: Boolean = false,
    /** 半透明底色 */
    val background: Boolean = false
)
