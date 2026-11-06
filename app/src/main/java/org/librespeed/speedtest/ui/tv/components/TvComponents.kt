package org.librespeed.speedtest.ui.tv.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librespeed.speedtest.ui.tv.TvColors

/**
 * Apple TV style focusable card:
 * Scales smoothly on focus, shows crisp white/cyan border, glowing shadow.
 */
@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = TvColors.CardNormal,
    focusedBackgroundColor: Color = TvColors.CardFocused,
    borderColor: Color = TvColors.CardNormalBorder,
    focusedBorderColor: Color = TvColors.CardFocusedBorder,
    focusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    onDownPressed: (() -> Unit)? = null,
    onLeftPressed: (() -> Unit)? = null,
    onRightPressed: (() -> Unit)? = null,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = tween(120),
        label = "card_scale"
    )
    val currentBg by animateColorAsState(
        targetValue = if (isFocused) focusedBackgroundColor else backgroundColor,
        animationSpec = tween(120),
        label = "card_bg"
    )
    val currentBorder by animateColorAsState(
        targetValue = if (isFocused) focusedBorderColor else borderColor,
        animationSpec = tween(120),
        label = "card_border"
    )

    val baseModifier = modifier
        .scale(scale)
        .shadow(
            elevation = if (isFocused) 10.dp else 2.dp,
            shape = shape
        )
        .clip(shape)
        .background(currentBg)
        .border(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = currentBorder,
            shape = shape
        )
        .onFocusChanged { isFocused = it.isFocused }
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (onLeftPressed != null) {
                            onLeftPressed()
                            true
                        } else false
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (onRightPressed != null) {
                            onRightPressed()
                            true
                        } else false
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center,
        content = { content(isFocused) }
    )
}

/**
 * Apple TV style focusable button.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = TvColors.AppleBlue,
    containerColor: Color? = null,
    focusedContainerColor: Color? = null,
    isDestructive: Boolean = false,
    focusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    onDownPressed: (() -> Unit)? = null,
    onLeftPressed: (() -> Unit)? = null,
    onRightPressed: (() -> Unit)? = null
) {
    val targetAccent = if (isDestructive) TvColors.AppleRed else accentColor
    TvCard(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        backgroundColor = containerColor ?: targetAccent.copy(alpha = 0.2f),
        focusedBackgroundColor = focusedContainerColor ?: targetAccent.copy(alpha = 0.38f),
        borderColor = targetAccent.copy(alpha = 0.5f),
        focusedBorderColor = Color.White,
        focusRequester = focusRequester,
        onUpPressed = onUpPressed,
        onDownPressed = onDownPressed,
        onLeftPressed = onLeftPressed,
        onRightPressed = onRightPressed
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Apple TV Top Navigation Capsule Bar (Segmented Control).
 */
@Composable
fun TvTabBar(
    tabs: List<Pair<String, ImageVector>>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabFocusRequesters: List<FocusRequester>? = null,
    onDownPressed: (() -> Unit)? = null
) {
    Surface(
        color = Color(0x33141A29),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = modifier.height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp)
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedTabIndex == index
                var isFocused by remember { mutableStateOf(false) }

                val scale by animateFloatAsState(
                    targetValue = if (isFocused) 1.05f else 1.0f,
                    animationSpec = tween(120),
                    label = "tab_scale"
                )

                val bgColor by animateColorAsState(
                    targetValue = when {
                        isFocused && isSelected -> Color.White
                        isFocused -> Color(0x40FFFFFF)
                        isSelected -> Color(0x28FFFFFF)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(120),
                    label = "tab_bg"
                )

                val textColor by animateColorAsState(
                    targetValue = when {
                        isFocused && isSelected -> Color.Black
                        isFocused -> Color.White
                        isSelected -> TvColors.AppleCyan
                        else -> TvColors.TextSecondary
                    },
                    animationSpec = tween(120),
                    label = "tab_text"
                )

                val req = tabFocusRequesters?.getOrNull(index)

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(26.dp))
                        .background(bgColor)
                        .onFocusChanged {
                            isFocused = it.isFocused
                        }
                        .then(if (req != null) Modifier.focusRequester(req) else Modifier)
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                    AndroidKeyEvent.KEYCODE_ENTER,
                                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        onTabSelected(index)
                                        if (onDownPressed != null) onDownPressed()
                                        true
                                    }
                                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                        onTabSelected(index)
                                        if (onDownPressed != null) {
                                            onDownPressed()
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .clickable {
                            onTabSelected(index)
                            if (onDownPressed != null) onDownPressed()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
