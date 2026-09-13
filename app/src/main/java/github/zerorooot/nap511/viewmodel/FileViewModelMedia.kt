package github.zerorooot.nap511.viewmodel

import android.content.Intent
import android.content.res.Configuration
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.PlaybackUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FileViewModel 的扩展函数：媒体与文件查看相关
 */
internal fun FileViewModel.getImage(fileBeanList: List<FileBean>, indexOf: Int) {
    if (imageBeanCache.containsKey(currentCid) && imageBeanCache[currentCid]!!.containsKey(
            indexOf
        )
    ) {
        return
    }

    viewModelScope.launch {
        runCatching {
            val imageBean = fileRepository.image(
                fileBeanList[indexOf].pickCode, System.currentTimeMillis() / 1000
            ).imageBean

            val oldMap = imageBeanCache[currentCid] ?: hashMapOf()
            val newMap = HashMap(oldMap)
            newMap[indexOf] = imageBean

            imageBeanCache[currentCid] = newMap
        }.onFailureToastAndLog()
    }
}

internal fun FileViewModel.updateVideoFileBean(
    cid: String,
    index: Int,
    duration: Int,
    pickCode: String
) {
    viewModelScope.launch {
        // 换集后返回的不是最初点击的行；按 pickCode 定位，避免改错行或索引越界。
        val actualIndex = if (fileBeanList.getOrNull(index)?.pickCode == pickCode) index
            else fileBeanList.indexOfFirst { it.pickCode == pickCode }
        val fileBean = fileBeanList.getOrNull(actualIndex)
        if (fileBean != null && fileBean.isVideo == 1) {
            val playTime = if (fileBean.playLong <= 0.0) 0
                else ((duration.toFloat() / fileBean.playLong) * 100).roundToInt().coerceIn(0, 100)
            val createTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                (fileBean.createTime.toLongOrNull() ?: 0L) * 1000
            )
            fileBeanList[actualIndex] = fileBean.copy(
                createTimeString = "▶️ $playTime% $createTimeString", currentPlayTime = duration.coerceAtLeast(0)
            )
            if (!isSearchState) fileListCache[cid]?.fileBeanList = ArrayList(fileBeanList.toList())
        }
        if (pickCode.isBlank()) return@launch

        val map = mapOf(
            "op" to "update",
            "pick_code" to pickCode,
            "time" to duration.toString(),
            "category" to "1",
            "format" to "json"
        )
        runCatching {
            val videoHistory = fileRepository.videoHistory(map)
            if (!videoHistory.state) {
                App.instance.toast(videoHistory.error)
                XLog.e("更新视频时间失败！ $videoHistory")
            }else{
                XLog.d("更新视频时间 $videoHistory")
            }
        }.onFailureToastAndLog()
    }
}

internal fun FileViewModel.getVideoInfo(
    pickCode: String,
    fileBeanIndex: Int,
    fileName: String,
    parentCid: String = ""
) {
    viewModelScope.launch {
        val isAutoRotate = DataStoreUtil.getDataSuspend(ConfigKeyUtil.AUTO_ROTATE, false)
        val videoLinkMode = DataStoreUtil.getDataSuspend(ConfigKeyUtil.VIDEO_LINK_MODE, false)

        runCatching {
            val video = if (videoLinkMode) {
                fileRepository.video(pickCode)
                    .copy(index = fileBeanIndex, isAutoRotate = isAutoRotate)
            } else {
                val (width, height) = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    1080 to 1920
                } else {
                    1920 to 1080
                }
                VideoInfoBean(
                    width = width,
                    height = height,
                    index = fileBeanIndex,
                    fileName = fileName,
                    pickCode = pickCode,
                    parentId = parentCid,
                    videoUrl = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
                )
            }
            video.parentId = video.parentId.ifEmpty { parentCid }
            fileBeanList.getOrNull(fileBeanIndex)?.takeIf { it.pickCode == pickCode }?.let { file ->
                video.resumePositionMs = PlaybackUtil.resumePosition(
                    file.currentPlayTime * 1000L, (file.playLong * 1000).toLong()
                )
            }
            XLog.d("FileViewModel getVideoInfo $video")
            _launchVideoEvent.emit(video)
        }.onFailureToastAndLog()
        setRefreshingStatus(false)
    }
}

internal fun FileViewModel.downloadSmallFile(
    fileBean: FileBean,
    onSuccess: (ByteArray) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        var bytes = textFileCache[fileBean]
        if (bytes == null) {
            runCatching {
                val downloadInputStream =
                    fileRepository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                if (downloadInputStream == null) {
                    setRefreshingStatus(false)
                    App.instance.toast("文件加载失败！")
                    return@launch
                }
                bytes = downloadInputStream.readBytes()
                textFileCache[fileBean] = bytes
            }.onFailureToastAndLog()
        }
        if (bytes != null) {
            setRefreshingStatus(false)
            onSuccess(bytes)
        } else {
            setRefreshingStatus(false)
        }
    }
}

internal fun FileViewModel.downloadText(fileBean: FileBean, onNav: (Route) -> Unit) {
    downloadSmallFile(fileBean) { bytes ->
        textBodyByteArray = bytes
        onNav.invoke(Route.TxtReader)
    }
}

internal fun FileViewModel.downloadWeb(fileBean: FileBean, onNav: (Route) -> Unit) {
    downloadSmallFile(fileBean) { bytes ->
        webBodyByteArray = bytes
        onNav.invoke(Route.HtmlWebViewScreen)
    }
}

internal fun FileViewModel.startSendAria2Service(index: Int) {
    val fileBean = fileBeanList[index]
    if (fileBean.isFolder) {
        App.instance.toast("暂时无法下载文件夹")
        return
    }
    val intent = Intent(context, Sha1Service::class.java)
    intent.putExtra(ConfigKeyUtil.COMMAND, ConfigKeyUtil.SENT_TO_ARIA2)
    intent.putExtra("list", Gson().toJson(fileBean))
    context.startService(intent)
}
