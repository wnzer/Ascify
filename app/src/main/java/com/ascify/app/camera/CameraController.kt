package com.ascify.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ascify.app.renderer.FrameAnalyzer
import com.ascify.app.settings.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class CameraController @Inject constructor(
    @ApplicationContext private val context: Context,
    val frameAnalyzer: FrameAnalyzer
) {

    companion object {
        private const val TAG = "ASCIICamera"
        private val ANALYSIS_SIZE = Size(480, 640)  // Low res for analysis, speed
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private val _availableLenses = MutableStateFlow<Set<CameraLens>>(emptySet())
    val availableLenses: StateFlow<Set<CameraLens>> = _availableLenses.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _minZoom = MutableStateFlow(1f)
    val minZoom: StateFlow<Float> = _minZoom.asStateFlow()

    private val _maxZoom = MutableStateFlow(5f)
    val maxZoom: StateFlow<Float> = _maxZoom.asStateFlow()

    private val _hasFlash = MutableStateFlow(false)
    val hasFlash: StateFlow<Boolean> = _hasFlash.asStateFlow()

    // ─── Internal state ───────────────────────────────────────────────────────

    private var cameraProvider: ProcessCameraProvider? = null
    private var currentCamera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // ─── Initialization ───────────────────────────────────────────────────────

    suspend fun initialize() = withContext(Dispatchers.Main) {
        val provider = getCameraProvider()
        cameraProvider = provider
        detectAvailableLenses(provider)
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCoroutine { cont ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener(
                    { cont.resume(future.get()) },
                    ContextCompat.getMainExecutor(context)
                )
            }
        }

    // ─── Lens detection ───────────────────────────────────────────────────────

    private fun detectAvailableLenses(provider: ProcessCameraProvider) {
        val lenses = mutableSetOf<CameraLens>()

        // Selfie always present
        if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            lenses.add(CameraLens.SELFIE)
        }

        // Main rear lens always assumed present
        if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            lenses.add(CameraLens.MAIN)
        }

        // Detect ultrawide and telephoto via camera2
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (cameraId in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing != CameraMetadata.LENS_FACING_BACK) continue

                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?: continue
                val sensorWidth = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width
                    ?: continue

                // Heuristic: focal < 2.5mm → ultrawide, focal > 6mm → telephoto
                for (focal in focalLengths) {
                    when {
                        focal < 2.5f -> lenses.add(CameraLens.ULTRAWIDE)
                        focal > 6.0f -> lenses.add(CameraLens.TELEPHOTO)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query camera2 for lens types: ${e.message}")
        }

        _availableLenses.value = lenses
    }

    // ─── Bind camera ──────────────────────────────────────────────────────────

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        lens: CameraLens,
        captureMode: CaptureMode,
        outputWidth: Int,
        outputHeight: Int
    ) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        // Update analyzer dimensions
        frameAnalyzer.outputWidth = outputWidth
        frameAnalyzer.outputHeight = outputHeight

        val selector = buildCameraSelector(lens)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(ANALYSIS_SIZE)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, frameAnalyzer) }

        val useCases = mutableListOf<UseCase>(imageAnalysis)

        if (captureMode == CaptureMode.PHOTO) {
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            imageCapture = capture
            useCases.add(capture)
        } else {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture
            useCases.add(capture)
        }

        try {
            currentCamera = provider.bindToLifecycle(
                lifecycleOwner, selector, *useCases.toTypedArray()
            )

            val info = currentCamera?.cameraInfo
            _hasFlash.value = info?.hasFlashUnit() ?: false
            _minZoom.value = info?.zoomState?.value?.minZoomRatio ?: 1f
            _maxZoom.value = info?.zoomState?.value?.maxZoomRatio ?: 5f
            _zoomRatio.value = 1f

        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed: ${e.message}", e)
        }
    }

    private fun buildCameraSelector(lens: CameraLens): CameraSelector {
        return when (lens) {
            CameraLens.SELFIE -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraLens.MAIN -> CameraSelector.DEFAULT_BACK_CAMERA
            // For ultrawide and telephoto we'd need Camera2 interop in a real app
            // For now fall back to back camera with a note for extension
            CameraLens.ULTRAWIDE -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraLens.TELEPHOTO -> CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    // ─── Zoom ─────────────────────────────────────────────────────────────────

    fun setZoom(ratio: Float) {
        val clamped = ratio.coerceIn(_minZoom.value, _maxZoom.value)
        currentCamera?.cameraControl?.setZoomRatio(clamped)
        _zoomRatio.value = clamped
    }

    fun pinchZoom(scaleFactor: Float) {
        setZoom(_zoomRatio.value * scaleFactor)
    }

    // ─── Focus ────────────────────────────────────────────────────────────────

    fun tapToFocus(x: Float, y: Float, viewWidth: Float, viewHeight: Float) {
        val factory = SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight)
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        currentCamera?.cameraControl?.startFocusAndMetering(action)
    }

    // ─── Flash ────────────────────────────────────────────────────────────────

    fun setFlash(mode: FlashMode) {
        when (mode) {
            FlashMode.TORCH -> currentCamera?.cameraControl?.enableTorch(true)
            else -> {
                currentCamera?.cameraControl?.enableTorch(false)
                imageCapture?.flashMode = when (mode) {
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            }
        }
    }

    // ─── Photo capture ────────────────────────────────────────────────────────

    fun getImageCapture(): ImageCapture? = imageCapture

    // ─── Video recording ──────────────────────────────────────────────────────

    fun isRecording(): Boolean = _isRecording.value

    fun startRecording(
        outputFile: java.io.File,
        onFinished: (java.io.File) -> Unit,
        onError: (String) -> Unit
    ) {
        val vc = videoCapture ?: run {
            onError("Video capture not initialized")
            return
        }

        val fileOutput = FileOutputOptions.Builder(outputFile).build()

        recording = vc.output
            .prepareRecording(context, fileOutput)
            .apply {
                try { withAudioEnabled() } catch (e: SecurityException) { /* no audio */ }
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> _isRecording.value = true
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        if (!event.hasError()) onFinished(outputFile)
                        else onError("Recording failed: ${event.error}")
                    }
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    fun release() {
        frameAnalyzer.cancel()
        analysisExecutor.shutdownNow()
        cameraProvider?.unbindAll()
    }
}
