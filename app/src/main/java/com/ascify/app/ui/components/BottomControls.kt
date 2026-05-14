package com.ascify.app.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.ascify.app.settings.CameraLens
import com.ascify.app.settings.CaptureMode
import com.ascify.app.ui.CameraUiState
import com.ascify.app.ui.theme.ASCIIColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomControls(
    uiState: CameraUiState,
    onShutterClick: () -> Unit,
    onLensSwitchClick: (CameraLens) -> Unit,
    onModeSwipeChange: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val modes = CaptureMode.values()
    val pagerState = rememberPagerState(initialPage = uiState.settings.captureMode.ordinal) { modes.size }

    LaunchedEffect(pagerState.currentPage) {
        onModeSwipeChange(modes[pagerState.currentPage])
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Mode switcher (swipeable)
        ModeSwitcher(
            pagerState = pagerState,
            modes = modes,
            onModeSelect = { mode ->
                scope.launch { pagerState.animateScrollToPage(mode.ordinal) }
            }
        )

        // Lens switcher row
        LensSwitcher(
            availableLenses = uiState.availableLenses,
            currentLens = uiState.settings.selectedLens,
            onLensSelect = onLensSwitchClick
        )

        // Main action row: gallery | shutter | flip camera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery thumbnail
            GalleryThumb(uri = uiState.lastSavedUri)

            // Shutter
            ShutterButton(
                isRecording = uiState.isRecording,
                isVideo = uiState.settings.captureMode == CaptureMode.VIDEO,
                isCapturing = uiState.isCapturing,
                onClick = onShutterClick
            )

            // Flip to selfie / back
            FlipButton(
                currentLens = uiState.settings.selectedLens,
                availableLenses = uiState.availableLenses,
                onFlip = { onLensSwitchClick(it) }
            )
        }
    }
}

// ─── Mode switcher ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModeSwitcher(
    pagerState: PagerState,
    modes: Array<CaptureMode>,
    onModeSelect: (CaptureMode) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        pageSize = PageSize.Fixed(80.dp),
        contentPadding = PaddingValues(horizontal = 140.dp)
    ) { page ->
        val mode = modes[page]
        val isSelected = pagerState.currentPage == page

        Text(
            text = mode.name,
            color = if (isSelected) ASCIIColors.Amber else ASCIIColors.TextMuted,
            fontSize = if (isSelected) 13.sp else 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            modifier = Modifier
                .clickable { onModeSelect(mode) }
                .padding(8.dp)
        )
    }
}

// ─── Lens switcher ────────────────────────────────────────────────────────────

@Composable
fun LensSwitcher(
    availableLenses: Set<CameraLens>,
    currentLens: CameraLens,
    onLensSelect: (CameraLens) -> Unit
) {
    val rearLenses = listOf(CameraLens.ULTRAWIDE, CameraLens.MAIN, CameraLens.TELEPHOTO)
        .filter { it in availableLenses }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        rearLenses.forEach { lens ->
            LensChip(
                lens = lens,
                isSelected = currentLens == lens,
                onClick = { onLensSelect(lens) }
            )
        }
    }
}

@Composable
fun LensChip(
    lens: CameraLens,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lens_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) ASCIIColors.Amber.copy(alpha = 0.2f)
                else ASCIIColors.SurfaceElevated.copy(alpha = 0.7f)
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) ASCIIColors.Amber else ASCIIColors.TextMuted,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = lens.displayName,
            color = if (isSelected) ASCIIColors.Amber else ASCIIColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Shutter button ───────────────────────────────────────────────────────────

@Composable
fun ShutterButton(
    isRecording: Boolean,
    isVideo: Boolean,
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isCapturing) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shutter_scale"
    )

    val innerColor by animateColorAsState(
        targetValue = when {
            isRecording -> ASCIIColors.RecordRed
            isVideo -> ASCIIColors.RecordRed.copy(alpha = 0.8f)
            else -> Color.White
        },
        animationSpec = tween(300),
        label = "shutter_color"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(
                width = 3.dp,
                color = if (isRecording) ASCIIColors.RecordRed else Color.White,
                shape = CircleShape
            )
            .clickable(
                enabled = !isCapturing,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isVideo && isRecording) {
            // Recording: show stop square
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(innerColor)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(innerColor)
            )
        }
    }
}

// ─── Gallery thumbnail ────────────────────────────────────────────────────────

@Composable
fun GalleryThumb(uri: Uri?) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ASCIIColors.SurfaceElevated)
            .border(1.dp, ASCIIColors.TextMuted, RoundedCornerShape(8.dp))
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = "Last photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "@",
                    color = ASCIIColors.TextMuted,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ─── Flip camera button ───────────────────────────────────────────────────────

@Composable
fun FlipButton(
    currentLens: CameraLens,
    availableLenses: Set<CameraLens>,
    onFlip: (CameraLens) -> Unit
) {
    val targetLens = if (currentLens == CameraLens.SELFIE) CameraLens.MAIN else CameraLens.SELFIE
    val available = targetLens in availableLenses

    var rotationAngle by remember { mutableStateOf(0f) }
    val rotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(400),
        label = "flip_rotation"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(ASCIIColors.SurfaceElevated.copy(alpha = 0.7f))
            .clickable(enabled = available) {
                rotationAngle += 180f
                onFlip(targetLens)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⟳",
            color = if (available) ASCIIColors.TextPrimary else ASCIIColors.TextMuted,
            fontSize = 22.sp,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
}
