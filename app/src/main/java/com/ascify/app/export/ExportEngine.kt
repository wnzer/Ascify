package com.ascify.app.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.ascify.app.renderer.ASCIIRenderer
import com.ascify.app.settings.AppSettings
import com.ascify.app.settings.ExportFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExportResult {
    data class Success(val uri: Uri, val isOriginal: Boolean = false) : ExportResult()
    data class Failure(val error: String) : ExportResult()
}

@Singleton
class ExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: ASCIIRenderer
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── ASCII photo export ───────────────────────────────────────────────────

    /**
     * Capture a still from CameraX, render it as ASCII, and save to gallery.
     */
    fun captureAndExport(
        imageCapture: ImageCapture,
        settings: AppSettings,
        outputWidth: Int,
        outputHeight: Int,
        onResult: (ExportResult) -> Unit
    ) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        try {
                            val bitmap = imageProxyToBitmap(image)
                            image.close()

                            // Render ASCII version
                            val ascii = renderer.renderFrame(bitmap, settings, outputWidth, outputHeight)

                            val uri = saveBitmapToGallery(
                                bitmap = ascii,
                                format = settings.exportFormat,
                                prefix = "ascii"
                            )

                            if (uri != null) {
                                withContext(Dispatchers.Main) {
                                    onResult(ExportResult.Success(uri))
                                }

                                // Optionally also save original
                                if (settings.saveOriginalFrame) {
                                    val origUri = saveBitmapToGallery(bitmap, settings.exportFormat, "orig")
                                    origUri?.let {
                                        withContext(Dispatchers.Main) {
                                            onResult(ExportResult.Success(it, isOriginal = true))
                                        }
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onResult(ExportResult.Failure("Could not save to gallery"))
                                }
                            }

                            bitmap.recycle()
                        } catch (e: Exception) {
                            image.close()
                            withContext(Dispatchers.Main) {
                                onResult(ExportResult.Failure(e.message ?: "Unknown error"))
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(ExportResult.Failure(exception.message ?: "Capture failed"))
                }
            }
        )
    }

    // ─── Save bitmap to MediaStore ────────────────────────────────────────────

    private fun saveBitmapToGallery(
        bitmap: Bitmap,
        format: ExportFormat,
        prefix: String
    ): Uri? {
        val filename = "${prefix}_${timestamp()}.${format.name.lowercase()}"
        val mimeType = if (format == ExportFormat.PNG) "image/png" else "image/jpeg"
        val compressFormat = if (format == ExportFormat.PNG) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val quality = if (format == ExportFormat.PNG) 100 else 92

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Ascify")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null

            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(compressFormat, quality, stream)
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "Ascify"
            ).also { it.mkdirs() }

            val file = File(dir, filename)
            FileOutputStream(file).use { stream ->
                bitmap.compress(compressFormat, quality, stream)
            }

            Uri.fromFile(file)
        }
    }

    // ─── Create temp video file ───────────────────────────────────────────────

    fun createVideoFile(): File {
        val dir = File(context.cacheDir, "videos").also { it.mkdirs() }
        return File(dir, "video_${timestamp()}.mp4")
    }

    /**
     * Move completed video from cache to gallery.
     */
    fun saveVideoToGallery(file: File, onResult: (ExportResult) -> Unit) {
        scope.launch {
            try {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Ascify")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("Could not insert video")

                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri
                } else {
                    Uri.fromFile(file)
                }

                withContext(Dispatchers.Main) {
                    onResult(ExportResult.Success(uri))
                }
                file.delete()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(ExportResult.Failure(e.message ?: "Video save failed"))
                }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Could not decode captured image")
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())

    fun release() {
        scope.cancel()
    }
}
