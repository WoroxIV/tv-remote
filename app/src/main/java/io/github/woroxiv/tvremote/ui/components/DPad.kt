package io.github.woroxiv.tvremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/** Circular directional pad with a central OK button. Arrows auto-repeat while held. */
@Composable
fun DPad(
    size: Dp,
    enabled: Boolean,
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val send by rememberUpdatedState(onKey)
    var pressed by remember { mutableStateOf<RemoteKey?>(null) }
    val okScale by animateFloatAsState(if (pressed == RemoteKey.OK) 0.93f else 1f, label = "ok")

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .semantics {
                contentDescription = "Pavé directionnel"
                customActions = listOf(
                    CustomAccessibilityAction("Haut") { send(RemoteKey.UP); true },
                    CustomAccessibilityAction("Bas") { send(RemoteKey.DOWN); true },
                    CustomAccessibilityAction("Gauche") { send(RemoteKey.LEFT); true },
                    CustomAccessibilityAction("Droite") { send(RemoteKey.RIGHT); true },
                    CustomAccessibilityAction("OK") { send(RemoteKey.OK); true },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val area = this.size
                detectTapGestures(onPress = { pos ->
                    val c = Offset(area.width / 2f, area.height / 2f)
                    val dx = pos.x - c.x
                    val dy = pos.y - c.y
                    val radius = area.width / 2f
                    val dist = hypot(dx, dy)
                    if (dist > radius) return@detectTapGestures
                    val key = when {
                        dist < radius * 0.38f -> RemoteKey.OK
                        abs(dx) > abs(dy) -> if (dx > 0) RemoteKey.RIGHT else RemoteKey.LEFT
                        else -> if (dy > 0) RemoteKey.DOWN else RemoteKey.UP
                    }
                    pressed = key
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    send(key)
                    var job: Job? = null
                    if (key != RemoteKey.OK) job = scope.launch {
                        delay(420)
                        while (true) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            send(key)
                            delay(120)
                        }
                    }
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = null
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            // Outer disc
            drawCircle(Brush.verticalGradient(listOf(Palette.ButtonTop, Palette.ButtonBottom)), r)
            drawCircle(Color.White.copy(alpha = 0.06f), r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            // Highlight the pressed direction
            pressed?.takeIf { it != RemoteKey.OK }?.let { key ->
                val start = when (key) {
                    RemoteKey.RIGHT -> -45f
                    RemoteKey.DOWN -> 45f
                    RemoteKey.LEFT -> 135f
                    else -> 225f
                }
                drawArc(
                    brush = Brush.radialGradient(
                        listOf(Palette.Accent.copy(alpha = 0.35f), Color.Transparent),
                        center = center, radius = r,
                    ),
                    startAngle = start, sweepAngle = 90f, useCenter = true,
                    topLeft = Offset.Zero, size = Size(r * 2, r * 2),
                )
            }
            // Arrows
            for ((angle, key) in listOf(0f to RemoteKey.UP, 90f to RemoteKey.RIGHT, 180f to RemoteKey.DOWN, 270f to RemoteKey.LEFT)) {
                rotate(angle) { chevron(r, if (pressed == key) Palette.Accent2 else Palette.TextDim) }
            }
            // Ring between the pad and OK
            drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.40f)
        }
        OkButton(size * 0.34f * okScale, pressed == RemoteKey.OK)
    }
}

private fun DrawScope.chevron(r: Float, color: Color) {
    val c = center
    val y = c.y - r * 0.70f
    val w = r * 0.11f
    val path = Path().apply {
        moveTo(c.x - w, y + w * 0.6f)
        lineTo(c.x, y - w * 0.4f)
        lineTo(c.x + w, y + w * 0.6f)
    }
    drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun OkButton(size: Dp, pressed: Boolean) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    if (pressed) listOf(Palette.Accent.copy(alpha = 0.75f), Palette.Accent2.copy(alpha = 0.75f))
                    else listOf(Palette.Accent, Palette.Accent2)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("OK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.24f).sp)
    }
}
