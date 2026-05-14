package com.ascify.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.ascify.app.ui.theme.ASCIIColors
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ZoomSlider(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(0L) }

    // Auto-hide after 3 seconds
    LaunchedEffect(zoomRatio) {
        isVisible = true
        lastInteraction = System.currentTimeMillis()
        delay(3000)
        if (System.currentTimeMillis() - lastInteraction >= 3000) {
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(
                    color = ASCIIColors.Overlay,
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    0.5.dp,
                    ASCIIColors.NeonGreen.copy(alpha = 0.3f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 6.dp, vertical = 12.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.1fx", zoomRatio),
                color = ASCIIColors.NeonGreen,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            Slider(
                value = zoomRatio,
                onValueChange = { onZoomChange(it) },
                valueRange = minZoom..maxZoom,
                modifier = Modifier
                    .height(180.dp)
                    .rotate(270f),
                colors = SliderDefaults.colors(
                    thumbColor = ASCIIColors.NeonGreen,
                    activeTrackColor = ASCIIColors.NeonGreen,
                    inactiveTrackColor = ASCIIColors.TextMuted
                )
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${minZoom.toInt()}x",
                color = ASCIIColors.TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
