package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
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
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleBrowseState
import github.zerorooot.nap511.bean.SubtitleStyleState
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.XunleiSubtitleBean
import github.zerorooot.nap511.dialog.SubtitlePickerDialog
import github.zerorooot.nap511.dialog.EpisodePickerDialog
import github.zerorooot.nap511.dialog.PlaybackSettingsDialog
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.SubtitleConvertUtil
import github.zerorooot.nap511.util.PlaybackUtil
import github.zerorooot.nap511.util.SubtitleStyleUtil
import github.zerorooot.nap511.util.UserSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    private var videoInfo by mutableStateOf(VideoInfoBean())
    private val isAutoRotate by lazy {
        videoInfo.isAutoRotate
    }


    private var videoLinkMode = false
    private var autoJumpRetry = true
    private var showEpisodeDialog by mutableStateOf(false)
    private var showPlaybackSettings by mutableStateOf(false)
    private var episodeFiles by mutableStateOf<List<FileBean>>(emptyList())
    private var episodeLoading by mutableStateOf(false)
    private var episodeError by mutableStateOf("")
    private var switchingEpisodeName by mutableStateOf("")
    private var episodeDirectory = ""
    private var episodeLoadJob: Job? = null
    private var episodeSwitchJob: Job? = null
    private var playbackSpeed by mutableStateOf(1f)
    private var holdSpeed by mutableStateOf(2f)
    private var seekStepSeconds by mutableStateOf(15L)
    private var autoPlayNext by mutableStateOf(false)
    private var sleepTimerSeconds by mutableStateOf(0L)
    private var stopAfterEpisode by mutableStateOf(false)
    private var sleepTimerJob: Job? = null
    private var resumeOnForeground = false
    private var videoDurationMs = 0L
    private var subtitleSession = 0
    private var subtitleSelectionId = 0
    private var sleepDeadlineMs = 0L
    private var pausedBySleepTimer = false

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
    private var subtitleSearchKeyword by mutableStateOf("")
    private var lastOnlineSearchKeyword by mutableStateOf<String?>(null)
    private var onlineSearchJob: Job? = null
    private var showSubtitleDialog by mutableStateOf(false)
    private var activeSubtitleName by mutableStateOf<String?>(null)

    /** 字幕样式（持久化）与延迟（仅本次播放） */
    private var subtitleStyle by mutableStateOf(SubtitleStyleState())
    private var subtitleDelayMs by mutableStateOf(0L)

    /** 网盘字幕浏览状态 */
    private var browseState by mutableStateOf(SubtitleBrowseState())

    /** 本机字幕文件选择器：srt/ass 的 MIME 各家不一，用通配 MIME 打开选择器，再按扩展名校验 */
    private val localSubtitlePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) loadLocalSubtitle(uri)
        }

    /** 视频所在目录的 cid，用于查找同目录字幕（getVideoInfo 已把真实 pid 写入 VideoInfoBean.parentId） */
    private val videoParentCid: String get() = videoInfo.parentId

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        videoInfo = runCatching {
            Gson().fromJson(intent.getStringExtra("bean"), VideoInfoBean::class.java)
        }.getOrNull() ?: run { finish(); return }
        setContentView(R.layout.activity_video)
        videoPlayer = findViewById(R.id.pre_video_player)
        lifecycleScope.launch {
            videoLinkMode = DataStoreUtil.getDataSuspend(ConfigKeyUtil.VIDEO_LINK_MODE, false)
            autoJumpRetry = DataStoreUtil.getDataSuspend(ConfigKeyUtil.AUTO_JUMP_RETRY, true)
            val hideLoading = DataStoreUtil.getDataSuspend(ConfigKeyUtil.HIDE_LOADING_VIEW, false)
            videoPlayer.setHideLoadingView(hideLoading)
            // 读取并应用字幕样式
            subtitleStyle = SubtitleStyleUtil.load()
            applySubtitleStyle(subtitleStyle)
            playbackSpeed = DataStoreUtil.getDataSuspend(ConfigKeyUtil.PLAYER_SPEED, 1f)
                .takeIf { it in PlaybackUtil.SPEEDS } ?: 1f
            holdSpeed = DataStoreUtil.getDataSuspend(ConfigKeyUtil.PLAYER_HOLD_SPEED, 2f)
                .takeIf { it in PlaybackUtil.HOLD_SPEEDS } ?: 2f
            seekStepSeconds = DataStoreUtil.getDataSuspend(ConfigKeyUtil.PLAYER_SEEK_STEP, 15L)
                .takeIf { it in PlaybackUtil.SEEK_STEPS } ?: 15L
            autoPlayNext = DataStoreUtil.getDataSuspend(ConfigKeyUtil.PLAYER_AUTO_NEXT, false)
            videoPlayer.setPlaybackSpeed(playbackSpeed)
            videoPlayer.setLongPressSpeed(holdSpeed)
            videoPlayer.setSeekStepSeconds(seekStepSeconds)
        }
        val headerMap = hashMapOf(
            "cookie" to UserSessionManager.cookie,
            "User-Agent" to ConfigKeyUtil.USER_AGENT
        )
        val address = videoInfo.videoUrl.ifEmpty {
            videoInfo.downloadUrl
        }
        val title = videoInfo.fileName
        subtitleSearchKeyword = title

        initGSYExoPlayerWithOkHttp(this.applicationContext)
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

        videoPlayer.apply {
            setUp(address, false, null, headerMap, title)
            setSeekOnStart(videoInfo.resumePositionMs)
            setOnPlaybackSpeedChanged { value -> changePlaybackSpeed(value) }
            findViewById<View>(R.id.episodeButton).setOnClickListener {
                showEpisodeDialog = true
                loadEpisodes()
            }
            findViewById<View>(R.id.previousEpisodeButton).setOnClickListener { playAdjacentEpisode(-1) }
            findViewById<View>(R.id.nextEpisodeButton).setOnClickListener { playAdjacentEpisode(1) }
            findViewById<View>(R.id.playerSettingsButton).setOnClickListener { showPlaybackSettings = true }
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
                if (showEpisodeDialog) {
                    EpisodePickerDialog(
                        episodes = episodeFiles, currentPickCode = videoInfo.pickCode,
                        loading = episodeLoading, switchingName = switchingEpisodeName,
                        error = episodeError, autoNext = autoPlayNext,
                        onSelect = { switchEpisode(it) }, onAutoNextChange = { changeAutoNext(it) },
                        onRetry = { loadEpisodes(force = true) }, onDismiss = { showEpisodeDialog = false }
                    )
                }
                if (showPlaybackSettings) {
                    PlaybackSettingsDialog(
                        speed = playbackSpeed, holdSpeed = holdSpeed, seekStep = seekStepSeconds,
                        autoNext = autoPlayNext, timerSeconds = sleepTimerSeconds,
                        stopAfterEpisode = stopAfterEpisode,
                        onSpeed = { changePlaybackSpeed(it) },
                        onHoldSpeed = {
                            holdSpeed = it
                            videoPlayer.setLongPressSpeed(it)
                            lifecycleScope.launch { DataStoreUtil.putDataSuspend(ConfigKeyUtil.PLAYER_HOLD_SPEED, it) }
                        },
                        onSeekStep = {
                            seekStepSeconds = it
                            videoPlayer.setSeekStepSeconds(it)
                            lifecycleScope.launch { DataStoreUtil.putDataSuspend(ConfigKeyUtil.PLAYER_SEEK_STEP, it) }
                        },
                        onAutoNext = { changeAutoNext(it) }, onTimer = { setSleepTimer(it) },
                        onStopAfterEpisode = { enabled ->
                            if (enabled) setSleepTimer(0)
                            stopAfterEpisode = enabled
                        },
                        onRestart = {
                            showPlaybackSettings = false
                            pausedBySleepTimer = false
                            videoPlayer.stopTemporarySpeed()
                            if (videoPlayer.currentState == GSYVideoView.CURRENT_STATE_AUTO_COMPLETE ||
                                videoPlayer.currentState == GSYVideoView.CURRENT_STATE_ERROR) {
                                videoPlayer.setSeekOnStart(0)
                                videoPlayer.startPlayLogic()
                            } else {
                                videoPlayer.gsyVideoManager.seekTo(0)
                                videoPlayer.onVideoResume()
                            }
                        },
                        onDismiss = { showPlaybackSettings = false }
                    )
                }
                if (showSubtitleDialog) {
                    SubtitlePickerDialog(
                        cloudSubtitles = cloudSubtitleCandidates,
                        onlineSubtitles = onlineSubtitleCandidates,
                        isSearchingOnline = isSearchingOnline,
                        searchError = searchError,
                        searchKeyword = subtitleSearchKeyword,
                        searchedKeyword = lastOnlineSearchKeyword.orEmpty(),
                        selectedSubtitleName = activeSubtitleName,
                        style = subtitleStyle,
                        delayMs = subtitleDelayMs,
                        browse = browseState,
                        onDismiss = {
                            showSubtitleDialog = false
                            browseState = browseState.copy(active = false)
                        },
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
                        onSearchKeywordChange = { subtitleSearchKeyword = it },
                        onSearchOnline = { searchOnlineSubtitles() },
                        onUseVideoNameSearch = { searchOnlineSubtitles(videoInfo.fileName) },
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
                        },
                        onBrowseStart = { startBrowse() },
                        onBrowseOpenFolder = { cid -> openBrowseFolder(cid) },
                        onBrowseUp = { browseUp() },
                        onBrowseExit = { browseState = browseState.copy(active = false) },
                        onPickLocalSubtitle = {
                            showSubtitleDialog = false
                            browseState = browseState.copy(active = false)
                            localSubtitlePicker.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        }

        // 启动时自动匹配云盘同目录字幕（在播放器启动后执行）
        autoMatchCloudSubtitle()

        videoPlayer.setVideoAllCallBack(gSYErrorCallBack)
        videoPlayer.startPlayLogic()
        loadEpisodes()
        updateEpisodeButtons()

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

        onBackPressedDispatcher.addCallback(this) {
            if (!videoPlayer.unlockControlsIfLocked()) back()
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
        if (::videoPlayer.isInitialized) {
            resumeOnForeground = videoPlayer.isActivelyPlaying
            videoPlayer.stopTemporarySpeed()
            videoPlayer.onVideoPause()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (sleepDeadlineMs > 0 && SystemClock.elapsedRealtime() >= sleepDeadlineMs) {
            pausedBySleepTimer = true
            resumeOnForeground = false
        }
        if (::videoPlayer.isInitialized && resumeOnForeground && !pausedBySleepTimer) videoPlayer.onVideoResume()
        resumeOnForeground = false
    }

    override fun onDestroy() {
        if (::videoPlayer.isInitialized) videoPlayer.stopTemporarySpeed()
        GSYVideoManager.releaseAllVideos()
        super.onDestroy()
    }


    private fun back(nav: String = "", toast: String = "", resultCode: Int = RESULT_OK) {
        if (nav.isEmpty() && videoPlayer.unlockControlsIfLocked()) return
        val currentDuration = (currentPlaybackPositionMs() / 1000).toInt()
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
        override fun onPrepared(url: String?, vararg objects: Any?) {
            videoDurationMs = videoPlayer.duration
            videoPlayer.setPlaybackSpeed(playbackSpeed)
        }

        override fun onClickResume(url: String?, vararg objects: Any?) { pausedBySleepTimer = false }
        override fun onClickResumeFullscreen(url: String?, vararg objects: Any?) { pausedBySleepTimer = false }

        override fun onAutoComplete(url: String?, vararg objects: Any?) {
            val completedPickCode = videoInfo.pickCode
            savePlaybackProgress(completedPickCode, (videoDurationMs / 1000).toInt())
            if (stopAfterEpisode) {
                stopAfterEpisode = false
                resumeOnForeground = false
                App.instance.toast("本集播放结束，已停止连播")
                return
            }
            if (autoPlayNext && !pausedBySleepTimer) videoPlayer.post {
                if (!isFinishing && !pausedBySleepTimer && videoInfo.pickCode == completedPickCode &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) playAdjacentEpisode(1, automatic = true)
            }
        }

        override fun onPlayError(url: String?, vararg objects: Any?) {
            val playerManager = videoPlayer.gsyVideoManager.player as? Exo2PlayerManager
            val exoPlayer = playerManager?.mediaPlayer as? ExoPlayer
            val exoError = exoPlayer?.playerError
            if (isHandledException(exoError)) {
                return
            }

            super.onPlayError(url, objects)
            val errorStatus =
                if (objects.getOrNull(2) is Int && videoPlayer.gsyVideoManager.player is Exo2PlayerManager) {
                    val code = (objects[2] as Int)
                    playbackErrorMessageMap.getOrDefault(code, "")
                        .ifEmpty { "发生未记录的错误 (错误码: $code)" }
                } else {
                    "UNKNOWN_ERROR"
                }
            XLog.e("$title 播放失败 $errorStatus")
            Toast.makeText(baseContext, errorStatus, Toast.LENGTH_SHORT).show()
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
        if (isReloadingVideo) return
        isReloadingVideo = true
        val requestedPickCode = videoInfo.pickCode
        val session = subtitleSession
        App.instance.toast("视频地址错误！正在重新获取新链接")
        lifecycleScope.launch {
            try {
                val video = FileRepository.getInstance().video(requestedPickCode)
                if (session != subtitleSession || videoInfo.pickCode != requestedPickCode ||
                    switchingEpisodeName.isNotEmpty()) return@launch
                val address = video.downloadUrl.ifEmpty { video.videoUrl }
                check(address.isNotBlank()) { "重新获取的视频地址为空" }
                videoPlayer.stopTemporarySpeed()
                videoPlayer.setSeekOnStart(currentPlaybackPositionMs())
                videoPlayer.playNext(address, videoInfo.fileName)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                XLog.e("重新获取视频链接失败", error)
            } finally {
                if (session == subtitleSession) isReloadingVideo = false
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

    // ======================= 播放列表与播放设置 =======================

    private fun changePlaybackSpeed(value: Float) {
        playbackSpeed = value
        videoPlayer.setPlaybackSpeed(value)
        lifecycleScope.launch { DataStoreUtil.putDataSuspend(ConfigKeyUtil.PLAYER_SPEED, value) }
    }

    private fun changeAutoNext(value: Boolean) {
        autoPlayNext = value
        lifecycleScope.launch { DataStoreUtil.putDataSuspend(ConfigKeyUtil.PLAYER_AUTO_NEXT, value) }
    }

    private fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerSeconds = 0
        sleepDeadlineMs = 0
        pausedBySleepTimer = false
        stopAfterEpisode = false
        if (minutes <= 0) return
        val deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepDeadlineMs = deadline
        sleepTimerJob = lifecycleScope.launch {
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                sleepTimerSeconds = (remaining + 999) / 1000
                delay(1000)
            }
            sleepTimerSeconds = 0
            sleepDeadlineMs = 0
            pausedBySleepTimer = true
            resumeOnForeground = false
            videoPlayer.stopTemporarySpeed()
            videoPlayer.onVideoPause()
            App.instance.toast("定时结束，播放已暂停")
        }
    }

    private fun loadEpisodes(force: Boolean = false) {
        val cid = videoParentCid
        if (cid.isBlank()) {
            episodeError = "缺少视频所在目录信息，请从网盘文件列表打开视频"
            return
        }
        if (!force && (episodeLoading || (episodeDirectory == cid && episodeFiles.isNotEmpty()))) return
        episodeLoadJob?.cancel()
        episodeLoading = true
        episodeError = ""
        val current = videoInfo
        episodeLoadJob = lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    val collected = mutableListOf<FileBean>()
                    val seen = mutableSetOf<String>()
                    var offset = 0
                    do {
                        ensureActive()
                        val page = FileRepository.getInstance().getFiles(
                            cid = cid, showDir = 0, limit = 1150, offset = offset
                        )
                        if (page.fileBeanList.isEmpty()) break
                        val fresh = page.fileBeanList.filter { seen.add(it.fileId.ifEmpty { it.categoryId }) }
                        check(fresh.isNotEmpty()) { "目录分页返回重复数据，请重试" }
                        collected.addAll(fresh)
                        offset += page.fileBeanList.size
                    } while (offset < page.count)
                    if (collected.none { it.pickCode == current.pickCode }) {
                        collected.add(FileBean(
                            fileId = current.fileId.ifEmpty { "current-${current.pickCode}" },
                            pickCode = current.pickCode, parentId = cid, name = current.fileName, isVideo = 1
                        ))
                    }
                    PlaybackUtil.episodes(collected)
                }
                if (videoParentCid == cid) {
                    episodeFiles = files
                    episodeDirectory = cid
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                episodeError = "选集列表加载失败，请重试"
                XLog.e("加载选集失败", error)
            } finally {
                if (isActive) {
                    episodeLoading = false
                    updateEpisodeButtons()
                }
            }
        }
    }

    private fun updateEpisodeButtons() {
        val current = episodeFiles.indexOfFirst { it.pickCode == videoInfo.pickCode }
        for ((id, direction) in listOf(R.id.previousEpisodeButton to -1, R.id.nextEpisodeButton to 1)) {
            val enabled = switchingEpisodeName.isEmpty() &&
                PlaybackUtil.adjacentIndex(episodeFiles.size, current, direction) != null
            videoPlayer.findViewById<View>(id).apply {
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.35f
            }
        }
    }

    private fun playAdjacentEpisode(direction: Int, automatic: Boolean = false) {
        if (switchingEpisodeName.isNotEmpty()) return
        val requestedFrom = videoInfo.pickCode
        lifecycleScope.launch {
            if (episodeFiles.isEmpty()) {
                loadEpisodes()
                episodeLoadJob?.join()
            }
            if (videoInfo.pickCode != requestedFrom ||
                (automatic && (!autoPlayNext || pausedBySleepTimer || stopAfterEpisode))) return@launch
            val current = episodeFiles.indexOfFirst { it.pickCode == requestedFrom }
            val next = PlaybackUtil.adjacentIndex(episodeFiles.size, current, direction)
            if (next == null) {
                App.instance.toast(if (direction > 0) "已是最后一集" else "已是第一集")
                return@launch
            }
            switchEpisode(episodeFiles[next], automatic)
        }
    }

    private fun currentPlaybackPositionMs(): Long =
        if (videoPlayer.currentState == GSYVideoView.CURRENT_STATE_AUTO_COMPLETE) videoDurationMs
        else videoPlayer.currentPositionWhenPlaying

    private fun savePlaybackProgress(pickCode: String, seconds: Int) {
        if (pickCode.isBlank()) return
        episodeFiles = episodeFiles.map {
            if (it.pickCode == pickCode) it.copy(currentPlayTime = seconds.coerceAtLeast(0)) else it
        }
        lifecycleScope.launch {
            try {
                FileRepository.getInstance().videoHistory(mapOf(
                    "op" to "update", "pick_code" to pickCode, "time" to seconds.coerceAtLeast(0).toString(),
                    "category" to "1", "format" to "json"
                ))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                XLog.e("保存视频进度失败", error)
            }
        }
    }

    private fun switchEpisode(file: FileBean, automatic: Boolean = false) {
        if (file.pickCode == videoInfo.pickCode) { showEpisodeDialog = false; return }
        if (switchingEpisodeName.isNotEmpty()) return
        episodeSwitchJob?.cancel()
        switchingEpisodeName = file.name
        if (!automatic) pausedBySleepTimer = false
        updateEpisodeButtons()
        val previous = videoInfo
        episodeSwitchJob = lifecycleScope.launch {
            try {
                val next = if (videoLinkMode) {
                    FileRepository.getInstance().video(file.pickCode).copy(
                        pickCode = file.pickCode, fileName = file.name,
                        parentId = file.parentId.ifEmpty { previous.parentId },
                        fileId = file.fileId, index = -1, isAutoRotate = previous.isAutoRotate
                    )
                } else {
                    VideoInfoBean(
                        pickCode = file.pickCode, fileName = file.name,
                        parentId = file.parentId.ifEmpty { previous.parentId }, fileId = file.fileId,
                        width = previous.width, height = previous.height, isAutoRotate = previous.isAutoRotate,
                        videoUrl = "http://115.com/api/video/m3u8/${file.pickCode}.m3u8"
                    )
                }
                val address = next.videoUrl.ifEmpty { next.downloadUrl }
                check(address.isNotBlank()) { "视频地址为空" }
                // 自动完成回调已保存完整时长，不要再用播放器归零后的进度覆盖它。
                if (!automatic) savePlaybackProgress(previous.pickCode, (currentPlaybackPositionMs() / 1000).toInt())
                videoPlayer.stopTemporarySpeed()
                videoPlayer.unlockControlsIfLocked()
                resetSubtitlesForEpisode(next.fileName)
                videoPlayer.onVideoReset()
                videoInfo = next
                videoDurationMs = 0
                check(videoPlayer.setUp(address, false, null, hashMapOf(
                    "cookie" to UserSessionManager.cookie, "User-Agent" to ConfigKeyUtil.USER_AGENT
                ), next.fileName)) { "播放器暂时无法切换，请重试" }
                val resume = if (automatic) 0L else PlaybackUtil.resumePosition(
                    file.currentPlayTime * 1000L, (file.playLong * 1000).toLong()
                )
                videoPlayer.setSeekOnStart(resume)
                videoPlayer.setPlaybackSpeed(playbackSpeed)
                videoPlayer.startPlayLogic()
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    videoPlayer.onVideoPause()
                    resumeOnForeground = true
                }
                autoMatchCloudSubtitle()
                showEpisodeDialog = false
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                XLog.e("切换选集失败: ${file.name}", error)
                App.instance.toast("打开选集失败，请重试或选择其它视频")
            } finally {
                if (isActive) {
                    switchingEpisodeName = ""
                    updateEpisodeButtons()
                }
            }
        }
    }

    private fun resetSubtitlesForEpisode(title: String) {
        subtitleSession++
        subtitleSelectionId++
        isReloadingVideo = false
        onlineSearchJob?.cancel()
        isSearchingOnline = false
        isSearchingCloud = false
        cloudSubtitleCandidates.clear()
        onlineSubtitleCandidates.clear()
        searchError = ""
        subtitleSearchKeyword = title
        lastOnlineSearchKeyword = null
        activeSubtitleName = null
        subtitleDelayMs = 0
        browseState = SubtitleBrowseState()
        showSubtitleDialog = false
        videoPlayer.setSubtitleSource(null)
        applySubtitleDelay(0)
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
        // 重开弹窗保留手动关键词和结果，不再悄悄恢复按视频名搜索。
        if (lastOnlineSearchKeyword == null) searchOnlineSubtitles()
    }

    /**
     * 自动匹配同目录字幕并默认加载
     */
    private fun autoMatchCloudSubtitle() {
        if (videoParentCid.isEmpty()) return
        val session = subtitleSession
        val selection = subtitleSelectionId
        val info = videoInfo
        lifecycleScope.launch {
            // 直接等待搜索完成后再取第一个候选
            val result = subtitleRepository.searchCloudSubtitles(
                info.parentId, info.fileName
            )
            if (session != subtitleSession) return@launch
            cloudSubtitleCandidates.clear()
            cloudSubtitleCandidates.addAll(result)
            // 自动挂载第一个匹配的同目录字幕
            val first = result.firstOrNull() ?: return@launch
            if (selection != subtitleSelectionId || activeSubtitleName != null) return@launch
            loadCloudSubtitle(first, toastOnFail = false)
        }
    }

    private fun searchCloudSubtitles() {
        // 避免并发搜索：进行中直接跳过
        if (isSearchingCloud) return
        isSearchingCloud = true
        val session = subtitleSession
        val info = videoInfo
        lifecycleScope.launch {
            val result = subtitleRepository.searchCloudSubtitles(
                info.parentId, info.fileName
            )
            if (session != subtitleSession) return@launch
            cloudSubtitleCandidates.clear()
            cloudSubtitleCandidates.addAll(result)
            isSearchingCloud = false
        }
    }

    private fun searchOnlineSubtitles(keyword: String = subtitleSearchKeyword) {
        val query = keyword.trim()
        if (query.isEmpty()) {
            searchError = "请输入字幕搜索关键词"
            return
        }
        subtitleSearchKeyword = query
        if (isSearchingOnline && lastOnlineSearchKeyword == query) return

        // 允许搜索过程中换关键词；被取消的旧请求不能覆盖新请求的结果。
        onlineSearchJob?.cancel()
        lastOnlineSearchKeyword = query
        isSearchingOnline = true
        searchError = ""
        onlineSubtitleCandidates.clear()
        onlineSearchJob = lifecycleScope.launch {
            try {
                subtitleRepository.searchOnlineSubtitles(query)
                    .onSuccess { onlineSubtitleCandidates.addAll(it) }
                    .onFailure { searchError = "在线字幕搜索失败，请检查网络后重试" }
            } finally {
                if (isActive) isSearchingOnline = false
            }
        }
    }

    /**
     * 加载 115 云盘同目录字幕：下载 -> 转换 -> 挂载
     */
    private fun loadCloudSubtitle(fileBean: FileBean, toastOnFail: Boolean = true) {
        val session = subtitleSession
        val selection = ++subtitleSelectionId
        lifecycleScope.launch {
            val localFile = subtitleRepository.downloadCloudSubtitle(fileBean, subtitleCacheDir)
            if (session != subtitleSession || selection != subtitleSelectionId) return@launch
            val mounted = localFile != null && mountSubtitleFile(localFile, session, selection)
            if (session != subtitleSession || selection != subtitleSelectionId) return@launch
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
        val session = subtitleSession
        val selection = ++subtitleSelectionId
        lifecycleScope.launch {
            val localFile = subtitleRepository.downloadSubtitle(
                bean.url, bean.name, subtitleCacheDir, fileExtension = bean.ext
            )
            if (session != subtitleSession || selection != subtitleSelectionId) return@launch
            val mounted = localFile != null && mountSubtitleFile(localFile, session, selection)
            if (session != subtitleSession || selection != subtitleSelectionId) return@launch
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
    private suspend fun mountSubtitleFile(file: File, session: Int, selection: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val playbackFile = SubtitleConvertUtil.prepareForPlayback(file, subtitleCacheDir)
                    ?: return@withContext false
                val source = GSYSubtitleSource.Builder(
                    android.net.Uri.fromFile(playbackFile).toString()
                ).setLabel(file.name).build()
                // setSubtitleSource 内部仅做 UI 操作，切回主线程
                withContext(Dispatchers.Main) {
                    if (session != subtitleSession || selection != subtitleSelectionId) {
                        throw CancellationException("字幕请求已被新的选集或选择替代")
                    }
                    videoPlayer.setSubtitleSource(source)
                    // 字幕控制器可能被重建，重新套用样式与延迟
                    applySubtitleStyle(subtitleStyle)
                    applySubtitleDelay(subtitleDelayMs)
                }
                true
            }.onFailure {
                if (it is CancellationException) throw it
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

    // ---------------- 网盘目录浏览 ----------------

    private fun startBrowse() {
        openBrowseFolder(videoParentCid.ifEmpty { "0" })
    }

    private fun openBrowseFolder(cid: String) {
        val session = subtitleSession
        browseState = browseState.copy(active = true, loading = true, cid = cid, error = "")
        lifecycleScope.launch {
            runCatching { subtitleRepository.listFolder(cid) }
                .onSuccess { listing ->
                    if (session != subtitleSession || !browseState.active || browseState.cid != cid) return@onSuccess
                    val matched = listing.subtitles
                        .filter { subtitleRepository.matchesVideo(videoInfo.fileName, it.name) }
                        .map { it.fileId }
                        .toSet()
                    browseState = SubtitleBrowseState(
                        active = true,
                        loading = false,
                        cid = cid,
                        path = listing.path,
                        folders = listing.folders,
                        subtitles = listing.subtitles,
                        matched = matched
                    )
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (session != subtitleSession || !browseState.active || browseState.cid != cid) return@onFailure
                    XLog.e("浏览网盘目录失败: $cid", it)
                    browseState = browseState.copy(
                        loading = false,
                        error = "目录加载失败: ${it.localizedMessage ?: "未知错误"}"
                    )
                }
        }
    }

    /** 面包屑最后一项为当前目录，倒数第二项即上级 */
    private fun browseUp() {
        val path = browseState.path
        if (path.size >= 2) openBrowseFolder(path[path.size - 2].cid)
    }

    // ---------------- 本机字幕 ----------------

    private fun loadLocalSubtitle(uri: Uri) {
        val session = subtitleSession
        val selection = ++subtitleSelectionId
        lifecycleScope.launch {
            val file = subtitleRepository.importLocalSubtitle(applicationContext, uri, subtitleCacheDir)
            if (session != subtitleSession || selection != subtitleSelectionId) return@launch
            if (file == null) {
                App.instance.toast("无法读取所选字幕（仅支持 srt/ass/ssa/vtt）")
                return@launch
            }
            if (mountSubtitleFile(file, session, selection)) {
                activeSubtitleName = file.name
                App.instance.toast("字幕已挂载: ${file.name}")
            } else {
                App.instance.toast("字幕解析失败")
            }
        }
    }

    private fun clearSubtitle() {
        subtitleSelectionId++
        runCatching { videoPlayer.setSubtitleSource(null) }
        activeSubtitleName = null
    }
}