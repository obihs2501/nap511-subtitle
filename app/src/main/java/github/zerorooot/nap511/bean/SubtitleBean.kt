package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName

/**
 * 迅雷字幕搜索接口 (https://api-shoulei-ssl.xunlei.com/oracle/subtitle?name=xxx) 返回的单条字幕
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

data class XunleiSubtitleResponse(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("result") val result: String = "",
    @SerializedName("data") val data: List<XunleiSubtitleBean> = emptyList()
)
