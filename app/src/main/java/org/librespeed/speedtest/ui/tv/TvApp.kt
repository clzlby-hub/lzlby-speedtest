package org.librespeed.speedtest.ui.tv

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.librespeed.speedtest.ui.speedtest.Phase
import org.librespeed.speedtest.ui.speedtest.SpeedtestViewModel
import org.librespeed.speedtest.ui.tv.components.TvTabBar
import org.librespeed.speedtest.ui.tv.screens.TvHistoryScreen
import org.librespeed.speedtest.ui.tv.screens.TvServersScreen
import org.librespeed.speedtest.ui.tv.screens.TvSettingsScreen
import org.librespeed.speedtest.ui.tv.screens.TvSpeedtestScreen

enum class TvTab(val label: String, val icon: ImageVector) {
    SPEEDTEST("测速", Icons.Filled.Speed),
    SERVERS("测速节点", Icons.Filled.Public),
    HISTORY("历史记录", Icons.Filled.History),
    SETTINGS("设置", Icons.Filled.Settings)
}

@Composable
fun TvApp(
    viewModel: SpeedtestViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val running = state.phase in setOf(Phase.PING, Phase.DOWNLOAD, Phase.UPLOAD)

    val context = LocalContext.current
    val activity = context as? Activity

    // Keep TV screen awake while LibreSpeed is open (prevent screensaver / sleep interrupts)
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Remote Back Button Handling
    BackHandler {
        when {
            running -> {
                viewModel.startOrStop()
            }
            selectedTab != 0 -> {
                selectedTab = 0
            }
            else -> {
                activity?.finish()
            }
        }
    }

    val tabFocusRequesters = remember { List(TvTab.entries.size) { FocusRequester() } }
    val speedtestFocusRequester = remember { FocusRequester() }
    val serversFocusRequester = remember { FocusRequester() }
    val historyFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedTab) {
        delay(80)
        try {
            when (TvTab.entries[selectedTab]) {
                TvTab.SPEEDTEST -> speedtestFocusRequester.requestFocus()
                TvTab.SERVERS -> serversFocusRequester.requestFocus()
                TvTab.HISTORY -> historyFocusRequester.requestFocus()
                TvTab.SETTINGS -> settingsFocusRequester.requestFocus()
            }
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.BackgroundBrush)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Apple TV Centered Segmented Capsule Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                TvTabBar(
                    tabs = TvTab.entries.map { it.label to it.icon },
                    selectedTabIndex = selectedTab,
                    tabFocusRequesters = tabFocusRequesters,
                    onTabSelected = { selectedTab = it },
                    onDownPressed = {
                        try {
                            when (TvTab.entries[selectedTab]) {
                                TvTab.SPEEDTEST -> speedtestFocusRequester.requestFocus()
                                TvTab.SERVERS -> serversFocusRequester.requestFocus()
                                TvTab.HISTORY -> historyFocusRequester.requestFocus()
                                TvTab.SETTINGS -> settingsFocusRequester.requestFocus()
                            }
                        } catch (_: Exception) {}
                    }
                )
            }

            // Tab Content Screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (TvTab.entries[selectedTab]) {
                    TvTab.SPEEDTEST -> {
                        TvSpeedtestScreen(
                            viewModel = viewModel,
                            onSwitchToServers = { selectedTab = TvTab.SERVERS.ordinal },
                            primaryFocusRequester = speedtestFocusRequester,
                            onUpPressed = {
                                try {
                                    tabFocusRequesters[0].requestFocus()
                                } catch (_: Exception) {}
                            }
                        )
                    }
                    TvTab.SERVERS -> {
                        TvServersScreen(
                            viewModel = viewModel,
                            onServerSelected = { selectedTab = TvTab.SPEEDTEST.ordinal },
                            primaryFocusRequester = serversFocusRequester,
                            onUpPressed = {
                                try {
                                    tabFocusRequesters[1].requestFocus()
                                } catch (_: Exception) {}
                            }
                        )
                    }
                    TvTab.HISTORY -> {
                        TvHistoryScreen(
                            onReTest = {
                                selectedTab = TvTab.SPEEDTEST.ordinal
                                viewModel.startOrStop()
                            },
                            primaryFocusRequester = historyFocusRequester,
                            onUpPressed = {
                                try {
                                    tabFocusRequesters[2].requestFocus()
                                } catch (_: Exception) {}
                            }
                        )
                    }
                    TvTab.SETTINGS -> {
                        TvSettingsScreen(
                            onOpenServers = { selectedTab = TvTab.SERVERS.ordinal },
                            primaryFocusRequester = settingsFocusRequester,
                            onUpPressed = {
                                try {
                                    tabFocusRequesters[3].requestFocus()
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }
    }
}
