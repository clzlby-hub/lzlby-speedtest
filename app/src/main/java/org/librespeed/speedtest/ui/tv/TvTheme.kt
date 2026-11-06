package org.librespeed.speedtest.ui.tv

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object TvColors {
    // Apple TV Deep Obsidian Space Canvas
    val BackgroundStart = Color(0xFF090D16)
    val BackgroundEnd = Color(0xFF101726)

    // Glass Card Surfaces
    val CardNormal = Color(0xFF141A29)
    val CardNormalBorder = Color(0x22FFFFFF)
    val CardFocused = Color(0xFF232D42)
    val CardFocusedBorder = Color(0xFFFFFFFF)
    val CardFocusedGlow = Color(0x400A84FF)

    // Apple Vibrant Accents
    val AppleBlue = Color(0xFF0A84FF)
    val AppleGreen = Color(0xFF30D158) // Download
    val ApplePurple = Color(0xFFBF5AF2) // Upload
    val AppleOrange = Color(0xFFFF9F0A) // Ping / Latency
    val AppleRed = Color(0xFFFF453A) // Error / Stop
    val AppleCyan = Color(0xFF64D2FF)

    // Text Colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF98A2B3)
    val TextMuted = Color(0xFF667085)

    val BackgroundBrush = Brush.verticalGradient(
        listOf(BackgroundStart, BackgroundEnd)
    )

    val GoButtonGradient = Brush.radialGradient(
        listOf(Color(0xFF1E88E5), Color(0xFF0D47A1))
    )

    val GoButtonGlow = Brush.radialGradient(
        listOf(Color(0x660A84FF), Color.Transparent)
    )
}
