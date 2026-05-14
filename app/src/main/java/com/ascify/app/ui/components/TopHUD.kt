package com.ascify.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ascify.app.settings.FlashMode
import com.ascify.app.ui.theme.ASCIIColors
import java.util.Locale

@Composable
fun TopHUD(
    flashMode: FlashMode,
    hasFlash: Boolean,
    fps: Float,
    showFps: Boolean,
    isRecording: Boolean,
    onFlashToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Flash toggle
        if (hasFlash) {
            HUDButton(
                icon = flashIcon(flashMode),
                tint = flashColor(flashMode),
                onClick = onFlashToggle
            )
        } else {
            Spacer(Modifier.size(48.dp))
        }

        // Center: App name / recording indicator / FPS
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isRecording) {
                RecordingIndicator()
            } else {
                Text(
                    text = "ASCII LENS",
                    color = ASCIIColors.NeonGreen.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }

            if (showFps && fps > 0) {
                Text(
                    text = String.format(Locale.US, "%.0f FPS", fps),
                    color = ASCIIColors.Amber.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        // Right: Settings
        HUDButton(
            icon = Icons.Default.Tune,
            tint = ASCIIColors.TextSecondary,
            onClick = onSettingsClick
        )
    }
}

@Composable
fun HUDButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(ASCIIColors.Overlay)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ASCIIColors.RecordRed.copy(alpha = alpha))
        )
        Text(
            text = "REC",
            color = ASCIIColors.RecordRed.copy(alpha = alpha),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

private fun flashIcon(mode: FlashMode): ImageVector = when (mode) {
    FlashMode.OFF -> Icons.Default.FlashOff
    FlashMode.ON -> Icons.Default.FlashOn
    FlashMode.AUTO -> Icons.Default.FlashAuto
    FlashMode.TORCH -> Icons.Default.Lightbulb
}

private fun flashColor(mode: FlashMode): Color = when (mode) {
    FlashMode.OFF -> ASCIIColors.TextSecondary
    FlashMode.ON -> ASCIIColors.Amber
    FlashMode.AUTO -> ASCIIColors.CyanAccent
    FlashMode.TORCH -> ASCIIColors.Amber
}
