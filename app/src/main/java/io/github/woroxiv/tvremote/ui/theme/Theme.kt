package io.github.woroxiv.tvremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF0B0D12)
    val Surface = Color(0xFF151821)
    val SurfaceHigh = Color(0xFF1D212C)
    val ButtonTop = Color(0xFF262B38)
    val ButtonBottom = Color(0xFF181B24)
    val Stroke = Color(0xFF2A2F3C)
    val Text = Color(0xFFF2F4F8)
    val TextDim = Color(0xFF8B93A7)
    val Accent = Color(0xFF7C6CFF)
    val Accent2 = Color(0xFF22D3EE)
    val Danger = Color(0xFFFF5A6E)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)

    val AccentGradient = Brush.linearGradient(listOf(Accent, Accent2))
    val ButtonGradient = Brush.verticalGradient(listOf(ButtonTop, ButtonBottom))
    val ButtonPressed = Brush.verticalGradient(listOf(Color(0xFF1A1E28), Color(0xFF14161D)))
}

private val colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.Accent2,
    onSecondary = Color.Black,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    surfaceContainerLow = Palette.Surface,
    outline = Palette.Stroke,
    outlineVariant = Palette.Stroke,
    error = Palette.Danger,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
)

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
