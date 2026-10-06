package com.aikipunk.betteraudiobookplayer.data.audio

import android.media.AudioDeviceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import com.aikipunk.betteraudiobookplayer.domain.model.AudioOutput
import io.mockk.verify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AudioOutputManagerTest {
    @Test
    fun `bluetoothSettingsIntent opens bluetooth settings`() {
        val manager = AudioOutputManager(ApplicationProvider.getApplicationContext(), mockk(relaxed = true))

        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, manager.bluetoothSettingsIntent().action)
    }

    @Test
    fun `showSystemOutputSwitcher returns false on devices without output panel`() {
        val manager = AudioOutputManager(ApplicationProvider.getApplicationContext(), mockk(relaxed = true))

        assertFalse(manager.showSystemOutputSwitcher())
    }

    @Test
    fun `refresh maps and sorts supported output devices with speaker as current fallback`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audioManager).setOutputDevices(
            listOf(
                device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
                device(AudioDeviceInfo.TYPE_USB_HEADSET),
                device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
                device(AudioDeviceInfo.TYPE_WIRED_HEADPHONES),
            ),
        )

        val manager = AudioOutputManager(context, mockk(relaxed = true))

        assertEquals(
            listOf(
                AudioOutput.Kind.PHONE_SPEAKER,
                AudioOutput.Kind.WIRED,
                AudioOutput.Kind.USB,
                AudioOutput.Kind.BLUETOOTH,
            ),
            manager.outputs.value.map { it.kind },
        )
        assertEquals(AudioOutput.Kind.BLUETOOTH, manager.current.value?.kind)
    }

    @Test
    @Config(sdk = [33])
    fun `refresh maps remaining device kinds and honors system routed output`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val usb = device(AudioDeviceInfo.TYPE_USB_DEVICE)
        val shadow = shadowOf(audioManager)
        shadow.setOutputDevices(
            listOf(
                device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
                usb,
                device(AudioDeviceInfo.TYPE_HEARING_AID),
                device(AudioDeviceInfo.TYPE_LINE_ANALOG),
                device(AudioDeviceInfo.TYPE_HDMI),
            ),
        )
        shadow.setAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(), ImmutableList.of(usb))

        val manager = AudioOutputManager(context, mockk(relaxed = true))

        assertEquals(
            listOf(
                AudioOutput.Kind.PHONE_SPEAKER,
                AudioOutput.Kind.USB,
                AudioOutput.Kind.HEARING_AID,
                AudioOutput.Kind.AUX,
                AudioOutput.Kind.HDMI,
            ),
            manager.outputs.value.map { it.kind },
        )
        assertEquals(AudioOutput.Kind.USB, manager.current.value?.kind)
    }

    @Test
    fun `select stores preferred device and clearPreference hands routing back to system`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audioManager).setOutputDevices(listOf(device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), device(AudioDeviceInfo.TYPE_WIRED_HEADSET)))
        val player = mockk<ExoPlayerAdapter>(relaxed = true)
        val manager = AudioOutputManager(context, player)
        val wired = manager.outputs.value.first { it.kind == AudioOutput.Kind.WIRED }

        manager.select(wired)
        assertEquals(wired, manager.current.value)
        manager.clearPreference()

        verify { player.setPreferredAudioDevice(any()) }
        verify { player.setPreferredAudioDevice(null) }
    }

    @Test
    fun `refresh drops selected output when device disconnects`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        val wiredDevice = device(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        val shadow = shadowOf(audioManager)
        shadow.setOutputDevices(listOf(speaker, wiredDevice))
        val player = mockk<ExoPlayerAdapter>(relaxed = true)
        val manager = AudioOutputManager(context, player)
        val wired = manager.outputs.value.first { it.kind == AudioOutput.Kind.WIRED }
        manager.select(wired)

        shadow.setOutputDevices(listOf(speaker))
        manager.refresh()

        assertEquals(AudioOutput.Kind.PHONE_SPEAKER, manager.current.value?.kind)
        verify { player.setPreferredAudioDevice(null) }
    }

    @Test
    @Config(sdk = [31])
    fun `showSystemOutputSwitcher sends SystemUI broadcast on Android S`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = AudioOutputManager(context, mockk(relaxed = true))

        assertTrue(manager.showSystemOutputSwitcher())

        val broadcast = shadowOf(context as android.app.Application).broadcastIntents.single()
        assertEquals("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG", broadcast.action)
        assertEquals("com.android.systemui", broadcast.`package`)
        assertEquals(context.packageName, broadcast.getStringExtra("com.android.settings.panel.extra.PACKAGE_NAME"))
    }

    private fun device(type: Int): AudioDeviceInfo =
        AudioDeviceInfoBuilder.newBuilder().setType(type).build().also { info ->
            val port = ReflectionHelpers.getField<Any>(info, "mPort")
            val handle = ReflectionHelpers.getField<Any>(port, "mHandle")
            ReflectionHelpers.setField(handle, "mId", nextDeviceId++)
            ReflectionHelpers.setField(port, "mName", "Device $type")
            ReflectionHelpers.setField(port, "mAddress", "address-$type-$nextDeviceId")
        }

    private companion object {
        var nextDeviceId = 1
    }
}
