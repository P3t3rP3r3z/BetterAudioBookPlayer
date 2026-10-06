package com.aikipunk.betteraudiobookplayer.domain.playback

/** Playback position captured when the sleep timer was last started or extended. */
data class SleepMarker(
    val audiobookId: String,
    val itemIndex: Int,
    val positionMs: Long,
) {
    internal fun encode(): String = "$itemIndex|$positionMs|$audiobookId"

    companion object {
        internal fun decode(value: String?): SleepMarker? {
            val parts = value?.split('|', limit = 3) ?: return null
            if (parts.size != 3 || parts[2].isEmpty()) return null
            val index = parts[0].toIntOrNull() ?: return null
            val position = parts[1].toLongOrNull() ?: return null
            return SleepMarker(parts[2], index, position)
        }
    }
}
