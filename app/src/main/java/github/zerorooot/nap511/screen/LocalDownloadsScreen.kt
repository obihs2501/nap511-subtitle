package github.zerorooot.nap511.screen

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import github.zerorooot.nap511.bean.LocalDownloadItem
import github.zerorooot.nap511.bean.LocalDownloadStatus
import github.zerorooot.nap511.repository.DownloadRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.DownloadUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Only Android 8/9 need the legacy storage permission for public Downloads. */
@Composable
internal fun rememberDownloadPermission(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted) action?.invoke() else App.instance.toast("未授予存储权限，下载未开始")
    }
    return { action ->
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pending = action
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else action()
    }
}

@Composable
fun LocalDownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val downloads = remember { DownloadRepository.instance }
    val withPermission = rememberDownloadPermission()
    var items by remember { mutableStateOf<List<LocalDownloadItem>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmRemoval by remember { mutableStateOf<LocalDownloadItem?>(null) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try {
                    items = downloads.list()
                    error = ""
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = "无法读取系统下载任务，请检查系统下载管理器是否被停用"
                } finally { loading = false }
                delay(1500)
            }
        }
    }

    fun perform(id: Long, action: suspend () -> Unit) {
        if (id in busy) return
        busy = busy + id
        scope.launch {
            try {
                action()
                items = downloads.list()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                App.instance.toast(if (failure is ActivityNotFoundException) "没有可打开此文件的应用"
                    else failure.message ?: "下载操作失败，请重试")
            } finally { busy = busy - id }
        }
    }

    Scaffold(topBar = {
        BaseTopAppBar(title = { Text("本机下载") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("保存位置：Download/nap511\n由 Android 内置服务下载，无需 aria2；退出应用后继续。使用当前网络（可能产生移动流量）。",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            if (loading) CircularProgressIndicator()
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            if (!loading && error.isEmpty() && items.isEmpty()) Text("暂无下载。请在文件菜单中选择“下载到本机”。")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.record.id }) { item ->
                    val enabled = item.record.id !in busy
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.record.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.reason, style = MaterialTheme.typography.bodySmall,
                                color = if (item.status == LocalDownloadStatus.FAILED) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${DownloadUtil.size(item.downloadedBytes)} / ${DownloadUtil.size(item.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall)
                            if (item.active) {
                                val fraction = DownloadUtil.progress(item.downloadedBytes, item.totalBytes)
                                if (fraction == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (item.status == LocalDownloadStatus.COMPLETE) {
                                    TextButton(enabled = enabled, onClick = {
                                        perform(item.record.id) { context.startActivity(downloads.openIntent(item.record.id)) }
                                    }) { Text("打开") }
                                } else if (!item.active) {
                                    TextButton(enabled = enabled, onClick = {
                                        withPermission { perform(item.record.id) { downloads.retry(item.record.id) } }
                                    }) { Text("重新下载") }
                                }
                                TextButton(enabled = enabled, onClick = { confirmRemoval = item }) {
                                    Text(if (item.active) "取消下载" else "移除记录")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmRemoval?.let { item ->
        AlertDialog(onDismissRequest = { confirmRemoval = null },
            title = { Text(if (item.active) "取消下载？" else "移除记录？") },
            text = { Text(if (item.status == LocalDownloadStatus.COMPLETE)
                "只移除列表记录，已下载的文件会保留在 Download/nap511。"
                else "将移除此任务并删除未完成的数据；之后可从网盘重新下载。") },
            confirmButton = { TextButton(onClick = {
                confirmRemoval = null
                perform(item.record.id) { downloads.removeRecord(item.record.id) }
            }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { confirmRemoval = null }) { Text("返回") } }
        )
    }
}
