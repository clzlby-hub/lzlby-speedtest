package org.librespeed.speedtest.ui.tv.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librespeed.speedtest.data.AppPreferences
import org.librespeed.speedtest.data.HistoryDatabase
import org.librespeed.speedtest.data.HistoryEntry
import org.librespeed.speedtest.ui.history.formatDate
import org.librespeed.speedtest.ui.tv.TvColors
import org.librespeed.speedtest.ui.tv.components.TvButton
import org.librespeed.speedtest.ui.tv.components.TvCard
import java.util.Locale

@Composable
fun TvHistoryScreen(
    onReTest: () -> Unit,
    primaryFocusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyDb = remember { HistoryDatabase(context.applicationContext) }
    val prefs = remember { AppPreferences(context.applicationContext) }
    val useMBytes by prefs.useMBytes.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val historyFocusRequester = primaryFocusRequester ?: remember { FocusRequester() }

    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun refreshHistory() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { historyDb.readAll() }
            entries = list
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    val unitLabel = if (useMBytes) "MB/s" else "Mbps"
    fun display(value: Double): Double = if (useMBytes) value / 8 else value

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 20.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "测速历史记录",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "本地存储的历次宽带吞吐量与延迟测试详情",
                    fontSize = 13.sp,
                    color = TvColors.TextSecondary
                )
            }

            if (entries.isNotEmpty()) {
                TvButton(
                    text = "清空历史",
                    icon = Icons.Filled.DeleteSweep,
                    isDestructive = true,
                    focusRequester = historyFocusRequester,
                    onUpPressed = onUpPressed,
                    onClick = { showClearDialog = true }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = TvColors.TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "暂无测速记录",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "在【测速】页面点击开始，即可运行第一次测速并自动保存记录",
                        color = TvColors.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    TvButton(
                        text = "立即前往测速",
                        icon = Icons.Filled.PlayArrow,
                        accentColor = TvColors.AppleGreen,
                        focusRequester = historyFocusRequester,
                        onUpPressed = onUpPressed,
                        onClick = onReTest
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { item ->
                    val isFirst = entries.firstOrNull()?.id == item.id
                    TvCard(
                        onClick = onReTest,
                        shape = RoundedCornerShape(16.dp),
                        onUpPressed = if (isFirst) onUpPressed else null,
                        backgroundColor = Color(0x28141A29),
                        borderColor = Color(0x22FFFFFF),
                        focusedBorderColor = Color.White
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.server,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = formatDate(item.date),
                                    fontSize = 12.sp,
                                    color = TvColors.TextMuted
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Download
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("下载", fontSize = 11.sp, color = TvColors.TextSecondary)
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", display(item.download))} $unitLabel",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TvColors.AppleGreen
                                    )
                                }

                                // Upload
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("上传", fontSize = 11.sp, color = TvColors.TextSecondary)
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", display(item.upload))} $unitLabel",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TvColors.ApplePurple
                                    )
                                }

                                // Ping
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("延迟", fontSize = 11.sp, color = TvColors.TextSecondary)
                                    Text(
                                        text = "${String.format(Locale.US, "%.0f", item.ping)} ms",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TvColors.AppleOrange
                                    )
                                }

                                if (isFocused) {
                                    Text(
                                        text = "按 OK 再次测速 ➔",
                                        color = TvColors.AppleCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史记录", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除所有保存的测速历史吗？该操作不可撤销。", color = TvColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { historyDb.clear() }
                        refreshHistory()
                        showClearDialog = false
                        Toast.makeText(context, "历史记录已清空", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("清空", color = TvColors.AppleRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = TvColors.TextSecondary)
                }
            },
            containerColor = Color(0xFF141C2B)
        )
    }
}
