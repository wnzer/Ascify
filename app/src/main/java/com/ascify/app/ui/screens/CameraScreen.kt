package com.ascify.app.ui.screens

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ascify.app.settings.*
import com.ascify.app.ui.CameraViewModel
import com.ascify.app.ui.components.*
import com.ascify.app.ui.theme.ASCIIColors
import com.google.accompanist.permissions.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    navController: NavController,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val asciiFrame by viewModel.asciiFrame.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Permission handling
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(cameraPermissionState.status) {
        if (cameraPermissionState.status.isGranted) {
            viewModel.bindCamera(lifecycleOwner)
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (!cameraPermissionState.status.isGranted) {
        PermissionDeniedScreen { cameraPermissionState.launchPermissionRequest() }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── ASCII Viewfinder ──────────────────────────────────────────────────
        ASCIIViewfinder(
            bitmap = asciiFrame,
            modifier = Modifier.fillMaxSize(),
            onSizeChanged = { w, h -> viewModel.setOutputSize(w, h) },
            onPinchZoom = { factor -> viewModel.pinchZoom(factor) },
            onTapFocus = { x, y, w, h -> viewModel.tapToFocus(x, y, w, h) }
        )

        // ── Scanline overlay (CRT effect) ─────────────────────────────────────
        CRTScanlineOverlay(modifier = Modifier.fillMaxSize())

        // ── Top HUD ───────────────────────────────────────────────────────────
        TopHUD(
            flashMode = uiState.settings.flashMode,
            hasFlash = uiState.hasFlash,
            fps = uiState.fps,
            showFps = uiState.settings.showFpsCounter,
            isRecording = uiState.isRecording,
            onFlashToggle = { viewModel.cycleFlash() },
            onSettingsClick = { viewModel.showSettings(true) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // ── Zoom slider ────────────────────────────────────────────────────────
        ZoomSlider(
            zoomRatio = uiState.zoomRatio,
            minZoom = uiState.minZoom,
            maxZoom = uiState.maxZoom,
            onZoomChange = { viewModel.setZoom(it) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .fillMaxHeight(0.4f)
        )

        // ── Bottom controls ────────────────────────────────────────────────────
        BottomControls(
            uiState = uiState,
            onShutterClick = {
                when (uiState.settings.captureMode) {
                    CaptureMode.PHOTO -> viewModel.capturePhoto()
                    CaptureMode.VIDEO -> viewModel.toggleRecording()
                }
            },
            onLensSwitchClick = { lens ->
                viewModel.switchLens(lens, lifecycleOwner)
            },
            onModeSwipeChange = { mode ->
                viewModel.setCaptureMode(mode, lifecycleOwner)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // ── Settings panel ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.showSettings,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SettingsPanel(
                settings = uiState.settings,
                onDismiss = { viewModel.showSettings(false) },
                onCharacterSetChange = { viewModel.updateCharacterSet(it) },
                onColorPaletteChange = { viewModel.updateColorPalette(it) },
                onDensityChange = { viewModel.updateDensity(it) },
                onExportFormatChange = { viewModel.updateExportFormat(it) },
                onEdgeEnhancementChange = { viewModel.updateEdgeEnhancement(it) },
                onNightModeChange = { viewModel.updateNightMode(it) },
                onAdaptiveRenderingChange = { viewModel.updateAdaptiveRendering(it) },
                onSaveOriginalChange = { viewModel.updateSaveOriginal(it) },
                onShowFpsChange = { viewModel.updateShowFps(it) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        uiState.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("DISMISS", color = ASCIIColors.NeonGreen)
                    }
                },
                containerColor = ASCIIColors.SurfaceElevated,
                contentColor = ASCIIColors.TextPrimary
            ) {
                Text(msg, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }

        // ── Capture flash animation ────────────────────────────────────────────
        if (uiState.isCapturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    }
}

// ─── ASCII Viewfinder ─────────────────────────────────────────────────────────

@Composable
fun ASCIIViewfinder(
    bitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
    onSizeChanged: (Int, Int) -> Unit,
    onPinchZoom: (Float) -> Unit,
    onTapFocus: (Float, Float, Float, Float) -> Unit
) {
    var viewWidth by remember { mutableStateOf(1080) }
    var viewHeight by remember { mutableStateOf(1920) }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                viewWidth = size.width
                viewHeight = size.height
                onSizeChanged(size.width, size.height)
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    if (zoom != 1f) onPinchZoom(zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onTapFocus(offset.x, offset.y, viewWidth.toFloat(), viewHeight.toFloat())
                }
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "ASCII camera preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Loading state: animated ASCII rain
            ASCIILoadingState(modifier = Modifier.fillMaxSize())
        }
    }
}

// ─── CRT Scanline overlay ─────────────────────────────────────────────────────

@Composable
fun CRTScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.alpha(0.04f)) {
        val lineHeight = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(
                color = Color.Black,
                topLeft = Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(size.width, lineHeight / 2)
            )
            y += lineHeight
        }
    }
}

// ─── ASCII Loading State ──────────────────────────────────────────────────────

@Composable
fun ASCIILoadingState(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = buildString {
                repeat(20) { appendLine("@%#*+=-:. @%#*+=-:.") }
            },
            color = ASCIIColors.NeonGreen.copy(alpha = alpha * 0.3f),
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            lineHeight = 12.sp
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ASCII LENS",
                color = ASCIIColors.NeonGreen.copy(alpha = alpha),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 8.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "INITIALIZING CAMERA...",
                color = ASCIIColors.NeonGreen.copy(alpha = alpha * 0.7f),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
    }
}

// ─── Permission denied screen ─────────────────────────────────────────────────

@Composable
fun PermissionDeniedScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("[ CAMERA ACCESS REQUIRED ]",
                color = ASCIIColors.NeonGreen,
                fontSize = 16.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Spacer(Modifier.height(16.dp))
            Text("Ascify needs camera permission\nto render your world in ASCII art.",
                color = ASCIIColors.TextSecondary,
                fontSize = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ASCIIColors.NeonGreenFaint,
                    contentColor = ASCIIColors.NeonGreen
                )
            ) {
                Text("GRANT ACCESS", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}
