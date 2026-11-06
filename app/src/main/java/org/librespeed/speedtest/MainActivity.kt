package org.librespeed.speedtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.librespeed.speedtest.data.ClientInfo
import org.librespeed.speedtest.ui.theme.LibreSpeedTheme
import org.librespeed.speedtest.ui.theme.ThemeMode
import org.librespeed.speedtest.ui.tv.TvApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.fdossena.speedtest.core.base.Connection.setUserAgent(ClientInfo.userAgent)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            androidx.compose.runtime.DisposableEffect(Unit) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            LibreSpeedTheme(mode = ThemeMode.DARK) {
                TvApp()
            }
        }
    }
}
