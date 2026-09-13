package github.zerorooot.nap511.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.util.PlaybackUtil

@Composable
fun EpisodePickerDialog(
    episodes: List<FileBean>,
    currentPickCode: String,
    loading: Boolean,
    switchingName: String,
    error: String,
    autoNext: Boolean,
    onSelect: (FileBean) -> Unit,
    onAutoNextChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    val visible = remember(episodes, keyword) {
        episodes.filter { it.name.contains(keyword.trim(), ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(visible, currentPickCode) {
        val current = visible.indexOfFirst { it.pickCode == currentPickCode }
        listState.scrollToItem(current.coerceAtLeast(0))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同目录选集 · ${episodes.size} 个视频") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("筛选片名 / 集数") }
                )
                SettingSwitch("播完自动播放下一集", autoNext, onAutoNextChange)
                if (loading) CircularProgressIndicator()
                if (switchingName.isNotEmpty()) Text("正在打开：$switchingName")
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, enabled = !loading) { Text("重新加载目录") }
                }
                if (!loading && visible.isEmpty() && error.isEmpty()) Text("没有匹配的视频")
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visible, key = { it.pickCode }) { file ->
                        val selected = file.pickCode == currentPickCode
                        Column(
                            Modifier.fillMaxWidth()
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = switchingName.isEmpty()) { onSelect(file) }
                                .padding(12.dp)
                        ) {
                            Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (selected) Text("正在播放", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackSettingsDialog(
    speed: Float,
    holdSpeed: Float,
    seekStep: Long,
    autoNext: Boolean,
    timerSeconds: Long,
    stopAfterEpisode: Boolean,
    onSpeed: (Float) -> Unit,
    onHoldSpeed: (Float) -> Unit,
    onSeekStep: (Long) -> Unit,
    onAutoNext: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onStopAfterEpisode: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("播放倍速", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaybackUtil.SPEEDS.forEach { value ->
                        FilterChip(selected = speed == value, onClick = { onSpeed(value) },
                            label = { Text(PlaybackUtil.speedLabel(value)) })
                    }
                }
                Text("长按临时倍速 · 松手恢复原速", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaybackUtil.HOLD_SPEEDS.forEach { value ->
                        FilterChip(selected = holdSpeed == value, onClick = { onHoldSpeed(value) },
                            label = { Text(PlaybackUtil.speedLabel(value)) })
                    }
                }
                Text("双击左右侧快退 / 快进", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaybackUtil.SEEK_STEPS.forEach { value ->
                        FilterChip(selected = seekStep == value, onClick = { onSeekStep(value) },
                            label = { Text("${value} 秒") })
                    }
                }
                SettingSwitch("播完自动播放下一集", autoNext, onAutoNext)
                Text("定时暂停", style = MaterialTheme.typography.titleSmall)
                Text(if (timerSeconds > 0) "剩余 ${PlaybackUtil.timerLabel(timerSeconds)}"
                    else "未启用定时暂停", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60).forEach { minutes ->
                        TextButton(onClick = { onTimer(minutes) }) { Text("${minutes} 分钟") }
                    }
                    TextButton(onClick = { onTimer(0) }) { Text("取消定时") }
                }
                SettingSwitch("本集结束后停止", stopAfterEpisode, onStopAfterEpisode)
                Text("本集停止优先于自动连播。双击画面中间暂停 / 继续，锁定后按返回键先解锁。",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRestart) { Text("从头播放本集") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
