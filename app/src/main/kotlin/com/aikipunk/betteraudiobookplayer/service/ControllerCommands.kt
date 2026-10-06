package com.aikipunk.betteraudiobookplayer.service

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * Player commands granted to controllers connecting to the playback session.
 *
 * Media3 gives controllers it can't verify (e.g. the Wear OS companion that drives watch media
 * controls) read-only access by default, so their play/pause buttons do nothing. They get the
 * playback controls as well; only trusted controllers (system UI, Android Auto, this app) can
 * replace the loaded book.
 */
@UnstableApi
object ControllerCommands {
    private val UNTRUSTED_PLAYER_COMMANDS: Player.Commands =
        MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_PLAYER_COMMANDS.buildUpon()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_SPEED_AND_PITCH,
            )
            .build()

    fun playerCommands(trusted: Boolean): Player.Commands =
        if (trusted) MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS else UNTRUSTED_PLAYER_COMMANDS
}
