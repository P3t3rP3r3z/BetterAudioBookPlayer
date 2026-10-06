package com.aikipunk.betteraudiobookplayer.service

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControllerCommandsTest {

    @Test
    fun `trusted controllers get every default player command`() {
        assertEquals(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS, ControllerCommands.playerCommands(trusted = true))
    }

    @Test
    fun `untrusted controllers such as a watch companion can use playback controls`() {
        val commands = ControllerCommands.playerCommands(trusted = false)

        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
        ).forEach { assertTrue("missing command $it", commands.contains(it)) }
    }

    @Test
    fun `untrusted controllers cannot replace the loaded book`() {
        val commands = ControllerCommands.playerCommands(trusted = false)

        assertFalse(commands.contains(Player.COMMAND_SET_MEDIA_ITEM))
        assertFalse(commands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
    }
}
