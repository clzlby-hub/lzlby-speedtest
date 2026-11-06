package org.librespeed.speedtest.ui.tv.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.librespeed.speedtest.data.AppPreferences
import org.librespeed.speedtest.ui.tv.TvColors
import org.librespeed.speedtest.ui.tv.components.TvCard

@Composable
fun TvSettingsScreen(
    onOpenServers: () -> Unit,
    primaryFocusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val firstCardFocusRequester = primaryFocusRequester ?: remember { FocusRequester() }

    val useMBytes by prefs.useMBytes.collectAsStateWithLifecycle(initialValue = false)
    val testMode by prefs.testMode.collectAsStateWithLifecycle(initialValue = "standard")
    val telemetryEnabled by prefs.telemetryEnabled.collectAsStateWithLifecycle(initialValue = false)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        // Left Column: App Info & TV Status
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = TvColors.AppleBlue.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, TvColors.AppleBlue)
            ) {
                Text(
                    text = "Apple TV Edition",
                    color = TvColors.AppleCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "LibreSpeed TV",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "完全开源 (LGPL-3.0) · 零广告 · 零追踪\n专为索尼电视及 Android TV 大屏深度优化",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = TvColors.TextSecondary
            )

            Spacer(Modifier.height(20.dp))

            Surface(
                color = Color(0x22141A29),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "💡 局域网自建测速提示",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TvColors.AppleOrange
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "如果您在群晖/NAS/软路由部署了 Docker 版 LibreSpeed，可在【测速节点】页面直接填入内网 IP，测试千兆/万兆局域网与 Wi-Fi 真实内网吞吐。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = TvColors.TextSecondary
                    )
                }
            }
        }

        // Right Column: Settings Cards with Remote Navigation
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Speed Unit (Mbps vs MB/s)
            TvSettingToggleCard(
                title = "速度显示单位",
                subtitle = if (useMBytes) "当前: MB/s (兆字节/秒 · 文件实际下载速度)" else "当前: Mbps (兆比特/秒 · 运营商宽带宣传单位)",
                currentValue = if (useMBytes) "MB/s" else "Mbps",
                icon = Icons.Filled.Speed,
                accentColor = TvColors.AppleGreen,
                focusRequester = firstCardFocusRequester,
                onUpPressed = onUpPressed,
                onClick = {
                    scope.launch {
                        prefs.setUseMBytes(!useMBytes)
                        Toast.makeText(context, if (!useMBytes) "已切换为 MB/s" else "已切换为 Mbps", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 2. Test Mode (Standard / Single / Stability)
            val modeName = when (testMode) {
                "single" -> "单连接 (Single Stream)"
                "stability" -> "长时稳定性测试 (Stability)"
                else -> "多线程并发 (Standard 推荐)"
            }
            TvSettingToggleCard(
                title = "测速并发模式",
                subtitle = "按确定键切换并发模式，测试单流与多流带宽极限",
                currentValue = modeName,
                icon = Icons.Filled.Tune,
                accentColor = TvColors.AppleCyan,
                onClick = {
                    val nextMode = when (testMode) {
                        "standard" -> "single"
                        "single" -> "stability"
                        else -> "standard"
                    }
                    scope.launch {
                        prefs.setTestMode(nextMode)
                        Toast.makeText(context, "已切换为: $nextMode 模式", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 3. Keep Screen On (Anti-Screensaver)
            TvSettingToggleCard(
                title = "测速时屏幕常亮 (防电视屏保)",
                subtitle = "测速运行期间保持屏幕活跃，测试完毕后自动恢复待机保护",
                currentValue = "已启用 (默认常亮)",
                icon = Icons.Filled.Tv,
                accentColor = TvColors.AppleOrange,
                onClick = {
                    Toast.makeText(context, "防屏保机制已默认全面激活", Toast.LENGTH_SHORT).show()
                }
            )

            // 4. Telemetry Privacy
            TvSettingToggleCard(
                title = "遥测与结果分享",
                subtitle = if (telemetryEnabled) "已开启共享" else "已关闭 (零数据上传，完全保护隐私)",
                currentValue = if (telemetryEnabled) "已开启" else "已关闭",
                icon = Icons.Filled.Shield,
                accentColor = if (telemetryEnabled) TvColors.AppleOrange else TvColors.AppleGreen,
                onClick = {
                    scope.launch {
                        prefs.setTelemetryEnabled(!telemetryEnabled)
                        Toast.makeText(context, if (!telemetryEnabled) "已开启遥测" else "已关闭遥测", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 5. Jump to Servers
            TvSettingToggleCard(
                title = "管理测速服务器",
                subtitle = "添加局域网自建节点或切换测速服务器",
                currentValue = "前往管理 ➔",
                icon = Icons.Filled.Public,
                accentColor = TvColors.AppleBlue,
                onClick = onOpenServers
            )
        }
    }
}

@Composable
private fun TvSettingToggleCard(
    title: String,
    subtitle: String,
    currentValue: String,
    icon: ImageVector,
    accentColor: Color,
    focusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    TvCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x28141A29),
        borderColor = Color(0x22FFFFFF),
        focusedBorderColor = Color.White,
        focusRequester = focusRequester,
        onUpPressed = onUpPressed
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(8.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TvColors.TextSecondary
                    )
                }
            }

            Surface(
                color = if (isFocused) Color.White else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = currentValue,
                    color = if (isFocused) Color.Black else accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
