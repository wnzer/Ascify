package com.ascify.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cyberpunk / retro-tech color palette
object ASCIIColors {
    val NeonGreen = Color(0xFF00FF88)
    val NeonGreenDim = Color(0xFF00CC66)
    val NeonGreenFaint = Color(0xFF003322)
    val Amber = Color(0xFFFFB300)
    val AmberDim = Color(0xFF996600)
    val CyanAccent = Color(0xFF00E5FF)
    val MatrixGreen = Color(0xFF39FF14)
    val DeepBlack = Color(0xFF000000)
    val SurfaceBlack = Color(0xFF0A0A0A)
    val SurfaceDark = Color(0xFF111111)
    val SurfaceElevated = Color(0xFF1A1A1A)
    val TextPrimary = Color(0xFFE8E8E8)
    val TextSecondary = Color(0xFF888888)
    val TextMuted = Color(0xFF444444)
    val Error = Color(0xFFFF3355)
    val RecordRed = Color(0xFFFF0040)
    val Overlay = Color(0x99000000)
    val OverlayLight = Color(0x44000000)

    // Terminal palette green
    val TerminalGreen = Color(0xFF00FF41)
    val TerminalGreenDark = Color(0xFF003B00)

    // CRT amber
    val CRTAmber = Color(0xFFFFAA00)
    val CRTAmberDark = Color(0xFF3D2900)
}

private val DarkColorScheme = darkColorScheme(
    primary = ASCIIColors.NeonGreen,
    onPrimary = Color.Black,
    primaryContainer = ASCIIColors.NeonGreenFaint,
    onPrimaryContainer = ASCIIColors.NeonGreen,
    secondary = ASCIIColors.CyanAccent,
    onSecondary = Color.Black,
    tertiary = ASCIIColors.Amber,
    onTertiary = Color.Black,
    background = ASCIIColors.DeepBlack,
    onBackground = ASCIIColors.TextPrimary,
    surface = ASCIIColors.SurfaceBlack,
    onSurface = ASCIIColors.TextPrimary,
    surfaceVariant = ASCIIColors.SurfaceElevated,
    onSurfaceVariant = ASCIIColors.TextSecondary,
    error = ASCIIColors.Error,
    outline = ASCIIColors.TextMuted,
)

@Composable
fun AscifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ASCIITypography,
        content = content
    )
}
