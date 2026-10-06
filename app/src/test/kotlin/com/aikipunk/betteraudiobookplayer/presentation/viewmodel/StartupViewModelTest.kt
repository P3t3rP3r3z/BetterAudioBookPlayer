package com.aikipunk.betteraudiobookplayer.presentation.viewmodel

import com.aikipunk.betteraudiobookplayer.data.transfer.ShelfBackup
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBook
import com.aikipunk.betteraudiobookplayer.testutil.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `init emits Ready with last valid audiobook id and restorable count after backup is ready`() = runTest {
        val playbackStates = mockk<PlaybackStateRepository> {
            coEvery { getMostRecentPlaybackState() } returns PlaybackState("book", "track", lastPlayedAt = LocalDateTime.now())
        }
        val books = mockk<AudiobookRepository> {
            coEvery { getAudiobookById("book") } returns Audiobook("book", "Book", null, 0, null, "file://book")
        }
        val backup = mockk<ShelfBackup> {
            coEvery { awaitReady() } returns Unit
            every { pending } returns MutableStateFlow(listOf(SavedBook("A"), SavedBook("B")))
            every { discardPending() } just Runs
        }

        val vm = StartupViewModel(playbackStates, books, backup)
        advanceUntilIdle()

        assertEquals(StartupState.Ready("book", 2), vm.state.value)
    }

    @Test
    fun `init drops stale most recent id and discardRestore delegates`() = runTest {
        val playbackStates = mockk<PlaybackStateRepository> {
            coEvery { getMostRecentPlaybackState() } returns PlaybackState("missing", "track")
        }
        val books = mockk<AudiobookRepository> {
            coEvery { getAudiobookById("missing") } returns null
        }
        val backup = mockk<ShelfBackup> {
            coEvery { awaitReady() } returns Unit
            every { pending } returns MutableStateFlow(emptyList())
            every { discardPending() } just Runs
        }

        val vm = StartupViewModel(playbackStates, books, backup)
        advanceUntilIdle()
        vm.discardRestore()

        assertEquals(StartupState.Ready(null, 0), vm.state.value)
        verify { backup.discardPending() }
    }
}
