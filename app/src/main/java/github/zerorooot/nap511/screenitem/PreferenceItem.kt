package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.dialog.BaseDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

// 分组标题组件（MIUI 风格小标题）
@Composable
fun PreferenceCategoryHeader(title: String) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 12.dp)
    )
}

// 1. 普通点击项（如：重启应用、离线下载验证）
@Composable
fun PreferenceItem(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = if (enabled) onClick else null,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(
            top = 14.dp, bottom = 14.dp, start = 20.dp, end = 16.dp
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.disabledOnSurface,
                )
                if (summary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else MiuixTheme.colorScheme.disabledOnSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

// 2. 开关选项（如：屏幕自动旋转、日志记录）
@Composable
fun SwitchPreferenceItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(
            top = 14.dp, bottom = 14.dp, start = 20.dp, end = 16.dp
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.disabledOnSurface,
                )
                if (summary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else MiuixTheme.colorScheme.disabledOnSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

// 3. 弹窗输入选项（如：修改 uid、password、aria2地址等）
@Composable
fun EditTextPreferenceItem(
    title: String,
    summary: String,
    value: String,
    label: String = title, // 默认使用 title 作为 输入框 label
    isNumber: Boolean = false,
    enabled: Boolean = true,
    onValueSave: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = if (enabled) { { showDialog = true } } else null,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(
            top = 14.dp, bottom = 14.dp, start = 20.dp, end = 16.dp
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.disabledOnSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else MiuixTheme.colorScheme.disabledOnSurface,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }

    if (showDialog) {
        BaseDialog(
            title = title,
            label = label,
            context = value,
            keyboardOptions = if (isNumber) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions.Default
            },
            enter = { result ->
                showDialog = false // 无论确认还是取消，都关闭 Dialog
                if (result != null) {
                    onValueSave(result) // 仅在用户确认输入时保存
                }
            }
        )
    }
}

// 4. 列表选择项（如：浮动按钮位置、主题模式）
@Composable
fun ListPreferenceItem(
    title: String,
    value: String,
    entries: Array<String>,
    entryValues: Array<String>,
    enabled: Boolean = true,
    onValueSave: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = if (enabled) { { showDialog = true } } else null,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(
            top = 14.dp, bottom = 14.dp, start = 20.dp, end = 16.dp
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.disabledOnSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else MiuixTheme.colorScheme.disabledOnSurface,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }

    if (showDialog && enabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { androidx.compose.material3.Text(text = title) },
            text = {
                Column {
                    entries.forEachIndexed { index, entry ->
                        val entryValue = entryValues.getOrNull(index) ?: entry
                        val selected = entryValue == value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onValueSave(entryValue)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.Text(text = entry)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    androidx.compose.material3.Text(text = "取消")
                }
            }
        )
    }
}
