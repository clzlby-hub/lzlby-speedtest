package org.librespeed.speedtest.ui.tv.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fdossena.speedtest.core.serverSelector.TestPoint
import kotlinx.coroutines.delay
import org.librespeed.speedtest.data.key
import org.librespeed.speedtest.ui.speedtest.SpeedtestViewModel
import org.librespeed.speedtest.ui.tv.TvColors
import org.librespeed.speedtest.ui.tv.components.TvButton
import org.librespeed.speedtest.ui.tv.components.TvCard
import java.util.Locale

@Composable
fun TvServersScreen(
    viewModel: SpeedtestViewModel,
    onServerSelected: () -> Unit,
    primaryFocusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    val addServerFocusRequester = primaryFocusRequester ?: remember { FocusRequester() }

    // Sort servers: custom servers first, then sorted by ping
    val sortedServers = remember(state.servers, state.customKeys) {
        val custom = state.servers.filter { it.key() in state.customKeys }
        val public = state.servers.filter { it.key() !in state.customKeys }
            .sortedBy { if (it.ping < 0) Float.MAX_VALUE else it.ping }
        custom + public
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 20.dp)
    ) {
        // Top Header & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "测速节点列表",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "选择公网官方节点，或添加您内网/局域网自建的 LibreSpeed 测速服务器",
                    fontSize = 13.sp,
                    color = TvColors.TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton(
                    text = "刷新探测延迟",
                    icon = Icons.Filled.Refresh,
                    accentColor = TvColors.AppleCyan,
                    onUpPressed = onUpPressed,
                    onClick = {
                        viewModel.refreshServers()
                        Toast.makeText(context, "正在重新探测节点延迟...", Toast.LENGTH_SHORT).show()
                    }
                )

                TvButton(
                    text = "添加自建局域网节点",
                    icon = Icons.Filled.Add,
                    accentColor = TvColors.AppleGreen,
                    focusRequester = addServerFocusRequester,
                    onUpPressed = onUpPressed,
                    onClick = { showAddDialog = true }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 3-Column Apple TV Grid
        if (state.selectingServers && sortedServers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TvColors.AppleBlue)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "正在探测全球节点延迟...",
                        color = TvColors.TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }
        } else if (sortedServers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无可用节点，请点击上方按钮刷新或添加自建节点",
                        color = TvColors.TextMuted,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sortedServers, key = { it.key() }) { server ->
                    val isCustom = server.key() in state.customKeys
                    val isSelected = state.selectedServer?.key() == server.key()

                    TvCard(
                        onClick = {
                            viewModel.selectServer(server)
                            Toast.makeText(context, "已切换测速节点: ${server.name}", Toast.LENGTH_SHORT).show()
                            onServerSelected()
                        },
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isSelected) TvColors.AppleBlue.copy(alpha = 0.2f) else TvColors.CardNormal,
                        borderColor = if (isSelected) TvColors.AppleCyan else TvColors.CardNormalBorder,
                        focusedBorderColor = Color.White
                    ) { isFocused ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isCustom) Icons.Filled.Lan else Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = if (isCustom) TvColors.AppleOrange else TvColors.AppleCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = server.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelected) {
                                    Surface(
                                        color = TvColors.AppleGreen,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "当前已选",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (isCustom) {
                                    Surface(
                                        color = TvColors.AppleOrange.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, TvColors.AppleOrange.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "自建节点",
                                            color = TvColors.AppleOrange,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = server.server,
                                fontSize = 12.sp,
                                color = TvColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val pingText = if (server.ping < 0) "探测中..." else "${String.format(Locale.US, "%.0f", server.ping)} ms"
                                val pingColor = when {
                                    server.ping < 0 -> TvColors.TextMuted
                                    server.ping < 30 -> TvColors.AppleGreen
                                    server.ping < 100 -> TvColors.AppleCyan
                                    else -> TvColors.AppleOrange
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.NetworkPing,
                                        contentDescription = null,
                                        tint = pingColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = pingText,
                                        color = pingColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isCustom) {
                                    Text(
                                        text = "长按或删除",
                                        color = TvColors.AppleRed,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            viewModel.removeCustomServer(server)
                                            Toast.makeText(context, "已删除自建节点", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Custom Server Dialog (TV Friendly)
    if (showAddDialog) {
        TvAddCustomServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                val success = viewModel.addCustomServer(name, url)
                if (success) {
                    Toast.makeText(context, "自建节点添加成功！", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                } else {
                    Toast.makeText(context, "添加失败，请检查 URL 格式", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

/**
 * TV Dialog for adding custom/LAN LibreSpeed servers.
 * Includes one-click presets for common LAN IPs.
 */
@Composable
private fun TvAddCustomServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("局域网自建测速") }
    var url by remember { mutableStateOf("http://192.168.1.1:8989/") }

    val presets = listOf(
        "http://192.168.1.1:8989/",
        "http://192.168.2.1:8989/",
        "http://10.10.2.1:8989/",
        "http://192.168.0.1:8989/"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF141C2B),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, TvColors.AppleBlue),
            modifier = Modifier
                .width(560.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    text = "添加自建 / 内网 LibreSpeed 节点",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "输入您局域网自建 Docker / 软路由 LibreSpeed 地址",
                    fontSize = 13.sp,
                    color = TvColors.TextSecondary
                )

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TvColors.AppleCyan,
                        unfocusedBorderColor = TvColors.CardNormalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("测速地址 URL") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TvColors.AppleCyan,
                        unfocusedBorderColor = TvColors.CardNormalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "常用局域网地址快捷预设 (按 OK 快速填入):",
                    fontSize = 13.sp,
                    color = TvColors.TextSecondary
                )
                Spacer(Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 第一排: 192.168.1.1 & 192.168.2.1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PresetChip(
                            label = "192.168.1.1:8989",
                            presetUrl = "http://192.168.1.1:8989/",
                            isSelected = url == "http://192.168.1.1:8989/",
                            onSelect = { url = it },
                            modifier = Modifier.weight(1f)
                        )
                        PresetChip(
                            label = "192.168.2.1:8989",
                            presetUrl = "http://192.168.2.1:8989/",
                            isSelected = url == "http://192.168.2.1:8989/",
                            onSelect = { url = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 第二排 (另外一趟): 10.10.2.1 & 192.168.0.1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PresetChip(
                            label = "10.10.2.1:8989",
                            presetUrl = "http://10.10.2.1:8989/",
                            isSelected = url == "http://10.10.2.1:8989/",
                            onSelect = { url = it },
                            modifier = Modifier.weight(1f)
                        )
                        PresetChip(
                            label = "192.168.0.1:8989",
                            presetUrl = "http://192.168.0.1:8989/",
                            isSelected = url == "http://192.168.0.1:8989/",
                            onSelect = { url = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = TvColors.TextSecondary, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    TvButton(
                        text = "保存并选择",
                        accentColor = TvColors.AppleGreen,
                        onClick = {
                            if (name.isNotBlank() && url.isNotBlank()) {
                                onConfirm(name, url)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    presetUrl: String,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TvCard(
        onClick = { onSelect(presetUrl) },
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = if (isSelected) TvColors.AppleCyan.copy(alpha = 0.25f) else Color(0x22FFFFFF),
        focusedBackgroundColor = TvColors.AppleBlue.copy(alpha = 0.6f),
        borderColor = if (isSelected) TvColors.AppleCyan else Color(0x33FFFFFF),
        focusedBorderColor = Color.White
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected || isFocused) Color.White else TvColors.AppleCyan,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
