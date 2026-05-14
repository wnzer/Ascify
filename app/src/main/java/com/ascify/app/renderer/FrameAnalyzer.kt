package com.ascify.app.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.ascify.app.settings.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * FrameAnalyzer
 *
 * Implements [ImageAnalysis.Analyzer]. For each camera frame:
 *  1. Converts YUV_420_888 → JPEG → [Bitmap] (fast, hardware accelerated).
 *  2. Corrects rotation using the image's rotation degrees.
 *  3. Dispatches to [ASCIIRenderer.renderFrame] on a background coroutine.
 *  4. Posts the resulting [Bitmap] to [asciiFrame] StateFlow (observed by UI).
 *
 * Frame skipping: if the renderer is still busy from the last frame, the
 * incoming frame is dropped (try-lock pattern), keeping the UI thread snappy.
 */
class FrameAnalyzer @Inject constructor(
    private val renderer: ASCIIRenderer
) : ImageAnalysis.Analyzer {

    private val _asciiFrame = MutableStateFlow<Bitmap?>(null)
    val asciiFrame: StateFlow<Bitmap?> = _asciiFrame.asStateFlow()

    var settings: AppSettings = AppSettings()
    var outputWidth: Int = 1080
    var outputHeight: Int = 1920

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val processing = AtomicBoolean(false)

    // YUV→JPEG scratch buffer, reused across frames
    private val jpegOutputStream = ByteArrayOutputStream(256 * 1024)

    override fun analyze(image: ImageProxy) {
        // Skip frame if still rendering the previous one
        if (!processing.compareAndSet(false, true)) {
            image.close()
            return
        }

        // Capture rotation BEFORE closing the image
        val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()

        // Convert image synchronously (fast), then release
        val bitmap = try {
            yuv420ToBitmap(image)
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }

        if (bitmap == null) {
            processing.set(false)
            return
        }

        // Rotate to match display orientation
        val rotated = rotateBitmap(bitmap, rotationDegrees)
        if (rotated !== bitmap) bitmap.recycle()

        scope.launch {
            try {
                val ascii = renderer.renderFrame(rotated, settings, outputWidth, outputHeight)
                _asciiFrame.value = ascii
            } finally {
                rotated.recycle()
                processing.set(false)
            }
        }
    }

    private fun yuv420ToBitmap(image: ImageProxy): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        jpegOutputStream.reset()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, jpegOutputStream)
        val jpegArray = jpegOutputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.size)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun cancel() {
        scope.cancel()
    }
}
