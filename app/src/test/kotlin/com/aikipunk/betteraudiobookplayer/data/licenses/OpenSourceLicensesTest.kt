package com.aikipunk.betteraudiobookplayer.data.licenses

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OpenSourceLicensesTest {
    @Test
    fun `load returns display-safe license list`() = runTest {
        val libraries = OpenSourceLicenses.load(ApplicationProvider.getApplicationContext())

        assertTrue(libraries.all { it.name.isNotBlank() })
    }
}
