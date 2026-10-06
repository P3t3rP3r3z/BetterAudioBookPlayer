package com.aikipunk.betteraudiobookplayer.data.audio

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverArtworkTest {
    @Test
    fun `encode returns compressed jpeg for readable image`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File("build/test-files/cover-artwork/cover.png").apply { parentFile?.mkdirs() }
        val bitmap = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val bytes = CoverArtwork.encode(context, Uri.fromFile(file))

        assertNotNull(bytes)
        assertTrue(bytes?.isNotEmpty() == true)
    }

    @Test
    fun `encode returns null for unreadable image`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertNull(CoverArtwork.encode(context, Uri.fromFile(File("build/test-files/cover-artwork/missing.jpg"))))
    }
}
