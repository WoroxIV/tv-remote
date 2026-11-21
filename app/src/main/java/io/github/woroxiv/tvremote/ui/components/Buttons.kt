package io.github.woroxiv.tvremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.woroxiv.tvremote.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Press handling shared by every remote button: haptic tick, press state,
 * and optional auto-repeat while held (volume, arrows).
 */
@Composable
fun Modifier.remotePress(
    label: String,
    enabled: Boolean = true,
    repeat: Boolean = false,
    onPressedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
): Modifier {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val action by rememberUpdatedState(onClick)
    val pressedChange by rememberUpdatedState(onPressedChange)
    return this
        .semantics {
            role = Role.Button
            contentDescription = label
            if (enabled) onClick { action(); true }
        }
        .pointerInput(enabled, repeat) {
            if (!enabled) return@pointerInput
            detectTapGestures(onPress = {
                pressedChange(true)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                action()
                var job: Job? = null
                if (repeat) job = scope.launch {
                    delay(420)
                    while (true) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        action()
                        delay(110)
                    }
                }
                tryAwaitRelease()
                job?.cancel()
                pressedChange(false)
            })
        }
}

@Composable
fun RemoteButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    size: Dp = 64.dp,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    repeat: Boolean = false,
    background: Brush = Palette.ButtonGradient,
    contentColor: Color = Palette.Text,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(stiffness = 900f), label = "press")
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(if (pressed) Palette.ButtonPressed else background)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .remotePress(label, enabled, repeat, { pressed = it }, onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.40f))
        } else if (text != null) {
            Text(text, color = contentColor, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Tall pill with + / − halves, for volume and channels. */
@Composable
fun Rocker(
    title: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    upLabel: String,
    downLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val shape = RoundedCornerShape(36.dp)
    Column(
        modifier = modifier
            .width(72.dp)
            .height(176.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(Palette.ButtonGradient)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        RockerHalf(upLabel, upIcon, enabled, onUp, Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
        RockerHalf(downLabel, downIcon, enabled, onDown, Modifier.weight(1f))
    }
}

@Composable
private fun RockerHalf(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (pressed) Color.Black.copy(alpha = 0.25f) else Color.Transparent)
            .remotePress(label, enabled, repeat = true, onPressedChange = { pressed = it }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(26.dp))
    }
}
