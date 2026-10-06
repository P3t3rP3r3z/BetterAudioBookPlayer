package com.aikipunk.betteraudiobookplayer.domain.model

/** A place audio can play: the phone speaker, wired/USB headphones, a Bluetooth device, etc. */
data class AudioOutput(
    val id: Int,
    val name: String,
    val kind: Kind,
) {
    enum class Kind { PHONE_SPEAKER, WIRED, USB, BLUETOOTH, HEARING_AID, AUX, HDMI }
}
