package com.ascify.app.renderer

import android.graphics.*
import android.os.SystemClock
import com.ascify.app.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * ASCIIRenderer
 *
 * Core rendering pipeline. Converts an input [Bitmap] into a colored ASCII art [Bitmap].
 *
 * Pipeline:
 *   1. Downsample input to match the ASCII column/row grid.
 *   2. (Optional) Apply Sobel edge-enhancement on luminance channel.
 *   3. Remap each cell's brightness to a character in the selected charset.
 *   4. Apply color palette transformation.
 *   5. Draw characters onto an output Canvas with their mapped colors.
 *
 * Uses a background dispatcher for CPU-intensive work. The caller (ViewModel / ImageAnalysis
 * callback) calls [renderFrame] which is a suspend fun — safe to call from a coroutine scope.
 */
@Singleton
class ASCIIRenderer @Inject constructor() {

    // ─── State ────────────────────────────────────────────────────────────────

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val fpsFrameCount = AtomicLong(0)
    private var fpsWindowStart = SystemClock.elapsedRealtime()

    /** Dedicated dispatcher — uses 2 threads to keep rendering smooth */
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(2)

    // ─── Pre-allocated render resources ──────────────────────────────────────

    private var charPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }

    // Reusable scaled bitmap (avoids GC pressure)
    private var scaledBitmap: Bitmap? = null
    private var outputBitmap: Bitmap? = null
    // Char pixel arrays
    private var pixelBuffer: IntArray = IntArray(0)

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Render [inputBitmap] to a new colored ASCII bitmap.
     *
     * @param inputBitmap  Raw camera frame (any resolution)
     * @param settings     Current rendering settings
     * @return             ASCII-rendered [Bitmap] at [outputWidth] × [outputHeight]
     */
    suspend fun renderFrame(
        inputBitmap: Bitmap,
        settings: AppSettings,
        outputWidth: Int,
        outputHeight: Int
    ): Bitmap = withContext(renderDispatcher) {
        val startMs = SystemClock.elapsedRealtime()

        val cols = settings.asciiDensity.columns
        val cellW = outputWidth.toFloat() / cols
        // Use 1.8:1 char aspect ratio (monospace chars are taller than wide)
        val cellH = cellW * 1.8f
        val rows = (outputHeight / cellH).toInt().coerceAtLeast(1)

        // 1. Downsample input to grid resolution
        val scaled = getOrCreateScaled(inputBitmap, cols, rows)

        // 2. Read pixel array
        ensurePixelBuffer(cols * rows)
        scaled.getPixels(pixelBuffer, 0, cols, 0, 0, cols, rows)

        // 3. Optional edge detection
        val edgeMap: FloatArray? = if (settings.edgeEnhancement) {
            computeSobelEdges(pixelBuffer, cols, rows)
        } else null

        // 4. Prepare output canvas
        val out = getOrCreateOutput(outputWidth, outputHeight)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)

        // 5. Configure paint
        charPaint.textSize = cellW
        charPaint.isFakeBoldText = settings.asciiDensity == AsciiDensity.RETRO

        // 6. Draw characters
        val charArr = CharArray(1)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val idx = row * cols + col
                val pixel = pixelBuffer[idx]

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Luminance (perceptual)
                var brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

                // Night mode: boost shadows
                if (settings.nightModeEnabled) {
                    brightness = sqrt(brightness).coerceIn(0f, 1f)
                }

                // Edge boost
                if (edgeMap != null) {
                    val edge = edgeMap[idx].coerceIn(0f, 1f)
                    brightness = (brightness - edge * 0.3f).coerceIn(0f, 1f)
                }

                // Map to character
                charArr[0] = settings.characterSet.charForBrightness(brightness)

                // Map to color palette
                val mappedColor = settings.colorPalette.mapColor(pixel)
                charPaint.color = mappedColor or (0xFF shl 24)

                val x = col * cellW
                val y = row * cellH + cellH * 0.85f  // baseline offset

                canvas.drawText(charArr, 0, 1, x, y, charPaint)
            }
        }

        // 7. Update FPS
        trackFps()

        out
    }

    // ─── FPS tracking ─────────────────────────────────────────────────────────

    private fun trackFps() {
        val count = fpsFrameCount.incrementAndGet()
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000L) {
            _fps.value = count * 1000f / elapsed
            fpsFrameCount.set(0)
            fpsWindowStart = now
        }
    }

    // ─── Sobel Edge Detection ─────────────────────────────────────────────────

    private fun computeSobelEdges(pixels: IntArray, width: Int, height: Int): FloatArray {
        val luminance = FloatArray(width * height) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }

        val edges = FloatArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val tl = luminance[(y - 1) * width + (x - 1)]
                val tm = luminance[(y - 1) * width + x]
                val tr = luminance[(y - 1) * width + (x + 1)]
                val ml = luminance[y * width + (x - 1)]
                val mr = luminance[y * width + (x + 1)]
                val bl = luminance[(y + 1) * width + (x - 1)]
                val bm = luminance[(y + 1) * width + x]
                val br = luminance[(y + 1) * width + (x + 1)]

                val gx = -tl - 2f * ml - bl + tr + 2f * mr + br
                val gy = -tl - 2f * tm - tr + bl + 2f * bm + br

                edges[y * width + x] = sqrt(gx * gx + gy * gy).coerceIn(0f, 1f)
            }
        }
        return edges
    }

    // ─── Bitmap reuse helpers ─────────────────────────────────────────────────

    private fun getOrCreateScaled(input: Bitmap, w: Int, h: Int): Bitmap {
        val existing = scaledBitmap
        return if (existing != null && !existing.isRecycled &&
            existing.width == w && existing.height == h
        ) {
            // Reuse: draw input scaled into it
            val c = Canvas(existing)
            val src = Rect(0, 0, input.width, input.height)
            val dst = RectF(0f, 0f, w.toFloat(), h.toFloat())
            c.drawBitmap(input, src, dst, null)
            existing
        } else {
            existing?.recycle()
            val bm = Bitmap.createScaledBitmap(input, w, h, true)
            scaledBitmap = bm
            bm
        }
    }

    private fun getOrCreateOutput(w: Int, h: Int): Bitmap {
        val existing = outputBitmap
        return if (existing != null && !existing.isRecycled &&
            existing.width == w && existing.height == h
        ) {
            existing
        } else {
            existing?.recycle()
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            outputBitmap = bm
            bm
        }
    }

    private fun ensurePixelBuffer(size: Int) {
        if (pixelBuffer.size < size) {
            pixelBuffer = IntArray(size)
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    fun release() {
        scaledBitmap?.recycle()
        outputBitmap?.recycle()
        scaledBitmap = null
        outputBitmap = null
    }
}
