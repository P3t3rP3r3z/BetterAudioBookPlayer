package com.aikipunk.betteraudiobookplayer.service

import androidx.media3.session.SessionError
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.aikipunk.betteraudiobookplayer.R
import com.aikipunk.betteraudiobookplayer.data.audio.ExoPlayerAdapter
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.playback.BookLauncher
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepTimer
import com.aikipunk.betteraudiobookplayer.domain.usecase.BookmarkCurrentPositionUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Exposes the app's player to the system via a Media3 [MediaLibraryService].
 *
 * Media3 handles the media notification, lock-screen, Bluetooth/headset controls and
 * foreground state. The library session also lets Android Auto browse and start books.
 * System controls show rewind / fast-forward (by the user's skip intervals) and add-bookmark.
 */
@AndroidEntryPoint
@UnstableApi
class AudiobookPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var playerAdapter: ExoPlayerAdapter

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var bookmarkCurrentPosition: BookmarkCurrentPositionUseCase

    @Inject
    lateinit var bookLauncher: BookLauncher

    @Inject
    lateinit var browseTree: MediaBrowseTree

    /** Held so its sleep-marker observer runs during playback without the app UI (e.g. Android Auto). */
    @Inject
    lateinit var sleepTimer: SleepTimer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var skipPlayer: AudiobookSkipPlayer

    override fun onCreate() {
        super.onCreate()
        skipPlayer = AudiobookSkipPlayer(playerAdapter.player, userPreferences, ::openBook)
        mediaSession = MediaLibrarySession.Builder(this, skipPlayer, LibraryCallback())
            .apply { openAppIntent()?.let { setSessionActivity(it) } }
            .setCustomLayout(controlButtons())
            .setSessionExtras(
                Bundle().apply {
                    // Let rewind / fast-forward take the previous / next slots (System UI, Android Auto)
                    putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, false)
                    putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, false)
                },
            )
            .build()
            // Register now so the media notification is managed even before any controller connects
            .also { addSession(it) }

        serviceScope.launch {
            // Button icons show the skip interval
            combine(userPreferences.skipBackSeconds, userPreferences.skipForwardSeconds) { _, _ -> }
                .drop(1)
                .collect { mediaSession?.setCustomLayout(controlButtons()) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    /** User swiped the app away from Recents: treat as quitting. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.pause()
            // Only the session is released; the player is an app-wide singleton
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Loads a book chosen from a system browser (Android Auto) at its saved position. */
    private fun openBook(audiobookId: String) {
        serviceScope.launch {
            var loaded = false
            try {
                loaded = bookLauncher.open(audiobookId).startTrack != null
            } finally {
                val play = skipPlayer.finishOpening()
                if (loaded && play) playerAdapter.play()
            }
        }
    }

    private fun controlButtons(): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder(skipBackIcon(userPreferences.skipBackSeconds.value))
            .setDisplayName("Rewind ${userPreferences.skipBackSeconds.value} seconds")
            .setSessionCommand(REWIND_COMMAND)
            .build(),
        CommandButton.Builder(skipForwardIcon(userPreferences.skipForwardSeconds.value))
            .setDisplayName("Fast forward ${userPreferences.skipForwardSeconds.value} seconds")
            .setSessionCommand(FORWARD_COMMAND)
            .build(),
        CommandButton.Builder(CommandButton.ICON_BOOKMARK_UNFILLED)
            .setDisplayName("Add bookmark")
            .setCustomIconResId(R.drawable.ic_bookmark_add)
            .setSessionCommand(BOOKMARK_COMMAND)
            .build(),
    )

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(BOOKMARK_COMMAND)
                .add(REWIND_COMMAND)
                .add(FORWARD_COMMAND)
                .build()
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(ControllerCommands.playerCommands(controller.isTrusted))
            if (session.isMediaNotificationController(controller) || session.isAutomotiveController(controller)) {
                // Without previous/next, the notification, lock screen and car show the
                // rewind / fast-forward buttons in those slots. Headset keys are handled in onMediaButtonEvent.
                builder.setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .removeAll(
                            Player.COMMAND_SEEK_TO_PREVIOUS,
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                            Player.COMMAND_SEEK_TO_NEXT,
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        )
                        .build(),
                )
            }
            return builder.build()
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                ?: return false
            val forward = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> true
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> false
                else -> return false
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (forward) skipPlayer.seekForward() else skipPlayer.seekBack()
            }
            return true
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_REWIND -> skipPlayer.seekBack()
                ACTION_FORWARD -> skipPlayer.seekForward()
                ACTION_ADD_BOOKMARK -> serviceScope.launch {
                    val added = bookmarkCurrentPosition()
                    val text = if (added != null) "Bookmark added" else "Nothing is playing"
                    Toast.makeText(this@AudiobookPlaybackService, text, Toast.LENGTH_SHORT).show()
                }
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browseTree.root(), browseTree.rootParams()))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val children = browseTree.children(parentId)
                ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            LibraryResult.ofItemList(children.page(page, pageSize), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            browseTree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.launch {
                session.notifySearchResultChanged(browser, query, browseTree.search(query).size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(browseTree.search(query).page(page, pageSize), params)
        }

        /**
         * Browsers send only the book's media ID (or a voice search); the player turns it into the
         * book's chapter playlist, so the item is passed through with a search resolved to a book.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            mediaItems.mapNotNull { item ->
                when {
                    MediaBrowseTree.bookIdOf(item.mediaId) != null -> item
                    item.requestMetadata.searchQuery != null ->
                        browseTree.bestMatch(item.requestMetadata.searchQuery.orEmpty())
                    else -> null
                }
            }.toMutableList()
        }

        /** "Play" on a headset or in the car with nothing loaded resumes the last book. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val item = bookLauncher.mostRecentBookId()?.let { browseTree.item(MediaBrowseTree.bookMediaId(it)) }
                ?: throw UnsupportedOperationException("Nothing to resume")
            MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET)
        }
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    private fun <T> List<T>.page(page: Int, pageSize: Int): ImmutableList<T> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return ImmutableList.copyOf(this)
        val from = (page.toLong() * pageSize).coerceAtMost(size.toLong()).toInt()
        return ImmutableList.copyOf(subList(from, (from + pageSize).coerceAtMost(size)))
    }

    private companion object {
        const val ACTION_ADD_BOOKMARK = "com.aikipunk.betteraudiobookplayer.ADD_BOOKMARK"
        const val ACTION_REWIND = "com.aikipunk.betteraudiobookplayer.REWIND"
        const val ACTION_FORWARD = "com.aikipunk.betteraudiobookplayer.FORWARD"
        val BOOKMARK_COMMAND = SessionCommand(ACTION_ADD_BOOKMARK, Bundle.EMPTY)
        val REWIND_COMMAND = SessionCommand(ACTION_REWIND, Bundle.EMPTY)
        val FORWARD_COMMAND = SessionCommand(ACTION_FORWARD, Bundle.EMPTY)

        fun skipBackIcon(seconds: Int): Int = when (seconds) {
            5 -> CommandButton.ICON_SKIP_BACK_5
            10 -> CommandButton.ICON_SKIP_BACK_10
            15 -> CommandButton.ICON_SKIP_BACK_15
            30 -> CommandButton.ICON_SKIP_BACK_30
            else -> CommandButton.ICON_SKIP_BACK
        }

        fun skipForwardIcon(seconds: Int): Int = when (seconds) {
            5 -> CommandButton.ICON_SKIP_FORWARD_5
            10 -> CommandButton.ICON_SKIP_FORWARD_10
            15 -> CommandButton.ICON_SKIP_FORWARD_15
            30 -> CommandButton.ICON_SKIP_FORWARD_30
            else -> CommandButton.ICON_SKIP_FORWARD
        }
    }

    /** Tapping the notification brings the existing app task to the front. */
    private fun openAppIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Audiobook convention: "previous"/"next" skip back / forward by the user's skip intervals
 * instead of changing track. Books picked in a system browser (Android Auto) arrive as a
 * single `book:<id>` item and are handed to [onOpenBook]; play requests wait until it has loaded.
 */
@UnstableApi
private class AudiobookSkipPlayer(
    player: Player,
    private val prefs: UserPreferences,
    private val onOpenBook: (String) -> Unit,
) : ForwardingPlayer(player) {

    private var opening = false
    private var playAfterOpen = false

    /** Ends a pending book open; returns whether playback was requested meanwhile. */
    fun finishOpening(): Boolean {
        opening = false
        return playAfterOpen.also { playAfterOpen = false }
    }

    private fun interceptBook(items: List<MediaItem>): Boolean {
        val bookId = items.firstOrNull()?.let { MediaBrowseTree.bookIdOf(it.mediaId) } ?: return false
        opening = true
        playAfterOpen = false
        onOpenBook(bookId)
        return true
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        if (!interceptBook(mediaItems)) super.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        if (!interceptBook(mediaItems)) super.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (!interceptBook(mediaItems)) super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        if (!interceptBook(listOf(mediaItem))) super.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        if (!interceptBook(listOf(mediaItem))) super.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        if (!interceptBook(listOf(mediaItem))) super.setMediaItem(mediaItem, resetPosition)
    }

    override fun prepare() {
        if (!opening) super.prepare()
    }

    override fun play() {
        if (opening) playAfterOpen = true else super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (opening) playAfterOpen = playWhenReady else super.setPlayWhenReady(playWhenReady)
    }

    override fun getSeekBackIncrement(): Long = prefs.skipBackSeconds.value * 1_000L

    override fun getSeekForwardIncrement(): Long = prefs.skipForwardSeconds.value * 1_000L

    override fun seekBack() = seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0L))

    override fun seekForward() {
        val target = currentPosition + seekForwardIncrement
        seekTo(if (duration != C.TIME_UNSET) target.coerceAtMost(duration) else target)
    }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == Player.COMMAND_SEEK_TO_NEXT ||
            super.isCommandAvailable(command)

    override fun seekToPrevious() = seekBack()

    override fun seekToNext() = seekForward()
}
