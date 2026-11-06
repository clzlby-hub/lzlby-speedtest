package org.librespeed.speedtest.ui.tv.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.librespeed.speedtest.data.key
import org.librespeed.speedtest.ui.components.Sparkline
import org.librespeed.speedtest.ui.components.SpeedGauge
import org.librespeed.speedtest.ui.speedtest.Phase
import org.librespeed.speedtest.ui.speedtest.SpeedtestViewModel
import org.librespeed.speedtest.ui.tv.TvColors
import org.librespeed.speedtest.ui.tv.components.TvButton
import org.librespeed.speedtest.ui.tv.components.TvCard
import java.util.Locale

@Composable
fun TvSpeedtestScreen(
    viewModel: SpeedtestViewModel,
    onSwitchToServers: () -> Unit,
    primaryFocusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unitLabel = if (state.useMBytes) "MB/s" else "Mbps"
    fun display(value: Double): Double = if (state.useMBytes) value / 8 else value
    val running = state.phase in setOf(Phase.PING, Phase.DOWNLOAD, Phase.UPLOAD)

    val actionButtonFocusRequester = primaryFocusRequester ?: remember { FocusRequester() }
    val directPillFocusRequester = remember { FocusRequester() }
    val proxyPillFocusRequester = remember { FocusRequester() }
    val serverCardFocusRequester = remember { FocusRequester() }
    val downloadCardFocusRequester = remember { FocusRequester() }
    val uploadCardFocusRequester = remember { FocusRequester() }

    // Request initial focus ONCE when screen first mounts
    LaunchedEffect(Unit) {
        delay(100)
        try {
            actionButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Main Middle Row: Left (Gauge + Action Button) + Right (Metrics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT COLUMN: Apple TV Speedometer Gauge & Persistent Action Button
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val currentDisplaySpeed = if (running) {
                        state.currentSpeed
                    } else if (state.download >= 0) {
                        state.download
                    } else {
                        0.0
                    }

                    SpeedGauge(
                        speed = currentDisplaySpeed,
                        modifier = Modifier.size(250.dp),
                        useMBytes = state.useMBytes
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val speedText = if (running) {
                                String.format(Locale.US, "%.1f", display(state.currentSpeed))
                            } else if (state.download >= 0) {
                                String.format(Locale.US, "%.1f", display(state.download))
                            } else {
                                "0.0"
                            }
                            Text(
                                text = speedText,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = unitLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TvColors.AppleCyan
                            )
                            Spacer(Modifier.height(8.dp))
                            PhaseBadge(state.phase)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Mode Switch: Direct Domestic Public IP vs. Clash Proxy Node
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvModeSwitchPill(
                            icon = Icons.Filled.Wifi,
                            title = "测公网直连",
                            isSelected = !state.isProxyMode,
                            onClick = { if (!running) viewModel.setProxyMode(false) },
                            focusRequester = directPillFocusRequester,
                            onLeftPressed = null,
                            onRightPressed = { proxyPillFocusRequester.requestFocus() },
                            onUpPressed = onUpPressed,
                            onDownPressed = { actionButtonFocusRequester.requestFocus() }
                        )
                        TvModeSwitchPill(
                            icon = Icons.Filled.VpnKey,
                            title = "测代理专线",
                            isSelected = state.isProxyMode,
                            onClick = { if (!running) viewModel.setProxyMode(true) },
                            focusRequester = proxyPillFocusRequester,
                            onLeftPressed = { directPillFocusRequester.requestFocus() },
                            onRightPressed = { downloadCardFocusRequester.requestFocus() },
                            onUpPressed = onUpPressed,
                            onDownPressed = { actionButtonFocusRequester.requestFocus() }
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Persistent Action Button: Stays mounted continuously so focus is NEVER lost or displaced!
                    TvButton(
                        text = if (running) "停止测速" else "确定",
                        icon = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        isDestructive = running,
                        accentColor = if (running) TvColors.AppleRed else TvColors.AppleGreen,
                        onClick = { viewModel.startOrStop() },
                        focusRequester = actionButtonFocusRequester,
                        onUpPressed = {
                            if (state.isProxyMode) {
                                proxyPillFocusRequester.requestFocus()
                            } else {
                                directPillFocusRequester.requestFocus()
                            }
                        },
                        onDownPressed = { serverCardFocusRequester.requestFocus() },
                        onRightPressed = { downloadCardFocusRequester.requestFocus() },
                        modifier = Modifier.width(190.dp)
                    )
                }
            }

            // RIGHT COLUMN: Apple TV Glass Metrics Dashboard
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // 1. Download Metric Card (Navigable via remote)
                AppleTvMetricCard(
                    title = "下载速度 (DOWNLOAD)",
                    icon = Icons.Filled.ArrowDownward,
                    accentColor = TvColors.AppleGreen,
                    value = if (state.download < 0) "—" else String.format(Locale.US, "%.1f", display(state.download)),
                    unit = unitLabel,
                    samples = state.downloadSamples,
                    isActive = state.phase == Phase.DOWNLOAD,
                    focusRequester = downloadCardFocusRequester,
                    onLeftPressed = { actionButtonFocusRequester.requestFocus() },
                    onDownPressed = { uploadCardFocusRequester.requestFocus() },
                    onUpPressed = onUpPressed
                )

                Spacer(Modifier.height(16.dp))

                // 2. Upload Metric Card (Navigable via remote)
                AppleTvMetricCard(
                    title = "上传速度 (UPLOAD)",
                    icon = Icons.Filled.ArrowUpward,
                    accentColor = TvColors.ApplePurple,
                    value = if (state.upload < 0) "—" else String.format(Locale.US, "%.1f", display(state.upload)),
                    unit = unitLabel,
                    samples = state.uploadSamples,
                    isActive = state.phase == Phase.UPLOAD,
                    focusRequester = uploadCardFocusRequester,
                    onLeftPressed = { actionButtonFocusRequester.requestFocus() },
                    onUpPressed = { downloadCardFocusRequester.requestFocus() },
                    onDownPressed = { serverCardFocusRequester.requestFocus() }
                )

                Spacer(Modifier.height(16.dp))

                // 3. Latency & Stability Row (Ping, Jitter, Loss)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppleTvMiniCard(
                        title = "延迟 PING",
                        value = if (state.ping < 0) "—" else String.format(Locale.US, "%.0f ms", state.ping),
                        accentColor = TvColors.AppleOrange,
                        modifier = Modifier.weight(1f)
                    )
                    AppleTvMiniCard(
                        title = "抖动 JITTER",
                        value = if (state.jitter < 0) "—" else String.format(Locale.US, "%.0f ms", state.jitter),
                        accentColor = TvColors.AppleCyan,
                        modifier = Modifier.weight(1f)
                    )
                    AppleTvMiniCard(
                        title = "丢包率 LOSS",
                        value = if (state.loss < 0) "0.0%" else String.format(Locale.US, "%.1f%%", state.loss),
                        accentColor = if (state.loss > 0) TvColors.AppleRed else TvColors.TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // BOTTOM BAR: Server Info & Client Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selected Server Card (Focusable)
            val server = state.selectedServer
            val isCustom = server != null && server.key() in state.customKeys
            val serverName = when {
                server != null -> "${server.name} ${if (isCustom) "[自建局域网节点]" else ""}"
                state.selectingServers -> "正在优选节点..."
                state.serverListFailed -> "节点列表加载失败，按确定重试"
                else -> "未选择节点"
            }
            val serverPing = if (server != null && server.ping >= 0) " (${String.format(Locale.US, "%.0f ms", server.ping)})" else ""

            TvCard(
                onClick = onSwitchToServers,
                modifier = Modifier.weight(1.3f).height(50.dp),
                shape = RoundedCornerShape(25.dp),
                backgroundColor = Color(0x33141A29),
                focusedBackgroundColor = TvColors.AppleBlue.copy(alpha = 0.35f),
                focusRequester = serverCardFocusRequester,
                onUpPressed = {
                    try {
                        actionButtonFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            ) { isFocused ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = if (isCustom) TvColors.AppleOrange else TvColors.AppleCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "当前节点: $serverName$serverPing",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (isFocused) "按 OK 切换 ➔" else "切换 ➔",
                        color = if (isFocused) Color.White else TvColors.AppleCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Client IP Pill
            Surface(
                color = Color(0x22141A29),
                shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, if (state.isProxyMode) TvColors.ApplePurple.copy(alpha = 0.5f) else Color(0x22FFFFFF)),
                modifier = Modifier.weight(0.9f).height(50.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (state.isProxyMode) Icons.Filled.VpnKey else Icons.Filled.Lan,
                        contentDescription = null,
                        tint = if (state.isProxyMode) TvColors.ApplePurple else TvColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (state.isProxyMode) "代理出口: ${formatClientIp(state.ipInfo, true)}" else "本地公网: ${formatClientIp(state.ipInfo, false)}",
                        color = if (state.isProxyMode) Color.White else TvColors.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TvModeSwitchPill(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onLeftPressed: (() -> Unit)? = null,
    onRightPressed: (() -> Unit)? = null,
    onUpPressed: (() -> Unit)? = null,
    onDownPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(19.dp),
        backgroundColor = if (isSelected) TvColors.AppleBlue.copy(alpha = 0.35f) else Color(0x18FFFFFF),
        focusedBackgroundColor = TvColors.AppleBlue.copy(alpha = 0.7f),
        borderColor = if (isSelected) TvColors.AppleCyan else Color(0x22FFFFFF),
        focusedBorderColor = Color.White,
        focusRequester = focusRequester,
        onLeftPressed = onLeftPressed,
        onRightPressed = onRightPressed,
        onUpPressed = onUpPressed,
        onDownPressed = onDownPressed
    ) { isFocused ->
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused || isSelected) Color.White else TvColors.AppleCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

private fun formatClientIp(raw: String?, isProxy: Boolean = false): String {
    if (raw.isNullOrBlank()) return if (isProxy) "正在获取代理出口 IP..." else "正在获取公网 IP..."
    val trimmed = raw.trim()
    val parts = trimmed.split("-", limit = 2)
    val ip = parts[0].trim()
    val serverIsp = if (parts.size > 1) parts[1].trim() else ""

    if (isProxy) {
        val detail = if (serverIsp.isNotBlank() && !serverIsp.equals("Unknown ISP", ignoreCase = true)) {
            serverIsp
        } else {
            "代理专线节点"
        }
        return "$ip · $detail"
    }

    val detectedIsp = when {
        ip.startsWith("117.") || ip.startsWith("223.") || ip.startsWith("111.") ||
        ip.startsWith("39.") || ip.startsWith("36.") || ip.startsWith("120.") ||
        ip.startsWith("183.") || ip.startsWith("211.136.") || ip.startsWith("211.137.") ||
        ip.startsWith("211.138.") || ip.startsWith("211.139.") || ip.startsWith("211.140.") ||
        ip.startsWith("211.141.") || ip.startsWith("211.142.") || ip.startsWith("211.143.") -> "中国移动"

        ip.startsWith("58.") || ip.startsWith("59.") || ip.startsWith("61.") ||
        ip.startsWith("114.") || ip.startsWith("118.") || ip.startsWith("180.") ||
        ip.startsWith("181.") || ip.startsWith("189.") || ip.startsWith("218.") ||
        ip.startsWith("219.") || ip.startsWith("222.") -> "中国电信"

        ip.startsWith("42.") || ip.startsWith("112.") || ip.startsWith("116.") ||
        ip.startsWith("123.") || ip.startsWith("156.") || ip.startsWith("175.") ||
        ip.startsWith("176.") || ip.startsWith("185.") || ip.startsWith("186.") ||
        ip.startsWith("210.") || ip.startsWith("220.") || ip.startsWith("221.") -> "中国联通"

        ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.") || ip.startsWith("127.") -> "局域网内网"
        else -> ""
    }

    val finalIsp = when {
        serverIsp.isBlank() || serverIsp.equals("Unknown ISP", ignoreCase = true) || serverIsp.equals("Unknown", ignoreCase = true) -> {
            if (detectedIsp.isNotEmpty()) detectedIsp else "中国移动"
        }
        serverIsp.contains("China Mobile", ignoreCase = true) -> "中国移动"
        serverIsp.contains("China Telecom", ignoreCase = true) -> "中国电信"
        serverIsp.contains("China Unicom", ignoreCase = true) -> "中国联通"
        serverIsp.contains("Unknown", ignoreCase = true) -> if (detectedIsp.isNotEmpty()) detectedIsp else "中国移动"
        else -> serverIsp
    }

    return "$ip · $finalIsp"
}

/**
 * Apple TV Grand "GO" Button with breathing ambient glow and remote focus.
 */
@Composable
private fun AppleTvGoButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onUpPressed: (() -> Unit)? = null,
    onDownPressed: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    // Breathing pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "go_scale"
    )

    Box(
        modifier = Modifier
            .size(260.dp)
            .scale(if (isFocused) scale else pulseScale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            onClick()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (onUpPressed != null) {
                                onUpPressed()
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onDownPressed != null) {
                                onDownPressed()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Outer Glowing Halo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    if (isFocused) Brush.radialGradient(
                        listOf(Color(0x880A84FF), Color(0x330A84FF), Color.Transparent)
                    ) else Brush.radialGradient(
                        listOf(Color(0x440A84FF), Color.Transparent)
                    )
                )
        )

        // Inner Disc
        Box(
            modifier = Modifier
                .size(210.dp)
                .shadow(
                    elevation = if (isFocused) 24.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = TvColors.AppleBlue,
                    spotColor = Color.White
                )
                .clip(CircleShape)
                .background(
                    if (isFocused) Brush.radialGradient(
                        listOf(Color(0xFF007AFF), Color(0xFF0040A8))
                    ) else Brush.radialGradient(
                        listOf(Color(0xFF14243B), Color(0xFF0D1624))
                    )
                )
                .border(
                    width = if (isFocused) 4.dp else 2.dp,
                    color = if (isFocused) Color.White else TvColors.AppleCyan.copy(alpha = 0.6f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "确定",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "开始测速",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) Color.White else TvColors.AppleCyan
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "按 OK 键确定",
                    fontSize = 11.sp,
                    color = if (isFocused) Color.White.copy(alpha = 0.8f) else TvColors.TextMuted
                )
            }
        }
    }
}

/**
 * Large Apple TV metric card for Download / Upload with embedded sparkline.
 * Fully focusable and navigable with remote D-pad.
 */
@Composable
private fun AppleTvMetricCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    value: String,
    unit: String,
    samples: List<Double>,
    isActive: Boolean,
    focusRequester: FocusRequester? = null,
    onLeftPressed: (() -> Unit)? = null,
    onRightPressed: (() -> Unit)? = null,
    onUpPressed: (() -> Unit)? = null,
    onDownPressed: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val borderColor = if (isActive) accentColor else TvColors.CardNormalBorder
    val bgColor = if (isActive) accentColor.copy(alpha = 0.15f) else Color(0x28141A29)

    TvCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = bgColor,
        focusedBackgroundColor = accentColor.copy(alpha = 0.25f),
        borderColor = borderColor,
        focusedBorderColor = Color.White,
        focusRequester = focusRequester,
        onLeftPressed = onLeftPressed,
        onRightPressed = onRightPressed,
        onUpPressed = onUpPressed,
        onDownPressed = onDownPressed
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else TvColors.TextSecondary
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = unit,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            // Embedded live sparkline curve
            if (samples.size > 1) {
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(65.dp)
                        .padding(start = 12.dp)
                ) {
                    Sparkline(
                        data = samples,
                        color = accentColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Compact Apple TV metric pill card for Ping / Jitter / Loss.
 */
@Composable
private fun AppleTvMiniCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0x22141A29),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        modifier = modifier.height(72.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TvColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

/**
 * Apple TV style Phase status indicator pill.
 * Features an acrylic glass container and crisp Material vector icons in a glowing circular halo badge.
 */
@Composable
private fun PhaseBadge(phase: Phase) {
    val (text, icon, color) = when (phase) {
        Phase.PING -> Triple("正在测量延迟与抖动...", Icons.Filled.Bolt, TvColors.AppleOrange)
        Phase.DOWNLOAD -> Triple("正在测量下载带宽...", Icons.Filled.ArrowDownward, TvColors.AppleGreen)
        Phase.UPLOAD -> Triple("正在测量上传带宽...", Icons.Filled.ArrowUpward, TvColors.ApplePurple)
        Phase.ERROR -> Triple("测速异常，按确定重试", Icons.Filled.Warning, TvColors.AppleRed)
        Phase.IDLE -> Triple("准备就绪", Icons.Filled.CheckCircle, TvColors.AppleCyan)
    }

    Surface(
        color = Color(0x33141C2B),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
