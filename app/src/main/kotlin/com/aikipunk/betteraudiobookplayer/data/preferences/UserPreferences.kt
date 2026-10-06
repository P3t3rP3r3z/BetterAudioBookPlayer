package com.aikipunk.betteraudiobookplayer.data.preferences

import androidx.core.content.edit
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import android.content.Context
import com.aikipunk.betteraudiobookplayer.domain.library.LibrarySort
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryViewMode
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepMarker
import com.aikipunk.betteraudiobookplayer.domain.settings.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide user settings, edited on the Settings tab. */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Follows the system light/dark setting unless the user overrides it. */
    val themeMode = enumPref(KEY_THEME_MODE, ThemeMode.SYSTEM)
    fun setThemeMode(mode: ThemeMode) = themeMode.set(mode)

    /** Material You wallpaper colors instead of the app palette (Android 12+). */
    val dynamicColor = boolPref(KEY_DYNAMIC_COLOR, false)
    fun setDynamicColor(enabled: Boolean) = dynamicColor.set(enabled)


    val skipBackSeconds = intPref(KEY_SKIP_BACK, DEFAULT_SKIP_BACK_SECONDS)
    fun setSkipBackSeconds(seconds: Int) = skipBackSeconds.set(seconds)

    val skipForwardSeconds = intPref(KEY_SKIP_FORWARD, DEFAULT_SKIP_FORWARD_SECONDS)
    fun setSkipForwardSeconds(seconds: Int) = skipForwardSeconds.set(seconds)

    /** Rewind a few seconds when resuming after a pause (longer pauses rewind more). */
    val rewindOnResume = boolPref(KEY_REWIND_ON_RESUME, true)
    fun setRewindOnResume(enabled: Boolean) = rewindOnResume.set(enabled)

    /** Speed used for books that have never been played. */
    val defaultSpeed = floatPref(KEY_DEFAULT_SPEED, 1.0f)
    fun setDefaultSpeed(speed: Float) = defaultSpeed.set(speed)

    val shakeToExtendSleepTimer = boolPref(KEY_SHAKE_TO_EXTEND, true)
    fun setShakeToExtendSleepTimer(enabled: Boolean) = shakeToExtendSleepTimer.set(enabled)

    val sleepTimerDefaultMinutes = intPref(KEY_SLEEP_DEFAULT_MINUTES, 30)
    fun setSleepTimerDefaultMinutes(minutes: Int) = sleepTimerDefaultMinutes.set(minutes)

    /** App-level volume multiplier (0–150%, where 100% = system volume). */
    val appVolume = intPref(KEY_APP_VOLUME, 100)
    fun setAppVolume(percent: Int) = appVolume.set(percent.coerceIn(0, 150))

    /** Gradually fade volume to the final setting over the sleep timer duration. */
    val gradualVolumeFade = boolPref(KEY_GRADUAL_VOLUME_FADE, false)
    fun setGradualVolumeFade(enabled: Boolean) = gradualVolumeFade.set(enabled)

    val pauseOnHeadphoneDisconnect = boolPref(KEY_PAUSE_ON_DISCONNECT, true)

    /** Bookshelf sort order and grid/list layout. */
    val librarySort = enumPref(KEY_LIBRARY_SORT, LibrarySort.RECENT)

    val libraryViewMode = enumPref(KEY_LIBRARY_VIEW_MODE, LibraryViewMode.GRID)
    fun setPauseOnHeadphoneDisconnect(enabled: Boolean) = pauseOnHeadphoneDisconnect.set(enabled)

    /** Where the sleep timer was last started, so a listener who dozed off can find their place. */
    val sleepMarker = PrefFlow(SleepMarker.decode(prefs.getString(KEY_SLEEP_MARKER, null))) { marker ->
        prefs.edit { if (marker == null) remove(KEY_SLEEP_MARKER) else putString(KEY_SLEEP_MARKER, marker.encode()) }
    }
    fun setSleepMarker(marker: SleepMarker?) = sleepMarker.set(marker)

    private fun boolPref(key: String, default: Boolean) =
        PrefFlow(prefs.getBoolean(key, default)) { prefs.edit { putBoolean(key, it) } }

    private fun intPref(key: String, default: Int) =
        PrefFlow(prefs.getInt(key, default)) { prefs.edit { putInt(key, it) } }

    private fun floatPref(key: String, default: Float) =
        PrefFlow(prefs.getFloat(key, default)) { prefs.edit { putFloat(key, it) } }

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): PrefFlow<E> {
        val stored = prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
        return PrefFlow(stored ?: default) { prefs.edit { putString(key, it.name) } }
    }

    companion object {
        const val DEFAULT_SKIP_BACK_SECONDS = 10
        const val DEFAULT_SKIP_FORWARD_SECONDS = 30
        val SKIP_BACK_OPTIONS = listOf(5, 10, 15, 30, 45, 60)
        val SKIP_FORWARD_OPTIONS = listOf(10, 15, 30, 45, 60)

        private const val PREFS_NAME = "user_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_SKIP_BACK = "skip_back_seconds"
        private const val KEY_SKIP_FORWARD = "skip_forward_seconds"
        private const val KEY_REWIND_ON_RESUME = "rewind_on_resume"
        private const val KEY_DEFAULT_SPEED = "default_speed"
        private const val KEY_SHAKE_TO_EXTEND = "shake_to_extend_sleep_timer"
        private const val KEY_SLEEP_DEFAULT_MINUTES = "sleep_timer_default_minutes"
        private const val KEY_APP_VOLUME = "app_volume"
        private const val KEY_GRADUAL_VOLUME_FADE = "gradual_volume_fade"
        private const val KEY_PAUSE_ON_DISCONNECT = "pause_on_headphone_disconnect"
        private const val KEY_LIBRARY_SORT = "library_sort"
        private const val KEY_LIBRARY_VIEW_MODE = "library_view_mode"
        private const val KEY_SLEEP_MARKER = "sleep_marker"
    }
}

/** Read-only StateFlow of a stored setting; [set] persists and publishes a new value. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class PrefFlow<T> private constructor(
    private val state: MutableStateFlow<T>,
    private val write: (T) -> Unit,
) : StateFlow<T> by state {
    internal constructor(initial: T, write: (T) -> Unit) : this(MutableStateFlow(initial), write)

    fun set(value: T) {
        write(value)
        state.value = value
    }
}
