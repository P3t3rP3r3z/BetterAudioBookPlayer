package com.aikipunk.betteraudiobookplayer.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aikipunk.betteraudiobookplayer.domain.library.LibrarySort
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryViewMode
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepMarker
import com.aikipunk.betteraudiobookplayer.domain.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `defaults expose expected settings and option lists`() {
        val preferences = UserPreferences(context)

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.value)
        assertFalse(preferences.dynamicColor.value)
        assertEquals(UserPreferences.DEFAULT_SKIP_BACK_SECONDS, preferences.skipBackSeconds.value)
        assertEquals(UserPreferences.DEFAULT_SKIP_FORWARD_SECONDS, preferences.skipForwardSeconds.value)
        assertTrue(preferences.rewindOnResume.value)
        assertEquals(1.0f, preferences.defaultSpeed.value, 0.0001f)
        assertTrue(preferences.shakeToExtendSleepTimer.value)
        assertEquals(30, preferences.sleepTimerDefaultMinutes.value)
        assertEquals(100, preferences.appVolume.value)
        assertFalse(preferences.gradualVolumeFade.value)
        assertTrue(preferences.pauseOnHeadphoneDisconnect.value)
        assertEquals(LibrarySort.RECENT, preferences.librarySort.value)
        assertEquals(LibraryViewMode.GRID, preferences.libraryViewMode.value)
        assertTrue(UserPreferences.SKIP_BACK_OPTIONS.contains(10))
        assertTrue(UserPreferences.SKIP_FORWARD_OPTIONS.contains(30))
    }

    @Test
    fun `setters update StateFlow values and persist to new instances`() {
        val preferences = UserPreferences(context)

        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setDynamicColor(true)
        preferences.setSkipBackSeconds(45)
        preferences.setSkipForwardSeconds(60)
        preferences.setRewindOnResume(false)
        preferences.setDefaultSpeed(1.35f)
        preferences.setShakeToExtendSleepTimer(false)
        preferences.setSleepTimerDefaultMinutes(45)
        preferences.setAppVolume(125)
        preferences.setGradualVolumeFade(true)
        preferences.setPauseOnHeadphoneDisconnect(false)

        val reloaded = UserPreferences(context)
        assertEquals(ThemeMode.DARK, reloaded.themeMode.value)
        assertTrue(reloaded.dynamicColor.value)
        assertEquals(45, reloaded.skipBackSeconds.value)
        assertEquals(60, reloaded.skipForwardSeconds.value)
        assertFalse(reloaded.rewindOnResume.value)
        assertEquals(1.35f, reloaded.defaultSpeed.value, 0.0001f)
        assertFalse(reloaded.shakeToExtendSleepTimer.value)
        assertEquals(45, reloaded.sleepTimerDefaultMinutes.value)
        assertEquals(125, reloaded.appVolume.value)
        assertTrue(reloaded.gradualVolumeFade.value)
        assertFalse(reloaded.pauseOnHeadphoneDisconnect.value)
    }

    @Test
    fun `app volume is clamped between zero and one hundred fifty percent`() {
        val preferences = UserPreferences(context)

        preferences.setAppVolume(-20)
        assertEquals(0, preferences.appVolume.value)

        preferences.setAppVolume(175)
        assertEquals(150, preferences.appVolume.value)
    }

    @Test
    fun `invalid stored enum names fall back to defaults`() {
        context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "NO_SUCH_THEME")
            .putString("library_sort", "NO_SUCH_SORT")
            .putString("library_view_mode", "NO_SUCH_VIEW")
            .commit()

        val preferences = UserPreferences(context)

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.value)
        assertEquals(LibrarySort.RECENT, preferences.librarySort.value)
        assertEquals(LibraryViewMode.GRID, preferences.libraryViewMode.value)
    }

    @Test
    fun `sleep marker survives a restart and can be cleared`() {
        UserPreferences(context).setSleepMarker(SleepMarker("book", 3, 125_000))

        val reopened = UserPreferences(context)
        assertEquals(SleepMarker("book", 3, 125_000), reopened.sleepMarker.value)

        reopened.setSleepMarker(null)
        assertNull(UserPreferences(context).sleepMarker.value)
    }
}
