package com.rork.ananego.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Prepares a captured document photo (DNI, tarjeta, placa) for upload: it
 * downscales the image and re-encodes it as a compact JPEG so one document
 * never weighs more than a few hundred kilobytes on the wire.
 */
object DocumentPhotos {

    private const val MAX_EDGE_PX = 1280
    private const val JPEG_QUALITY = 72

    /** Returns the compressed JPEG bytes, or null when the image can't be read. */
    fun compressToJpeg(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsOk = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } != null
        if (!boundsOk || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_EDGE_PX ||
            bounds.outHeight / (sample * 2) >= MAX_EDGE_PX
        ) {
            sample *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val scaled = scaleDown(decoded)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()
        return output.toByteArray()
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val largestEdge = maxOf(bitmap.width, bitmap.height)
        if (largestEdge <= MAX_EDGE_PX) return bitmap
        val scale = MAX_EDGE_PX.toFloat() / largestEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
