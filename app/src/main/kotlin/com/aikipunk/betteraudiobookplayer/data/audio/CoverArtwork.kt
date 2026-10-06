package com.aikipunk.betteraudiobookplayer.data.audio

import androidx.core.graphics.scale
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream

/** Downscales a cover image to a small JPEG suitable for media session metadata. */
internal object CoverArtwork {
    private const val TAG = "CoverArtwork"
    private const val MAX_SIZE_PX = 512

    fun encode(context: Context, uri: Uri): ByteArray? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIZE_PX && bounds.outHeight / (sample * 2) >= MAX_SIZE_PX) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        decoded?.let { bitmap ->
            val scale = MAX_SIZE_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
            val sized = if (scale < 1f) {
                bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
            } else {
                bitmap
            }
            ByteArrayOutputStream().use { out ->
                sized.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
        }
    } catch (e: Exception) {
        // The media controls then fall back to the cover URI (visible in-app, maybe not in System UI)
        Log.w(TAG, "Couldn't load cover art", e)
        null
    }
}
