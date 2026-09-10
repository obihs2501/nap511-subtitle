package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException.CUSTOM_ERROR_CODE_BASE
import androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_UNSPECIFIED
import androidx.media3.common.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import com.shuyu.gsyvideoplayer.subtitle.GSYSubtitleSource
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleStyleState
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.XunleiSubtitleBean
import github.zerorooot.nap511.dialog.SubtitlePickerDialog
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.SubtitleConvertUtil
import github.zerorooot.nap511.util.SubtitleStyleUtil
import github.zerorooot.nap511.util.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import tv.danmaku.ijk.media.exo2.ExoMediaSourceInterceptListener
import tv.danmaku.ijk.media.exo2.ExoSourceManager
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

data class OssError(
    val code: String = "",
    val message: String = "",
    val requestId: String = "",
    val hostId: String = "",
    val actualObjectSize: Long = 0L,
    val rangeRequested: String = ""
)

/**
 * 标识已被拦截器接管并处理过的视频异常
 */
class HandledVideoException(message: String) : IOException(message)

/**
 * 视频请求错误拦截器
 * @param onErrorCallback 当状态码非 2xx 时触发回调：(url, httpCode, responseBody)
 */
class VideoErrorInterceptor(
    private val onErrorCallback: ((url: String, contentType: MediaType, errorBody: String) -> Boolean)
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        val response = chain.proceed(request)
        val body = response.body
        val contentType = body.contentType() ?: "application/null".toMediaType()
        val contentTypeString = contentType.toString().lowercase()

        // 2. 判断是否属于典型的“非视频/非音视频流”响应类型
        val isErrorContentType = isNonMediaContentType(contentTypeString)

        if (isErrorContentType) {
            // 使用 peekBody 窥探返回的错误信息（如 JSON 字符串或 HTML 网页）
            val errorBody = try {
                response.peekBody(1024 * 1024).string()
            } catch (e: Exception) {
                ""
            }
            // 回调业务层通知（比如提取 JSON 里的 code 和 msg）
            if (onErrorCallback.invoke(url, contentType, errorBody)) {
                // 这会让 ExoPlayer 在 open() 阶段直接捕获网络源头错误，阻止其继续尝试解码 JSON/HTML
                throw HandledVideoException("Invalid video Content-Type: '$contentType', Error Body: $errorBody")
            }
        }

        return response
    }

    /**
     * 判断是否为非媒体类型（即业务错误类型）
     */
    private fun isNonMediaContentType(contentType: String): Boolean {
        // 如果连 Content-Type 都没返回，或者返回了典型的文本/JSON 类型
        if (contentType.isEmpty()) return false

        // 1. 明确的黑名单（优先匹配典型的错误类型）
        val isBlacklisted = contentType.contains("application/json") ||
                contentType.contains("text/html") ||
                contentType.contains("text/plain") ||
                contentType.contains("application/xml") ||
                contentType.contains("text/xml")

        if (isBlacklisted) return true

        // 2. 白名单校验（如果不在黑名单，确保它属于合法媒体流类型）
        // 常见的合法视频/音频 Content-Type 包括:
        // - video/* (video/mp4, video/x-flv 等)
        // - audio/* (audio/mpeg 等)
        // - application/x-mpegurl, application/vnd.apple.mpegurl (HLS .m3u8)
        // - application/dash+xml (DASH)
        // - application/octet-stream (通用二进制流，部分 CDN 会强制返这个)
        val isMediaStream = contentType.contains("video/") ||
                contentType.contains("audio/") ||
                contentType.contains("mpegurl") ||
                contentType.contains("dash+xml") ||
                contentType.contains("application/octet-stream")

        // 如果既不是明确的媒体流，又不是流媒体格式，则判定为错误
        return !isMediaStream
    }
}

class VideoActivity : AppCompatActivity() {
    val playbackErrorMessageMap: Map<Int, String> = mapOf(
        // 基础与通用错误
        ERROR_CODE_UNSPECIFIED to "发生未知错误",
        ERROR_CODE_REMOTE_ERROR to "服务器开小差了，请稍后再试",
        ERROR_CODE_BEHIND_LIVE_WINDOW to "当前直播已过期或进度太落后",
        ERROR_CODE_TIMEOUT to "操作超时，请检查网络",
        ERROR_CODE_FAILED_RUNTIME_CHECK to "系统运行环境异常",
// IO 与网络错误 (最常见的用户网络问题)
        ERROR_CODE_IO_UNSPECIFIED to "网络或文件读取发生未知错误",
        ERROR_CODE_IO_NETWORK_CONNECTION_FAILED to "网络连接失败，请检查网络设置",
        ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT to "网络连接超时，请重试",
        ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE to "播放链接无效（服务器返回数据类型错误）",
        ERROR_CODE_IO_BAD_HTTP_STATUS to "服务器响应异常（视频可能已下架）",
        ERROR_CODE_IO_FILE_NOT_FOUND to "找不到该视频文件",
        ERROR_CODE_IO_NO_PERMISSION to "应用没有网络或文件读取权限",
        ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED to "安全限制，不允许使用非加密的 HTTP 链接",
        ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE to "视频数据读取出错",

        // 解析错误 (文件格式问题)
        ERROR_CODE_PARSING_CONTAINER_MALFORMED to "视频文件已损坏",
        ERROR_CODE_PARSING_MANIFEST_MALFORMED to "播放列表文件已损坏，可能需要验证(高级设置->视频播放验证)",
        ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED to "不支持该视频文件格式",
        ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED to "不支持该播放列表格式",

        // 解码与播放错误 (设备性能或兼容性问题)
        ERROR_CODE_DECODER_INIT_FAILED to "视频解码器初始化失败",
        ERROR_CODE_DECODER_QUERY_FAILED to "当前设备找不到合适的视频解码器",
        ERROR_CODE_DECODING_FAILED to "视频解码失败，无法播放",
        ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES to "视频规格太高，当前设备性能不足以播放",
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED to "当前设备不支持这种视频编码格式",
        ERROR_CODE_AUDIO_TRACK_INIT_FAILED to "音频播放初始化失败",
        ERROR_CODE_AUDIO_TRACK_WRITE_FAILED to "音频数据输出失败",

        // DRM (数字版权管理) 错误
        ERROR_CODE_DRM_UNSPECIFIED to "版权保护模块发生未知错误",
        ERROR_CODE_DRM_SCHEME_UNSUPPORTED to "当前设备不支持该视频的版权保护格式",
        ERROR_CODE_DRM_PROVISIONING_FAILED to "获取数字版权证书失败",
        ERROR_CODE_DRM_CONTENT_ERROR to "受版权保护的视频内容解密失败",
        ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED to "获取视频播放许可证失败",
        ERROR_CODE_DRM_DISALLOWED_OPERATION to "因版权限制，不允许此操作",
        ERROR_CODE_DRM_SYSTEM_ERROR to "设备数字版权系统底层出错",
        ERROR_CODE_DRM_DEVICE_REVOKED to "当前设备的播放权限已被吊销",
        ERROR_CODE_DRM_LICENSE_EXPIRED to "该视频的播放许可证已过期",

        // 自定义错误
        CUSTOM_ERROR_CODE_BASE to "发生自定义系统错误"
    )

    @Volatile
    private var isReloadingVideo = false
    private lateinit var videoPlayer: MyGSYVideoPlayer
    private val videoInfo: VideoInfoBean by lazy {
        Gson().fromJson(
            intent.getStringExtra("bean")!!, VideoInfoBean::class.java
        )
    }
    private val isAutoRotate by lazy {
        videoInfo.isAutoRotate
    }


    private var videoLinkMode = false
    private var autoJumpRetry = true

    // ------------------- 字幕相关 -------------------
    private val subtitleRepository by lazy { SubtitleRepository.getInstance() }
    private val subtitleCacheDir: File by lazy {
        File(cacheDir, "subtitles").apply { if (!exists()) mkdirs() }
    }
    private val cloudSubtitleCandidates = mutableStateListOf<FileBean>()
    private val onlineSubtitleCandidates = mutableStateListOf<XunleiSubtitleBean>()
    private var isSearchingOnline by mutableStateOf(false)
    private var isSearchingCloud by mutableStateOf(false)
    private var searchError by mutableStateOf("")
    private var showSubtitleDialog by mutableStateOf(false)
    private var activeSubtitleName by mutableStateOf<String?>(null)

    /** 字幕样式（持久化）与延迟（仅本次播放） */
    private var subtitleStyle by mutableStateOf(SubtitleStyleState())
    private var subtitleDelayMs by mutableStateOf(0L)

    /** 视频所在目录的 cid，用于查找同目录字幕（getVideoInfo 已把真实 pid 写入 VideoInfoBean.parentId） */
    private val videoParentCid: String by lazy { videoInfo.parentId }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            videoLinkMode = DataStoreUtil.getDataSuspend(ConfigKeyUtil.VIDEO_LINK_MODE, false)
            autoJumpRetry = DataStoreUtil.getDataSuspend(ConfigKeyUtil.AUTO_JUMP_RETRY, true)
            val hideLoading = DataStoreUtil.getDataSuspend(ConfigKeyUtil.HIDE_LOADING_VIEW, false)
            videoPlayer.setHideLoadingView(hideLoading)
            // 读取并应用字幕样式
            subtitleStyle = SubtitleStyleUtil.load()
            applySubtitleStyle(subtitleStyle)
        }
        setContentView(R.layout.activity_video)
        val headerMap = hashMapOf(
            "cookie" to UserSessionManager.cookie,
            "User-Agent" to ConfigKeyUtil.USER_AGENT
        )
        val address = videoInfo.videoUrl.ifEmpty {
            videoInfo.downloadUrl
        }
        val title = videoInfo.fileName
        videoPlayer = findViewById(R.id.pre_video_player)

        initGSYExoPlayerWithOkHttp(this.applicationContext)
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

        videoPlayer.apply {
            setUp(address, false, null, headerMap, title)
            //增加title
            titleTextView.visibility = View.VISIBLE
            titleTextView.isSelected = true
            seekRatio = 10f
            //设置返回键
            backButton.visibility = View.VISIBLE
            isShowFullAnimation = false

            fullscreenButton.setOnClickListener {
                rotateScreen()
            }
            //设置返回按键功能
            backButton.setOnClickListener {
                back()
            }
            //字幕选择入口
            findViewById<View>(R.id.subtitleButton).setOnClickListener {
                showSubtitleDialog = true
                prepareSubtitleCandidates()
            }
            findViewById<View>(R.id.subtitleButtonFullscreen).setOnClickListener {
                showSubtitleDialog = true
                prepareSubtitleCandidates()
            }
        }

        // 挂载 Compose 字幕选择弹窗
        // 注意：不能调用 Activity.setContent()，它会用一个空 ComposeView 整体替换掉
        // setContentView 装入的播放器布局，导致播放器从未挂到窗口上（白屏、无控件、只剩声音）。
        // 这里把 ComposeView 作为兄弟视图叠加在内容层之上，仅承载字幕对话框。
        val subtitleComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        findViewById<ViewGroup>(android.R.id.content).addView(
            subtitleComposeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        subtitleComposeView.setContent {
            val dynamicColor by DataStoreUtil.getDataFlow(ConfigKeyUtil.DYNAMIC_COLOR, true)
                .collectAsStateWithLifecycle(initialValue = true)
            val themeMode by DataStoreUtil.getDataFlow(ConfigKeyUtil.THEME_MODE, "跟随系统")
                .collectAsStateWithLifecycle(initialValue = "跟随系统")
            val darkTheme = when (themeMode) {
                "亮色模式" -> false
                "暗色模式" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            Nap511Theme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                if (showSubtitleDialog) {
                    SubtitlePickerDialog(
                        cloudSubtitles = cloudSubtitleCandidates,
                        onlineSubtitles = onlineSubtitleCandidates,
                        isSearchingOnline = isSearchingOnline,
                        searchError = searchError,
                        selectedSubtitleName = activeSubtitleName,
                        style = subtitleStyle,
                        delayMs = subtitleDelayMs,
                        onDismiss = { showSubtitleDialog = false },
                        onSelectCloudSubtitle = { fileBean ->
                            showSubtitleDialog = false
                            loadCloudSubtitle(fileBean)
                        },
                        onSelectOnlineSubtitle = { bean ->
                            showSubtitleDialog = false
                            loadOnlineSubtitle(bean)
                        },
                        onClearSubtitle = {
                            showSubtitleDialog = false
                            clearSubtitle()
                        },
                        onRetrySearch = { prepareSubtitleCandidates() },
                        onStyleChange = { newStyle, persist ->
                            subtitleStyle = newStyle
                            applySubtitleStyle(newStyle)
                            if (persist) {
                                lifecycleScope.launch { SubtitleStyleUtil.save(newStyle) }
                            }
                        },
                        onDelayChange = { ms ->
                            subtitleDelayMs = ms
                            applySubtitleDelay(ms)
                        }
                    )
                }
            }
        }

        // 启动时自动匹配云盘同目录字幕（在播放器启动后执行）
        autoMatchCloudSubtitle()

        videoPlayer.startPlayLogic()

        //设置横屏
        lifecycleScope.launch {
            if (isAutoRotate) {
                val videoHeight = videoInfo.height
                val videoWidth = videoInfo.width
                if (videoWidth < videoHeight) {
                    rotateScreen()
                }
            }
        }

        videoPlayer.setVideoAllCallBack(gSYErrorCallBack)

        onBackPressedDispatcher.addCallback(this) {
            back()
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    fun rotateScreen() {
        // 获取当前屏幕方向
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 当前是竖屏，强制转为横屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        } else {
            // 当前是横屏，强制转为竖屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }
    }

    override fun onPause() {
        videoPlayer.onVideoPause()
        super.onPause()
    }

    override fun onResume() {
        videoPlayer.onVideoResume()
        super.onResume()
    }

    override fun onDestroy() {
        GSYVideoManager.releaseAllVideos()
        super.onDestroy()
    }


    private fun back(nav: String = "", toast: String = "", resultCode: Int = RESULT_OK) {
        val currentDuration = (videoPlayer.currentPositionWhenPlaying / 1000).toInt()
        val fileBeanIndex = intent.getIntExtra("fileBeanIndex", -1)
        // 1. 创建一个新的 Intent 用来装载要返回的数据
        val returnIntent = Intent().apply {
            putExtra("current_time", currentDuration)
            putExtra("fileBeanIndex", fileBeanIndex)
            putExtra("pickCode", videoInfo.pickCode)
            putExtra("nav", nav)
            putExtra("toast", toast)
        }
        // 2. 设置结果码为 RESULT_OK，并传入 Intent
        setResult(resultCode, returnIntent)
        //释放所有
        videoPlayer.setVideoAllCallBack(null);
        finish()
    }


    private fun isHandledException(throwable: Throwable?): Boolean {
        var cause = throwable
        while (cause != null) {
            if (cause is HandledVideoException) return true
            cause = cause.cause
        }
        return false
    }

    val gSYErrorCallBack = object : GSYSampleCallBack() {
        override fun onPlayError(url: String?, vararg objects: Any?) {
            val playerManager = videoPlayer.gsyVideoManager.player as? Exo2PlayerManager
            val exoPlayer = playerManager?.mediaPlayer as? ExoPlayer
            val exoError = exoPlayer?.playerError
            if (isHandledException(exoError)) {
                return
            }

            super.onPlayError(url, objects)
            val errorStatus =
                if (objects[2] != null && videoPlayer.gsyVideoManager.player is Exo2PlayerManager) {
                    val code = (objects[2] as Int)
                    playbackErrorMessageMap.getOrDefault(code, "")
                        .ifEmpty { "发生未记录的错误 (错误码: $code)" }
                } else {
                    "UNKNOWN_ERROR"
                }
            XLog.e("$title 播放失败 $errorStatus")
            Toast.makeText(baseContext, errorStatus, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * 全局配置 GSYVideoPlayer (ExoPlayer) 使用自定义的 OkHttpClient 拦截器
     */
    fun initGSYExoPlayerWithOkHttp(context: Context) {
        // 1. 创建包含错误拦截器的 OkHttpClient
        val customOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(VideoErrorInterceptor { url, contentType, errorBody ->
                XLog.e(
                    "GSY Player 网络请求 $url 失败: [$contentType] -> Body: ${
                        errorBody.replace(
                            "\n",
                            ""
                        )
                    }"
                )
                if (errorBody.isEmpty()) {
                    if (!videoLinkMode && autoJumpRetry) {
                        playNewVideo()
                        return@VideoErrorInterceptor true
                    }

                    back(
                        toast = "视频地址错误！请打开\"视频解析模式\"请求正确链接",
                        resultCode = RESULT_CANCELED
                    )
                    return@VideoErrorInterceptor true
                }

                runCatching { Gson().fromJson(errorBody, JsonObject::class.java) }
                    .onSuccess { fromJson ->
                        if (fromJson.has("error")) {
                            val message = fromJson.get("error").asString
                            back(
                                nav = "VerifyVideoAccount",
                                toast = message,
                                resultCode = RESULT_CANCELED
                            )
                            return@VideoErrorInterceptor true
                        }
                    }
                runCatching { parseOssErrorWithDom(errorBody).message }
                    .onSuccess { message ->
                        back(toast = message, resultCode = RESULT_CANCELED)
                        return@VideoErrorInterceptor true
                    }

                return@VideoErrorInterceptor false
            })
            .build()

        // 3. 拦截 GSYVideoPlayer 的 MediaSource 构建流程
        ExoSourceManager.setExoMediaSourceInterceptListener(object :
            ExoMediaSourceInterceptListener {
            override fun getMediaSource(
                dataSource: String?,
                preview: Boolean,
                cacheEnable: Boolean,
                isLooping: Boolean,
                cacheDir: File?
            ): MediaSource? {
                return null
            }

            @OptIn(UnstableApi::class)
            override fun getHttpDataSourceFactory(
                userAgent: String?,
                listener: TransferListener?,
                connectTimeoutMillis: Int,
                readTimeoutMillis: Int,
                mapHeadData: Map<String?, String?>?,
                allowCrossProtocolRedirects: Boolean
            ): DataSource.Factory {
                // 2. 将 OkHttpClient 包装为 ExoPlayer 的 HttpDataSource.Factory
                val okHttpDataSourceFactory = OkHttpDataSource.Factory(customOkHttpClient)
                // 如果有自定义的 Request Header，同步给 Factory
                mapHeadData?.let {
                    okHttpDataSourceFactory.setDefaultRequestProperties(it as Map<String, String>)
                }
                return okHttpDataSourceFactory
            }

            @OptIn(UnstableApi::class)
            override fun cacheWriteDataSinkFactory(
                cachePath: String?,
                url: String?
            ): DataSink.Factory? {
                return null
            }
        })
    }

    fun playNewVideo() {
        // 如果已经在重新获取链接中，直接跳过
        if (isReloadingVideo) return
        isReloadingVideo = true
        App.instance.toast("视频地址错误！正在重新获取新链接")
        lifecycleScope.launch {
            try {
                val fileRepository = FileRepository.getInstance()
                val video = fileRepository.video(videoInfo.pickCode)
                XLog.i("playNewVideo $video")
                this@VideoActivity.videoPlayer.playNext(video.downloadUrl, video.fileName)
            } catch (e: Exception) {
                isReloadingVideo = false // 异常时重置标志位
            }
        }
    }

    fun parseOssErrorWithDom(xmlString: String): OssError {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlString.byteInputStream())
        doc.documentElement.normalize()

        fun getValue(tag: String): String {
            return doc.getElementsByTagName(tag).item(0)?.textContent.orEmpty()
        }

        return OssError(
            code = getValue("Code"),
            message = getValue("Message"),
            requestId = getValue("RequestId"),
            hostId = getValue("HostId"),
            actualObjectSize = getValue("ActualObjectSize").toLongOrNull() ?: 0L,
            rangeRequested = getValue("RangeRequested")
        )
    }

    // ======================= 字幕逻辑 =======================

    /**
     * 打开字幕弹窗时准备候选列表：
     * 1. 同目录字幕（首次进入时查询）
     * 2. 触发在线字幕搜索
     */
    private fun prepareSubtitleCandidates() {
        if (videoParentCid.isNotEmpty() && cloudSubtitleCandidates.isEmpty() && !isSearchingCloud) {
            searchCloudSubtitles()
        }
        searchOnlineSubtitles()
    }

    /**
     * 自动匹配同目录字幕并默认加载
     */
    private fun autoMatchCloudSubtitle() {
        if (videoParentCid.isEmpty()) return
        lifecycleScope.launch {
            // 直接等待搜索完成后再取第一个候选
            val result = subtitleRepository.searchCloudSubtitles(
                videoParentCid, videoInfo.fileName
            )
            cloudSubtitleCandidates.clear()
            cloudSubtitleCandidates.addAll(result)
            // 自动挂载第一个匹配的同目录字幕
            val first = result.firstOrNull() ?: return@launch
            loadCloudSubtitle(first, toastOnFail = false)
        }
    }

    private fun searchCloudSubtitles() {
        // 避免并发搜索：进行中直接跳过
        if (isSearchingCloud) return
        isSearchingCloud = true
        lifecycleScope.launch {
            val result = subtitleRepository.searchCloudSubtitles(
                videoParentCid, videoInfo.fileName
            )
            cloudSubtitleCandidates.clear()
            cloudSubtitleCandidates.addAll(result)
            isSearchingCloud = false
        }
    }

    private fun searchOnlineSubtitles() {
        if (isSearchingOnline) return
        isSearchingOnline = true
        searchError = ""
        lifecycleScope.launch {
            val result = subtitleRepository.searchOnlineSubtitles(videoInfo.fileName)
            onlineSubtitleCandidates.clear()
            onlineSubtitleCandidates.addAll(result)
            if (result.isEmpty()) {
                searchError = if (subtitleRepository.lastSearchFailed) {
                    "在线字幕搜索失败，请检查网络"
                } else {
                    ""
                }
            }
            isSearchingOnline = false
        }
    }

    /**
     * 加载 115 云盘同目录字幕：下载 -> 转换 -> 挂载
     */
    private fun loadCloudSubtitle(fileBean: FileBean, toastOnFail: Boolean = true) {
        lifecycleScope.launch {
            val localFile = subtitleRepository.downloadCloudSubtitle(fileBean, subtitleCacheDir)
            val mounted = localFile != null && mountSubtitleFile(localFile)
            if (mounted) {
                activeSubtitleName = fileBean.name
                App.instance.toast("字幕已挂载: ${fileBean.name}")
            } else {
                if (toastOnFail) App.instance.toast("字幕挂载失败")
                XLog.e("云盘字幕挂载失败: ${fileBean.name}")
            }
        }
    }

    /**
     * 加载迅雷在线字幕：下载 -> 转换 -> 挂载
     */
    private fun loadOnlineSubtitle(bean: XunleiSubtitleBean) {
        lifecycleScope.launch {
            val localFile = subtitleRepository.downloadSubtitle(
                bean.url, bean.name, subtitleCacheDir
            )
            val mounted = localFile != null && mountSubtitleFile(localFile)
            if (mounted) {
                activeSubtitleName = bean.name
                App.instance.toast("字幕已挂载: ${bean.name}")
            } else {
                App.instance.toast("字幕下载或解析失败")
            }
        }
    }

    /**
     * 把本地字幕文件（srt/ass/ssa/vtt）挂载到 GSY 播放器
     * ass/ssa 会先转换为 srt；文件读写放在 IO 线程，避免卡 UI
     */
    private suspend fun mountSubtitleFile(file: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val srtFile = SubtitleConvertUtil.convertToSrt(file, subtitleCacheDir)
                    ?: return@withContext false
                val source = GSYSubtitleSource.Builder(
                    android.net.Uri.fromFile(srtFile).toString()
                ).setLabel(file.name).build()
                // setSubtitleSource 内部仅做 UI 操作，切回主线程
                withContext(Dispatchers.Main) {
                    videoPlayer.setSubtitleSource(source)
                    // 字幕控制器可能被重建，重新套用样式与延迟
                    applySubtitleStyle(subtitleStyle)
                    applySubtitleDelay(subtitleDelayMs)
                }
                true
            }.onFailure {
                XLog.e("挂载字幕失败: ${file.name}", it)
            }.getOrDefault(false)
        }

    /** 应用字幕样式：字号/颜色/底色走 GSYSubtitleStyle，字体直接设到字幕 TextView */
    private fun applySubtitleStyle(style: SubtitleStyleState) {
        runCatching {
            videoPlayer.setSubtitleStyle(SubtitleStyleUtil.toGsyStyle(style))
            videoPlayer.applySubtitleTypeface(SubtitleStyleUtil.toTypeface(style))
        }.onFailure { XLog.e("应用字幕样式失败", it) }
    }

    /**
     * 字幕延迟：UI 语义为正值=字幕推后显示。
     * GSY 的 offset 语义相反（sourcePosition = position + offset，正值=提前），故取负。
     */
    private fun applySubtitleDelay(delayMs: Long) {
        runCatching { videoPlayer.setSubtitleOffsetMs(-delayMs) }
            .onFailure { XLog.e("设置字幕延迟失败", it) }
    }

    private fun clearSubtitle() {
        runCatching { videoPlayer.setSubtitleSource(null) }
        activeSubtitleName = null
    }
}