package github.zerorooot.nap511.repository

import com.google.gson.Gson
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.XunleiSubtitleBean
import github.zerorooot.nap511.bean.XunleiSubtitleResponse
import github.zerorooot.nap511.util.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 字幕来源：
 * 1. 迅雷字幕搜索接口（在线字幕）
 * 2. 115 网盘同目录下的字幕文件（本地字幕）
 */
class SubtitleRepository {
    companion object {
        private const val XUNLEI_SUBTITLE_API = "https://api-shoulei-ssl.xunlei.com/oracle/subtitle"

        /** 支持的字幕文件扩展名 */
        val SUBTITLE_EXTENSIONS = listOf("srt", "ass", "ssa", "vtt", "webvtt", "sub")

        @Volatile
        private var INSTANCE: SubtitleRepository? = null
        fun getInstance(): SubtitleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubtitleRepository().also { INSTANCE = it }
            }
        }
    }

    /** 上次在线搜索是否因网络/接口异常失败（区别于"无结果"） */
    @Volatile
    var lastSearchFailed: Boolean = false
        private set

    private val gson = Gson()

    /**
     * 通过迅雷接口搜索在线字幕
     */
    suspend fun searchOnlineSubtitles(videoName: String): List<XunleiSubtitleBean> {
        lastSearchFailed = false
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$XUNLEI_SUBTITLE_API?name=${java.net.URLEncoder.encode(videoName, "UTF-8")}"
                val request = Request.Builder().url(url).get().build()
                val response = NetworkClient.sharedOkHttpClient.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) throw IOException("迅雷字幕接口响应异常: ${it.code}")
                    val body = it.body.string()
                    val result = gson.fromJson(body, XunleiSubtitleResponse::class.java)
                    if (result.code != 0) throw IOException("迅雷字幕接口返回错误: ${result.code}")
                    result.data
                }
            }.onFailure {
                lastSearchFailed = true
                XLog.e("SubtitleRepository searchOnlineSubtitles 失败", it)
            }.getOrElse { emptyList() }
        }
    }

    /**
     * 在 115 网盘同目录中查找与视频匹配的字幕文件
     * 匹配规则：去掉扩展名后，字幕文件名以视频文件名开头（或视频文件名以字幕文件名开头）
     */
    suspend fun searchCloudSubtitles(parentCid: String, videoName: String): List<FileBean> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val filesBean: FilesBean = FileRepository.getInstance().getFiles(
                    cid = parentCid,
                    limit = 1150
                )
                val videoBaseName = videoName.substringBeforeLast('.')
                filesBean.fileBeanList.filter { fileBean ->
                    !fileBean.isFolder && isSubtitleFile(fileBean.name) && isNameMatch(
                        videoBaseName, fileBean.name.substringBeforeLast('.')
                    )
                }
            }.onFailure {
                XLog.e("SubtitleRepository searchCloudSubtitles 失败", it)
            }.getOrElse { emptyList() }
        }
    }

    /**
     * 文件名匹配：字幕名与视频名去掉扩展名后，互为前缀（容忍 "video.chs.srt" 与 "video.mp4"）
     */
    private fun isNameMatch(videoBaseName: String, subtitleBaseName: String): Boolean {
        val v = videoBaseName.lowercase()
        val s = subtitleBaseName.lowercase()
        return s.startsWith(v) || v.startsWith(s)
    }

    fun isSubtitleFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SUBTITLE_EXTENSIONS
    }

    /**
     * 下载字幕到缓存目录，返回本地文件
     */
    suspend fun downloadSubtitle(
        subtitleUrl: String,
        fileName: String,
        cacheDir: File
    ): File? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(subtitleUrl).get().build()
                val response = NetworkClient.sharedOkHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) throw IOException("字幕下载失败: ${resp.code}")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val target = File(cacheDir, safeName)
                    target.outputStream().use { out ->
                        resp.body.byteStream().copyTo(out)
                    }
                    target
                }
            }.onFailure {
                XLog.e("SubtitleRepository downloadSubtitle 失败: $subtitleUrl", it)
            }.getOrNull()
        }
    }

    /**
     * 下载 115 网盘字幕文件到本地缓存（走 115 下载链接）
     */
    suspend fun downloadCloudSubtitle(
        fileBean: FileBean,
        cacheDir: File
    ): File? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val repository = FileRepository.getInstance()
                val inputStream =
                    repository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                        ?: throw IOException("获取字幕下载链接失败")
                inputStream.use { input ->
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val target = File(cacheDir, fileBean.name)
                    target.outputStream().use { out ->
                        input.copyTo(out)
                    }
                    target
                }
            }.onFailure {
                XLog.e("SubtitleRepository downloadCloudSubtitle 失败: ${fileBean.name}", it)
            }.getOrNull()
        }
    }
}
