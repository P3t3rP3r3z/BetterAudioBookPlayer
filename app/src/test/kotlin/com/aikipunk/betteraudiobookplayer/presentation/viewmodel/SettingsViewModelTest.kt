package com.aikipunk.betteraudiobookplayer.presentation.viewmodel

import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.testutil.testContext
import com.aikipunk.betteraudiobookplayer.domain.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun `settings setters persist and publish preference flows`() {
        val prefs = UserPreferences(testContext())
        val vm = SettingsViewModel(prefs)

        vm.setThemeMode(ThemeMode.DARK)
        vm.setDynamicColor(true)
        vm.setSkipBackSeconds(15)
        vm.setSkipForwardSeconds(45)
        vm.setRewindOnResume(false)
        vm.setDefaultSpeed(1.25f)
        vm.setAppVolume(125)
        vm.setGradualVolumeFade(true)
        vm.setPauseOnHeadphoneDisconnect(false)
        vm.setSleepTimerDefaultMinutes(20)
        vm.setShakeToExtend(false)

        assertEquals(ThemeMode.DARK, vm.themeMode.value)
        assertTrue(vm.dynamicColor.value)
        assertEquals(15, vm.skipBackSeconds.value)
        assertEquals(45, vm.skipForwardSeconds.value)
        assertFalse(vm.rewindOnResume.value)
        assertEquals(1.25f, vm.defaultSpeed.value)
        assertEquals(125, vm.appVolume.value)
        assertTrue(vm.gradualVolumeFade.value)
        assertFalse(vm.pauseOnHeadphoneDisconnect.value)
        assertEquals(20, vm.sleepTimerDefaultMinutes.value)
        assertFalse(vm.shakeToExtend.value)
    }

    @Test
    fun `app volume and gradual fade flows stay in sync with preferences`() {
        val prefs = UserPreferences(testContext())
        val vm = SettingsViewModel(prefs)

        prefs.setAppVolume(80)
        prefs.setGradualVolumeFade(true)

        assertEquals(80, vm.appVolume.value)
        assertTrue(vm.gradualVolumeFade.value)
    }
}
