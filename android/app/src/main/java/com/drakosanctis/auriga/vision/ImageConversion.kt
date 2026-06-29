package com.drakosanctis.auriga.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Converts a CameraX ImageProxy (YUV_420_888 format) into an ARGB Bitmap
 * suitable for MediaPipe's BitmapImageBuilder, and applies the correct
 * rotation so detection coordinates line up with what's actually on screen.
 *
 * This conversion runs on the analysis executor thread (set up in
 * CameraEngine), not the main thread, since it does real work per frame.
 */
object ImageConversion {

    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yuvBitmap = yuv420ToBitmap(imageProxy) ?: return null
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees == 0) {
                yuvBitmap
            } else {
                rotateBitmap(yuvBitmap, rotationDegrees)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun yuv420ToBitmap(imageProxy: ImageProxy): Bitmap? {
        if (imageProxy.format != ImageFormat.YUV_420_888) return null

        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            90,
            outputStream
        )
        val jpegBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
