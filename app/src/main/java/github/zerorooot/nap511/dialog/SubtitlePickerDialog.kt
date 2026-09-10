package github.zerorooot.nap511.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleStyleState
import github.zerorooot.nap511.bean.XunleiSubtitleBean
import github.zerorooot.nap511.util.SubtitleStyleUtil
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 字幕弹窗：
 * - 「字幕源」：云盘同目录字幕 + 迅雷在线字幕
 * - 「样式与同步」：延迟、字号、颜色、字体、粗体、底色
 *
 * @param onStyleChange (新样式, 是否持久化)。拖动滑块过程中只预览不落盘，松手后落盘
 * @param onDelayChange 字幕延迟毫秒，正值=字幕推后显示，负值=提前
 */
@Composable
fun SubtitlePickerDialog(
    cloudSubtitles: List<FileBean>,
    onlineSubtitles: List<XunleiSubtitleBean>,
    isSearchingOnline: Boolean,
    searchError: String,
    selectedSubtitleName: String?,
    style: SubtitleStyleState,
    delayMs: Long,
    onDismiss: () -> Unit,
    onSelectCloudSubtitle: (FileBean) -> Unit,
    onSelectOnlineSubtitle: (XunleiSubtitleBean) -> Unit,
    onClearSubtitle: () -> Unit,
    onRetrySearch: () -> Unit,
    onStyleChange: (SubtitleStyleState, Boolean) -> Unit,
    onDelayChange: (Long) -> Unit
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "字幕",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("字幕源") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("样式与同步") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (tabIndex == 0) {
                    SubtitleSourceList(
                        cloudSubtitles = cloudSubtitles,
                        onlineSubtitles = onlineSubtitles,
                        isSearchingOnline = isSearchingOnline,
                        searchError = searchError,
                        selectedSubtitleName = selectedSubtitleName,
                        onSelectCloudSubtitle = onSelectCloudSubtitle,
                        onSelectOnlineSubtitle = onSelectOnlineSubtitle,
                        onClearSubtitle = onClearSubtitle,
                        onRetrySearch = onRetrySearch
                    )
                } else {
                    SubtitleStylePanel(
                        style = style,
                        delayMs = delayMs,
                        onStyleChange = onStyleChange,
                        onDelayChange = onDelayChange
                    )
                }
            }
        }
    }
}

// ============================================================
// 页签 1：字幕源
// ============================================================
@Composable
private fun SubtitleSourceList(
    cloudSubtitles: List<FileBean>,
    onlineSubtitles: List<XunleiSubtitleBean>,
    isSearchingOnline: Boolean,
    searchError: String,
    selectedSubtitleName: String?,
    onSelectCloudSubtitle: (FileBean) -> Unit,
    onSelectOnlineSubtitle: (XunleiSubtitleBean) -> Unit,
    onClearSubtitle: () -> Unit,
    onRetrySearch: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 当前生效字幕
        if (selectedSubtitleName != null) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "当前: $selectedSubtitleName",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onClearSubtitle) {
                        Text("移除", fontSize = 12.sp)
                    }
                }
            }
        }

        // 云盘同目录字幕
        if (cloudSubtitles.isNotEmpty()) {
            item {
                SectionHeader(icon = {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }, title = "同目录字幕")
            }
            items(cloudSubtitles, key = { "cloud_${it.fileId}" }) { fileBean ->
                SubtitleItem(
                    title = fileBean.name,
                    subtitle = "115 云盘文件",
                    onClick = { onSelectCloudSubtitle(fileBean) }
                )
            }
        }

        // 在线字幕
        item {
            SectionHeader(icon = {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }, title = "在线字幕（迅雷）")
        }

        if (isSearchingOnline) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        } else if (searchError.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        searchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetrySearch) {
                        Text("重试")
                    }
                }
            }
        } else if (onlineSubtitles.isEmpty()) {
            item {
                Text(
                    "未找到在线字幕",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(onlineSubtitles, key = { "online_${it.gcid}_${it.url}" }) { bean ->
                SubtitleItem(
                    title = bean.name,
                    subtitle = buildString {
                        append(bean.ext.uppercase())
                        if (bean.extraName.isNotEmpty()) {
                            append(" · ")
                            append(bean.extraName)
                        }
                    },
                    onClick = { onSelectOnlineSubtitle(bean) }
                )
            }
        }
    }
}

// ============================================================
// 页签 2：样式与同步
// ============================================================
@Composable
private fun SubtitleStylePanel(
    style: SubtitleStyleState,
    delayMs: Long,
    onStyleChange: (SubtitleStyleState, Boolean) -> Unit,
    onDelayChange: (Long) -> Unit
) {
    // 滑块本地状态：拖动中只预览，松手后落盘
    var sliderSize by remember(style.textSizeSp) {
        mutableFloatStateOf(style.textSizeSp.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- 同步 / 延迟 ----
        PanelTitle("同步 / 延迟")
        Text(
            text = delayText(delayMs),
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DelayButton("−0.5s") { onDelayChange(delayMs - 500) }
            DelayButton("−0.1s") { onDelayChange(delayMs - 100) }
            DelayButton("归零") { onDelayChange(0L) }
            DelayButton("+0.1s") { onDelayChange(delayMs + 100) }
            DelayButton("+0.5s") { onDelayChange(delayMs + 500) }
        }
        Text(
            "正值：字幕推后显示；负值：字幕提前显示。仅对本次播放生效。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- 字号 ----
        PanelTitle("字号  ${sliderSize.roundToInt()} sp")
        Slider(
            value = sliderSize,
            onValueChange = { v ->
                sliderSize = v
                onStyleChange(style.copy(textSizeSp = v.roundToInt()), false)
            },
            valueRange = SubtitleStyleUtil.MIN_SIZE.toFloat()..SubtitleStyleUtil.MAX_SIZE.toFloat(),
            onValueChangeFinished = {
                onStyleChange(style.copy(textSizeSp = sliderSize.roundToInt()), true)
            }
        )

        // ---- 颜色 ----
        PanelTitle("颜色")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SubtitleStyleUtil.COLOR_PRESETS.forEach { (_, argb) ->
                val selected = argb == style.textColor
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onStyleChange(style.copy(textColor = argb), true) }
                )
            }
        }

        // ---- 字体 ----
        PanelTitle("字体")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleStyleUtil.FONT_PRESETS.forEach { (key, name) ->
                FilterChip(
                    selected = key == style.fontKey,
                    onClick = { onStyleChange(style.copy(fontKey = key), true) },
                    label = { Text(name) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("粗体", modifier = Modifier.weight(1f))
            Switch(
                checked = style.bold,
                onCheckedChange = { onStyleChange(style.copy(bold = it), true) }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("半透明底色", modifier = Modifier.weight(1f))
            Switch(
                checked = style.background,
                onCheckedChange = { onStyleChange(style.copy(background = it), true) }
            )
        }

        // ---- 预览 ----
        PanelTitle("预览")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF202020), RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "字幕预览 Subtitle Preview",
                color = Color(style.textColor),
                fontSize = style.textSizeSp.sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = toComposeFontFamily(style.fontKey),
                textAlign = TextAlign.Center,
                modifier = if (style.background) {
                    Modifier
                        .background(Color(0x80000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                } else {
                    Modifier
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun DelayButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

private fun delayText(ms: Long): String = when {
    ms == 0L -> "当前：同步（0 s）"
    ms > 0 -> String.format(Locale.US, "当前：+%.1f s（字幕推后）", ms / 1000.0)
    else -> String.format(Locale.US, "当前：−%.1f s（字幕提前）", -ms / 1000.0)
}

private fun toComposeFontFamily(key: String): FontFamily = when (key) {
    "sans" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

@Composable
private fun SectionHeader(icon: @Composable () -> Unit, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SubtitleItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}
