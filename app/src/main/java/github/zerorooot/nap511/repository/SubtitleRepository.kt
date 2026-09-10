package github.zerorooot.nap511.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.XunleiSubtitleBean
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
     * 响应为包裹对象 {code, result, data:[...]}；做防御式解析，
     * 兼容直接返回裸数组 [ {...}, ... ] 的历史形态
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
                    val element = JsonParser.parseString(body)
                    val items = when {
                        element.isJsonArray -> element.asJsonArray
                        element.isJsonObject && element.asJsonObject.has("data") &&
                                element.asJsonObject.get("data").isJsonArray ->
                            element.asJsonObject.getAsJsonArray("data")
                        else -> throw IOException("迅雷字幕接口响应结构异常")
                    }
                    items.map { item ->
                        gson.fromJson(item, XunleiSubtitleBean::class.java)
                    }
                }
            }.onFailure {
                lastSearchFailed = true
                XLog.e("SubtitleRepository searchOnlineSubtitles 失败", it)
            }.getOrElse { emptyList() }
        }
    }

    /**
     * 在 115 网盘同目录中查找与视频匹配的字幕文件
     */
    suspend fun searchCloudSubtitles(parentCid: String, videoName: String): List<FileBean> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val filesBean: FilesBean = FileRepository.getInstance().getFiles(
                    cid = parentCid,
                    limit = 1150
                )
                val videoBaseName = videoName.substringBeforeLast('.')
                // Gson 原始数据里 isFolder 未被填充（formatFileBeanList 在 ViewModel 层才计算），
                // 用 fileId 为空判断文件夹
                filesBean.fileBeanList.filter { fileBean ->
                    fileBean.fileId.isNotEmpty() && isSubtitleFile(fileBean.name) && isNameMatch(
                        videoBaseName, fileBean.name.substringBeforeLast('.')
                    )
                }
            }.onFailure {
                XLog.e("SubtitleRepository searchCloudSubtitles 失败", it)
            }.getOrElse { emptyList() }
        }
    }

    /**
     * 文件名匹配：字幕名与视频名去掉扩展名后互为前缀，且剩余部分必须像
     * 语言/标签后缀（.chs、.eng、简体、_1 等），避免 "a.mp4" 误配 "avatar.chs.srt"
     */
    private fun isNameMatch(videoBaseName: String, subtitleBaseName: String): Boolean {
        val v = videoBaseName.lowercase()
        val s = subtitleBaseName.lowercase()
        if (v == s) return true
        val longer = if (s.length > v.length) s else v
        val shorter = if (s.length > v.length) v else s
        if (!longer.startsWith(shorter)) return false
        // 剩余部分以分隔符开头（. _ - 空格），视为同一文件的不同标签版本
        val rest = longer.substring(shorter.length)
        return rest.firstOrNull()?.isLetterOrDigit() != true
    }

    fun isSubtitleFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SUBTITLE_EXTENSIONS
    }

    /** 字幕文件名是否与视频文件名匹配（去扩展名后同名或同名+分隔符后缀） */
    fun matchesVideo(videoName: String, subtitleName: String): Boolean =
        isNameMatch(videoName.substringBeforeLast('.'), subtitleName.substringBeforeLast('.'))

    /** 目录浏览结果 */
    data class FolderListing(
        val path: List<PathBean>,
        val folders: List<FileBean>,
        val subtitles: List<FileBean>
    )

    /**
     * 列出目录下的子文件夹与字幕文件（用于在弹窗里逐级浏览网盘挑选字幕）
     * Gson 原始数据里 isFolder 未填充，用 fileId 为空判文件夹
     */
    suspend fun listFolder(cid: String): FolderListing = withContext(Dispatchers.IO) {
        val filesBean: FilesBean = FileRepository.getInstance().getFiles(cid = cid, limit = 1150)
        @Suppress("USELESS_ELVIS")
        val path: List<PathBean> = filesBean.path ?: emptyList()
        val folders = filesBean.fileBeanList
            .filter { it.fileId.isEmpty() }
            .sortedBy { it.name.lowercase() }
        val subtitles = filesBean.fileBeanList
            .filter { it.fileId.isNotEmpty() && isSubtitleFile(it.name) }
            .sortedBy { it.name.lowercase() }
        FolderListing(path, folders, subtitles)
    }

    /**
     * 把本机选中的字幕（SAF Uri）复制到缓存目录，返回本地文件；扩展名不支持或读取失败返回 null
     */
    suspend fun importLocalSubtitle(context: Context, uri: Uri, cacheDir: File): File? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                var name = ""
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: "" }
                if (name.isEmpty()) {
                    name = uri.lastPathSegment?.substringAfterLast('/') ?: "local_subtitle"
                }
                if (!isSubtitleFile(name)) throw IOException("不支持的字幕格式: $name")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val target = File(cacheDir, safeName)
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                } ?: throw IOException("无法打开所选文件")
                target
            }.onFailure {
                XLog.e("SubtitleRepository importLocalSubtitle 失败: $uri", it)
            }.getOrNull()
        }
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
