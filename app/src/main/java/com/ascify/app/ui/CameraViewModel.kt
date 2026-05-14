package com.ascify.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascify.app.camera.CameraController
import com.ascify.app.export.ExportEngine
import com.ascify.app.export.ExportResult
import com.ascify.app.renderer.ASCIIRenderer
import com.ascify.app.settings.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraUiState(
    val settings: AppSettings = AppSettings(),
    val availableLenses: Set<CameraLens> = setOf(CameraLens.MAIN),
    val isRecording: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 5f,
    val hasFlash: Boolean = false,
    val fps: Float = 0f,
    val showSettings: Boolean = false,
    val lastSavedUri: Uri? = null,
    val isCapturing: Boolean = false,
    val errorMessage: String? = null,
    val outputWidth: Int = 1080,
    val outputHeight: Int = 1920,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    val cameraController: CameraController,
    private val exportEngine: ExportEngine,
    private val renderer: ASCIIRenderer,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // ASCII frame flow (passed directly from analyzer → Compose)
    val asciiFrame: StateFlow<Bitmap?> = cameraController.frameAnalyzer.asciiFrame

    init {
        // Observe settings
        viewModelScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                cameraController.frameAnalyzer.settings = settings
            }
        }

        // Observe camera state
        viewModelScope.launch {
            cameraController.availableLenses.collect { lenses ->
                _uiState.update { it.copy(availableLenses = lenses) }
            }
        }
        viewModelScope.launch {
            cameraController.isRecording.collect { rec ->
                _uiState.update { it.copy(isRecording = rec) }
            }
        }
        viewModelScope.launch {
            cameraController.zoomRatio.collect { z ->
                _uiState.update { it.copy(zoomRatio = z) }
            }
        }
        viewModelScope.launch {
            cameraController.minZoom.collect { z ->
                _uiState.update { it.copy(minZoom = z) }
            }
        }
        viewModelScope.launch {
            cameraController.maxZoom.collect { z ->
                _uiState.update { it.copy(maxZoom = z) }
            }
        }
        viewModelScope.launch {
            cameraController.hasFlash.collect { f ->
                _uiState.update { it.copy(hasFlash = f) }
            }
        }
        viewModelScope.launch {
            renderer.fps.collect { fps ->
                _uiState.update { it.copy(fps = fps) }
            }
        }

        // Initialize camera
        viewModelScope.launch {
            cameraController.initialize()
        }
    }

    // ─── Camera binding ───────────────────────────────────────────────────────

    fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val state = _uiState.value
        cameraController.bindCamera(
            lifecycleOwner = lifecycleOwner,
            lens = state.settings.selectedLens,
            captureMode = state.settings.captureMode,
            outputWidth = state.outputWidth,
            outputHeight = state.outputHeight
        )
    }

    fun setOutputSize(width: Int, height: Int) {
        _uiState.update { it.copy(outputWidth = width, outputHeight = height) }
        cameraController.frameAnalyzer.outputWidth = width
        cameraController.frameAnalyzer.outputHeight = height
    }

    // ─── Lens switching ───────────────────────────────────────────────────────

    fun switchLens(lens: CameraLens, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            updateSettings { it.copy(selectedLens = lens) }
            bindCamera(lifecycleOwner)
        }
    }

    // ─── Capture mode ─────────────────────────────────────────────────────────

    fun setCaptureMode(mode: CaptureMode, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            updateSettings { it.copy(captureMode = mode) }
            bindCamera(lifecycleOwner)
        }
    }

    // ─── Zoom ─────────────────────────────────────────────────────────────────

    fun pinchZoom(scaleFactor: Float) {
        cameraController.pinchZoom(scaleFactor)
    }

    fun setZoom(ratio: Float) {
        cameraController.setZoom(ratio)
    }

    // ─── Focus ────────────────────────────────────────────────────────────────

    fun tapToFocus(x: Float, y: Float, viewWidth: Float, viewHeight: Float) {
        cameraController.tapToFocus(x, y, viewWidth, viewHeight)
    }

    // ─── Flash ────────────────────────────────────────────────────────────────

    fun cycleFlash() {
        val current = _uiState.value.settings.flashMode
        val next = when (current) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
        viewModelScope.launch {
            updateSettings { it.copy(flashMode = next) }
            cameraController.setFlash(next)
        }
    }

    // ─── Capture ──────────────────────────────────────────────────────────────

    fun capturePhoto() {
        val ic = cameraController.getImageCapture() ?: return
        val state = _uiState.value
        _uiState.update { it.copy(isCapturing = true) }

        exportEngine.captureAndExport(
            imageCapture = ic,
            settings = state.settings,
            outputWidth = state.outputWidth,
            outputHeight = state.outputHeight,
            onResult = { result ->
                _uiState.update { it.copy(isCapturing = false) }
                when (result) {
                    is ExportResult.Success -> {
                        if (!result.isOriginal) {
                            _uiState.update { it.copy(lastSavedUri = result.uri) }
                        }
                    }
                    is ExportResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = result.error) }
                    }
                }
            }
        )
    }

    // ─── Video ────────────────────────────────────────────────────────────────

    fun toggleRecording() {
        if (cameraController.isRecording()) {
            cameraController.stopRecording()
        } else {
            val videoFile = exportEngine.createVideoFile()
            cameraController.startRecording(
                outputFile = videoFile,
                onFinished = { file ->
                    exportEngine.saveVideoToGallery(file) { result ->
                        when (result) {
                            is ExportResult.Success ->
                                _uiState.update { it.copy(lastSavedUri = result.uri) }
                            is ExportResult.Failure ->
                                _uiState.update { it.copy(errorMessage = result.error) }
                        }
                    }
                },
                onError = { err ->
                    _uiState.update { it.copy(errorMessage = err) }
                }
            )
        }
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun updateCharacterSet(cs: CharacterSet) {
        viewModelScope.launch { updateSettings { it.copy(characterSet = cs) } }
    }

    fun updateColorPalette(cp: ColorPalette) {
        viewModelScope.launch { updateSettings { it.copy(colorPalette = cp) } }
    }

    fun updateDensity(density: AsciiDensity) {
        viewModelScope.launch { updateSettings { it.copy(asciiDensity = density) } }
    }

    fun updateExportFormat(fmt: ExportFormat) {
        viewModelScope.launch { updateSettings { it.copy(exportFormat = fmt) } }
    }

    fun updateEdgeEnhancement(enabled: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(edgeEnhancement = enabled) } }
    }

    fun updateNightMode(enabled: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(nightModeEnabled = enabled) } }
    }

    fun updateAdaptiveRendering(enabled: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(adaptiveRendering = enabled) } }
    }

    fun updateSaveOriginal(enabled: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(saveOriginalFrame = enabled) } }
    }

    fun updateShowFps(enabled: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(showFpsCounter = enabled) } }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val newSettings = transform(_uiState.value.settings)
        settingsManager.updateSettings(newSettings)
        cameraController.frameAnalyzer.settings = newSettings
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.release()
        renderer.release()
        exportEngine.release()
    }
}
