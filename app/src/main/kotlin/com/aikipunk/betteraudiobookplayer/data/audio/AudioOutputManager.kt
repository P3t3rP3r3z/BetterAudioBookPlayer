package com.aikipunk.betteraudiobookplayer.data.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.aikipunk.betteraudiobookplayer.domain.model.AudioOutput
import com.aikipunk.betteraudiobookplayer.domain.model.AudioOutput.Kind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks connected audio outputs and which one playback uses. Choosing an output only
 * affects this app's audio; the choice is dropped when that device disconnects.
 */
@OptIn(UnstableApi::class)
@Singleton
class AudioOutputManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: ExoPlayerAdapter,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var devices: Map<Int, AudioDeviceInfo> = emptyMap()
    private var preferredId: Int? = null

    private val _outputs = MutableStateFlow<List<AudioOutput>>(emptyList())
    val outputs: StateFlow<List<AudioOutput>> = _outputs.asStateFlow()

    private val _current = MutableStateFlow<AudioOutput?>(null)
    val current: StateFlow<AudioOutput?> = _current.asStateFlow()

    init {
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
            },
            Handler(Looper.getMainLooper()),
        )
        refresh()
    }

    /** Re-reads connected devices and the active route (e.g. after the system switcher was used). */
    fun refresh() {
        val usable = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull { info -> kindOf(info.type)?.let { info to it } }
            // A device can be listed once per profile; keep one entry per physical device
            .distinctBy { (info, kind) -> kind to (info.addressOrEmpty().ifEmpty { info.productName?.toString() }) }
        devices = usable.associate { (info, _) -> info.id to info }
        val list = usable
            .map { (info, kind) -> AudioOutput(info.id, displayName(info, kind), kind) }
            .sortedBy { it.kind.ordinal }
        _outputs.value = list

        if (preferredId != null && preferredId !in devices) {
            preferredId = null
            player.setPreferredAudioDevice(null)
        }
        _current.value = list.firstOrNull { it.id == preferredId }
            ?: systemRoutedOutput(list)
            ?: list.firstOrNull { it.kind == Kind.PHONE_SPEAKER }
    }

    fun select(output: AudioOutput) {
        val device = devices[output.id] ?: return
        preferredId = output.id
        player.setPreferredAudioDevice(device)
        _current.value = output
    }

    /** Hands routing back to the system (used before opening the system output switcher). */
    fun clearPreference() {
        preferredId = null
        player.setPreferredAudioDevice(null)
    }

    /** Opens Android's own media output switcher. Returns false if this device has none. */
    fun showSystemOutputSwitcher(): Boolean {
        clearPreference()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return MediaRouter2.getInstance(context).showSystemOutputSwitcher()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(ACTION_SYSTEMUI_OUTPUT_DIALOG)
                .setPackage(SYSTEMUI_PACKAGE)
                .putExtra(EXTRA_PACKAGE_NAME, context.packageName)
            context.sendBroadcast(intent)
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(ACTION_SETTINGS_OUTPUT_PANEL)
                .putExtra(EXTRA_PACKAGE_NAME, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    fun bluetoothSettingsIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    private fun systemRoutedOutput(list: List<AudioOutput>): AudioOutput? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            val routed = audioManager.getAudioDevicesForAttributes(attrs).firstOrNull()
            if (routed != null) {
                val match = devices.values.firstOrNull { it.type == routed.type && it.address == routed.address }
                    ?: devices.values.firstOrNull { it.type == routed.type }
                list.firstOrNull { it.id == match?.id }?.let { return it }
            }
        }
        // Older Android: media goes to the most "personal" connected output
        val priority = listOf(Kind.BLUETOOTH, Kind.HEARING_AID, Kind.WIRED, Kind.USB, Kind.AUX, Kind.HDMI)
        return priority.firstNotNullOfOrNull { kind -> list.firstOrNull { it.kind == kind } }
    }

    private fun displayName(info: AudioDeviceInfo, kind: Kind): String {
        val product = info.productName?.toString()?.trim().orEmpty()
        return when (kind) {
            Kind.PHONE_SPEAKER -> "Phone speaker"
            Kind.WIRED -> "Wired headphones"
            Kind.AUX -> "Aux"
            Kind.HDMI -> "HDMI"
            Kind.USB -> product.ifEmpty { "USB audio" }
            Kind.BLUETOOTH -> product.ifEmpty { "Bluetooth" }
            Kind.HEARING_AID -> product.ifEmpty { "Hearing aid" }
        }
    }

    private fun kindOf(type: Int): Kind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Kind.PHONE_SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Kind.WIRED
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> Kind.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER -> Kind.BLUETOOTH
        AudioDeviceInfo.TYPE_HEARING_AID -> Kind.HEARING_AID
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE -> Kind.AUX
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> Kind.HDMI
        else -> null
    }

    private companion object {
        // AudioDeviceInfo.TYPE_BLE_HEADSET / TYPE_BLE_SPEAKER (API 31); values are stable
        const val TYPE_BLE_HEADSET = 26
        const val TYPE_BLE_SPEAKER = 27
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        const val ACTION_SYSTEMUI_OUTPUT_DIALOG = "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"
        const val ACTION_SETTINGS_OUTPUT_PANEL = "com.android.settings.panel.action.MEDIA_OUTPUT"
        const val EXTRA_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"
    }
}

// AudioDeviceInfo.address arrived in API 28; older devices dedupe by product name alone
private fun AudioDeviceInfo.addressOrEmpty(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) address else ""
