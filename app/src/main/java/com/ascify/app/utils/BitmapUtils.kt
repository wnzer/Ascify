package com.ascify.app.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF

object BitmapUtils {

    /**
     * Rotate a bitmap by [degrees] degrees.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Crop [bitmap] to a centered [targetAspectRatio] crop.
     */
    fun centerCrop(bitmap: Bitmap, targetAspectRatio: Float): Bitmap {
        val srcAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        return if (srcAspect > targetAspectRatio) {
            // Source is wider → crop sides
            val newWidth = (bitmap.height * targetAspectRatio).toInt()
            val offsetX = (bitmap.width - newWidth) / 2
            Bitmap.createBitmap(bitmap, offsetX, 0, newWidth, bitmap.height)
        } else {
            // Source is taller → crop top/bottom
            val newHeight = (bitmap.width / targetAspectRatio).toInt()
            val offsetY = (bitmap.height - newHeight) / 2
            Bitmap.createBitmap(bitmap, 0, offsetY, bitmap.width, newHeight)
        }
    }

    /**
     * Flip bitmap horizontally (for front camera mirroring).
     */
    fun flipHorizontal(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    /**
     * Calculate the scaled rect that fits [srcSize] into [dstSize] with [ContentScale.Crop] behavior.
     */
    fun scaleToCropRect(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): RectF {
        val srcAspect = srcWidth.toFloat() / srcHeight
        val dstAspect = dstWidth.toFloat() / dstHeight

        return if (srcAspect > dstAspect) {
            val scaledWidth = dstHeight * srcAspect
            val offsetX = (scaledWidth - dstWidth) / 2
            RectF(-offsetX, 0f, scaledWidth - offsetX, dstHeight.toFloat())
        } else {
            val scaledHeight = dstWidth / srcAspect
            val offsetY = (scaledHeight - dstHeight) / 2
            RectF(0f, -offsetY, dstWidth.toFloat(), scaledHeight - offsetY)
        }
    }

    /**
     * Safe recycle — only recycles mutable, non-recycled bitmaps.
     */
    fun safeRecycle(vararg bitmaps: Bitmap?) {
        bitmaps.forEach { bm ->
            if (bm != null && !bm.isRecycled) bm.recycle()
        }
    }
}
