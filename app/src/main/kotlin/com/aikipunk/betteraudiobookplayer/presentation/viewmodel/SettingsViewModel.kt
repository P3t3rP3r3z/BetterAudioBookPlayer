package com.aikipunk.betteraudiobookplayer.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {
    val themeMode = prefs.themeMode
    val dynamicColor = prefs.dynamicColor
    val skipBackSeconds = prefs.skipBackSeconds
    val skipForwardSeconds = prefs.skipForwardSeconds
    val rewindOnResume = prefs.rewindOnResume
    val defaultSpeed = prefs.defaultSpeed
    val appVolume = prefs.appVolume
    val gradualVolumeFade = prefs.gradualVolumeFade
    val pauseOnHeadphoneDisconnect = prefs.pauseOnHeadphoneDisconnect
    val sleepTimerDefaultMinutes = prefs.sleepTimerDefaultMinutes
    val shakeToExtend = prefs.shakeToExtendSleepTimer

    fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = prefs.setDynamicColor(enabled)
    fun setSkipBackSeconds(seconds: Int) = prefs.setSkipBackSeconds(seconds)
    fun setSkipForwardSeconds(seconds: Int) = prefs.setSkipForwardSeconds(seconds)
    fun setRewindOnResume(enabled: Boolean) = prefs.setRewindOnResume(enabled)
    fun setDefaultSpeed(speed: Float) = prefs.setDefaultSpeed(speed)
    fun setAppVolume(percent: Int) = prefs.setAppVolume(percent)
    fun setGradualVolumeFade(enabled: Boolean) = prefs.setGradualVolumeFade(enabled)
    fun setPauseOnHeadphoneDisconnect(enabled: Boolean) = prefs.setPauseOnHeadphoneDisconnect(enabled)
    fun setSleepTimerDefaultMinutes(minutes: Int) = prefs.setSleepTimerDefaultMinutes(minutes)
    fun setShakeToExtend(enabled: Boolean) = prefs.setShakeToExtendSleepTimer(enabled)
}
