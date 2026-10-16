package com.zoomers.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.zoomers.app.core.data.NewPostImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Turns a picked image [Uri] into upload-ready JPEG bytes: downscaled to a
 * sensible maximum edge and re-encoded, so uploads are small and always a
 * supported type regardless of the source format (incl. HEIC from the camera).
 */
object ImageCompressor {
    private const val MaxEdge = 2048
    private const val Quality = 85

    suspend fun fromUri(context: Context, uri: Uri): NewPostImage? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // Read dimensions without allocating the full bitmap.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return@withContext null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // Coarse downsample while decoding to keep memory bounded.
        var sample = 1
        val maxSrcEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSrcEdge / sample > MaxEdge * 2) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@withContext null

        // Fine scale to the exact maximum edge.
        val scaled = run {
            val maxEdge = maxOf(decoded.width, decoded.height)
            if (maxEdge > MaxEdge) {
                val ratio = MaxEdge.toFloat() / maxEdge
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true,
                ).also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, Quality, out)
        val result = NewPostImage(
            bytes = out.toByteArray(),
            contentType = "image/jpeg",
            width = scaled.width,
            height = scaled.height,
        )
        scaled.recycle()
        result
    }
}
