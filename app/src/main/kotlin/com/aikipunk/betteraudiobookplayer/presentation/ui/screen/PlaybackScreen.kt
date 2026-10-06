package com.aikipunk.betteraudiobookplayer.presentation.ui.screen

import com.aikipunk.betteraudiobookplayer.presentation.ui.components.BookDetailsDialog
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepMarker
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepTimerState
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepTimer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import coil.compose.AsyncImage
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.*
import androidx.compose.ui.graphics.graphicsLayer
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.model.AudioOutput
import com.aikipunk.betteraudiobookplayer.domain.bookmark.BookmarkExport
import com.aikipunk.betteraudiobookplayer.presentation.viewmodel.PlaybackViewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.min

@Composable
fun PlaybackScreen(
    audiobookId: String?,
    modifier: Modifier = Modifier,
    autoplay: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onNavigateToLibrary: (() -> Unit)? = null,
    viewModel: PlaybackViewModel? = null,
) {
    // No audiobook loaded: show the normal player UI; Play/Bookshelf open the Library
    if (audiobookId.isNullOrEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            BlankPlaybackContent(
                onBookshelfClick = { onNavigateToLibrary?.invoke() },
            )
        }
        return
    }
    
    if (viewModel == null) {
        return
    }
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val sleepMarker by viewModel.sleepMarker.collectAsStateWithLifecycle()
    val shakeToExtendEnabled by viewModel.shakeToExtendEnabled.collectAsStateWithLifecycle()
    val skipBackSeconds by viewModel.skipBackSeconds.collectAsStateWithLifecycle()
    val skipForwardSeconds by viewModel.skipForwardSeconds.collectAsStateWithLifecycle()
    val sleepDefaultMinutes by viewModel.sleepTimerDefaultMinutes.collectAsStateWithLifecycle()
    val audioOutputs by viewModel.audioOutputs.collectAsStateWithLifecycle()
    val currentAudioOutput by viewModel.currentAudioOutput.collectAsStateWithLifecycle()

    // Autoplay once; recreating the screen (rotation, process restore) shouldn't restart playback
    var autoplayConsumed by rememberSaveable(audiobookId) { mutableStateOf(false) }
    LaunchedEffect(audiobookId) {
        viewModel.loadAudiobook(audiobookId, autoPlay = autoplay && !autoplayConsumed)
        autoplayConsumed = true
    }

    // Persist position whenever the app goes to the background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.savePlaybackState()
            // The route may have changed in the system output switcher while we were away
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAudioOutputs()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+: the media notification/controls need notification permission.
    // Ask in context on Play; playback proceeds whatever the answer.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.togglePlayPause() }
    val onPlayPause: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !uiState.isPlaying &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.togglePlayPause()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(it)
        }
    }

    // After adding a bookmark, offer to write a note for it
    var noteBookmark by remember { mutableStateOf<Bookmark?>(null) }
    LaunchedEffect(uiState.addedBookmark) {
        uiState.addedBookmark?.let { added ->
            viewModel.consumeAddedBookmark()
            val result = snackbarHostState.showSnackbar(
                message = "Bookmark added",
                actionLabel = "Add note",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) noteBookmark = added
        }
    }
    noteBookmark?.let { bookmark ->
        BookmarkEditDialog(
            bookmark = bookmark,
            dialogTitle = "Add note",
            onSave = {
                viewModel.updateBookmark(it)
                noteBookmark = null
            },
            onDelete = {
                viewModel.deleteBookmark(bookmark)
                noteBookmark = null
            },
            onDismiss = { noteBookmark = null },
        )
    }

    // Held here so the tab survives rotation, when the book briefly reloads
    var selectedTab by rememberSaveable { mutableStateOf("Player") }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.bookRemoved -> {
                BlankPlaybackContent(
                    onBookshelfClick = { onNavigateToLibrary?.invoke() },
                )
            }
            uiState.isLoading -> {
                LoadingScreen()
            }
            uiState.error != null -> {
                ErrorScreen(
                    message = uiState.error ?: "Unknown error",
                    onDismiss = { viewModel.clearError() },
                )
            }
            else -> {
                PlaybackContent(
                    uiState = uiState,
                    selectedTab = selectedTab,
                    onSelectedTabChange = { selectedTab = it },
                    skipBackSeconds = skipBackSeconds,
                    skipForwardSeconds = skipForwardSeconds,
                    sleepDefaultMinutes = sleepDefaultMinutes,
                    onSkipBackward10 = { viewModel.skipBackward(skipBackSeconds * 1_000L) },
                    onSkipForward30 = { viewModel.skipForward(skipForwardSeconds * 1_000L) },
                    onSkipForward60 = { viewModel.skipForward(60_000) },
                    onSkipBackward60 = { viewModel.skipBackward(60_000) },
                    onPlayPause = onPlayPause,
                    onSpeedChanged = { viewModel.updateSpeed(it) },
                    onPositionChanged = { viewModel.updatePosition(it) },
                    onBookmarkClick = { title, description ->
                        viewModel.createBookmark(title, description)
                    },
                    onBookshelfClick = { onNavigateToLibrary?.invoke() },
                    onBookmarkSelected = { viewModel.jumpToBookmark(it) },
                    onPreviousChapter = { viewModel.previousChapter() },
                    onNextChapter = { viewModel.nextChapter() },
                    onChapterSelected = { viewModel.selectChapter(it) },
                    onBookmarkUpdated = { viewModel.updateBookmark(it) },
                    onBookmarkDeleted = { viewModel.deleteBookmark(it) },
                    onDeleteBookmarks = { viewModel.deleteBookmarks(it) },
                    onExportBookmarks = { viewModel.exportBookmarks(it) },
                    sleepTimerState = sleepTimerState,
                    sleepMarker = sleepMarker?.takeIf { it.audiobookId == uiState.audiobookId },
                    onJumpToSleepMarker = { viewModel.jumpToSleepMarker() },
                    shakeToExtendEnabled = shakeToExtendEnabled,
                    onStartSleepTimer = { viewModel.startSleepTimer(it) },
                    onStartSleepTimerEndOfChapter = { viewModel.startSleepTimerEndOfChapter() },
                    onExtendSleepTimer = { viewModel.extendSleepTimer() },
                    onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                    audioOutputs = audioOutputs,
                    currentAudioOutput = currentAudioOutput,
                    onOpenAudioOutputs = { viewModel.refreshAudioOutputs() },
                    onSelectAudioOutput = { viewModel.selectAudioOutput(it) },
                    onMoreAudioOutputs = { viewModel.showSystemOutputSwitcher() },
                    onSaveBookDetails = viewModel::saveBookDetails,
                    onRemoveBook = { viewModel.removeBook() },
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PlaybackContent(
    uiState: com.aikipunk.betteraudiobookplayer.presentation.viewmodel.PlaybackUiState,
    modifier: Modifier = Modifier,
    selectedTab: String = "Player",
    onSelectedTabChange: (String) -> Unit = {},
    skipBackSeconds: Int = UserPreferences.DEFAULT_SKIP_BACK_SECONDS,
    skipForwardSeconds: Int = UserPreferences.DEFAULT_SKIP_FORWARD_SECONDS,
    sleepDefaultMinutes: Int = SLEEP_DEFAULT_MINUTES,
    onSkipBackward10: () -> Unit,
    onSkipForward30: () -> Unit,
    onSkipForward60: () -> Unit = {},
    onSkipBackward60: () -> Unit = {},
    onPlayPause: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onPositionChanged: (Long) -> Unit,
    onBookmarkClick: (String, String?) -> Unit,
    onBookshelfClick: () -> Unit = {},
    onBookmarkSelected: (Bookmark) -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onChapterSelected: (Int) -> Unit = {},
    onBookmarkUpdated: (Bookmark) -> Unit = {},
    onBookmarkDeleted: (Bookmark) -> Unit = {},
    onDeleteBookmarks: (Set<String>) -> Unit = {},
    onExportBookmarks: (android.net.Uri) -> Unit = {},
    sleepTimerState: SleepTimerState = SleepTimerState.Off,
    sleepMarker: SleepMarker? = null,
    onJumpToSleepMarker: () -> Unit = {},
    shakeToExtendEnabled: Boolean = false,
    onStartSleepTimer: (Int) -> Unit = {},
    onStartSleepTimerEndOfChapter: () -> Unit = {},
    onExtendSleepTimer: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    audioOutputs: List<AudioOutput> = emptyList(),
    currentAudioOutput: AudioOutput? = null,
    onOpenAudioOutputs: () -> Unit = {},
    onSelectAudioOutput: (AudioOutput) -> Unit = {},
    onMoreAudioOutputs: () -> Boolean = { false },
    onSaveBookDetails: (BookDetails) -> Unit = {},
    onRemoveBook: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var timelineBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showAudioOutputs by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showBookDetails by remember { mutableStateOf(false) }

    val detailsBook = uiState.audiobook
    if (showBookDetails && detailsBook != null) {
        BookDetailsDialog(
            audiobook = detailsBook,
            onSave = { details ->
                onSaveBookDetails(details)
                showBookDetails = false
            },
            onRemove = {
                onRemoveBook()
                showBookDetails = false
            },
            onDismiss = { showBookDetails = false },
        )
    }

    if (showAudioOutputs) {
        AudioOutputSheet(
            outputs = audioOutputs,
            current = currentAudioOutput,
            onSelect = {
                onSelectAudioOutput(it)
                showAudioOutputs = false
            },
            onMoreDevices = {
                if (onMoreAudioOutputs()) showAudioOutputs = false
            },
            onDismiss = { showAudioOutputs = false },
        )
    }

    if (showSleepTimer) {
        val trackDurationMs = uiState.durationMs.takeIf { it > 0 } ?: uiState.currentTrack?.duration ?: 0L
        SleepTimerDialog(
            state = sleepTimerState,
            canStopAtEndOfChapter = trackDurationMs in 1 until SleepTimer.END_OF_CHAPTER_MAX_TRACK_MS,
            shakeToExtendEnabled = shakeToExtendEnabled,
            defaultMinutes = sleepDefaultMinutes,
            onStart = {
                onStartSleepTimer(it)
                showSleepTimer = false
            },
            onEndOfChapter = {
                onStartSleepTimerEndOfChapter()
                showSleepTimer = false
            },
            onExtend = onExtendSleepTimer,
            onTurnOff = {
                onCancelSleepTimer()
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false },
        )
    }

    timelineBookmark?.let { bookmark ->
        BookmarkEditDialog(
            bookmark = bookmark,
            dialogTitle = "Bookmark • ${BookmarkExport.location(bookmark, uiState.tracks)}",
            onSave = {
                onBookmarkUpdated(it)
                timelineBookmark = null
            },
            onDelete = {
                onBookmarkDeleted(bookmark)
                timelineBookmark = null
            },
            onJump = {
                onBookmarkSelected(bookmark)
                timelineBookmark = null
            },
            onShare = { shareBookmark(context, uiState.audiobook?.displayTitle.orEmpty(), it, uiState.tracks) },
            onDismiss = { timelineBookmark = null },
        )
    }

    if (showChapters) {
        ChapterListDialog(
            tracks = uiState.tracks,
            currentTrackId = uiState.currentTrack?.id,
            onChapterSelected = {
                onChapterSelected(it)
                showChapters = false
            },
            onDismiss = { showChapters = false },
        )
    }
    if (showSpeed) {
        SpeedPickerDialog(
            currentSpeed = uiState.speed,
            onSpeedSelected = {
                onSpeedChanged(it)
                showSpeed = false
            },
            onDismiss = { showSpeed = false },
        )
    }

    Scaffold(
        containerColor = SurfaceDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PlayerBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab == "Bookshelf") {
                        onBookshelfClick()
                    } else {
                        onSelectedTabChange(tab)
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (selectedTab == "Bookmarks") {
            BookmarksContent(
                bookTitle = uiState.audiobook?.displayTitle.orEmpty(),
                bookmarks = uiState.bookmarks,
                tracks = uiState.tracks,
                onBookmarkSelected = {
                    onBookmarkSelected(it)
                    onSelectedTabChange("Player")
                },
                onBookmarkUpdated = onBookmarkUpdated,
                onDeleteBookmarks = onDeleteBookmarks,
                onExport = onExportBookmarks,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }
        if (selectedTab == "Settings") {
            SettingsContent(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }
        val isWide = isWideLayout()
        val track = uiState.currentTrack
        val chapterNumber = track?.let { t ->
            uiState.tracks.indexOfFirst { it.id == t.id }.takeIf { it >= 0 }?.plus(1) ?: t.trackNumber
        }
        // Player reports duration only once prepared; fall back to the track's metadata
        val durationMs = uiState.durationMs.takeIf { it > 0 } ?: track?.duration ?: 0L
        val positionMs = uiState.currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))

        val hasChapters = uiState.tracks.size > 1
        val bookTitle = uiState.audiobook?.displayTitle ?: track?.title.orEmpty()

        val cover: @Composable (Modifier) -> Unit = { coverModifier ->
            BookCoverViewport(
                modifier = coverModifier,
                coverUrl = uiState.audiobook?.coverArtPath.orEmpty(),
                // Only multi-chapter books get the chapter tag
                chapterTitle = if (track != null && hasChapters) "Ch. $chapterNumber • ${track.title}" else null,
                sleepTimerRemaining = sleepTimerCoverLabel(sleepTimerState),
                sleepMarkerLabel = sleepMarker
                    ?.takeIf { sleepTimerState == SleepTimerState.Off && it.itemIndex in uiState.tracks.indices }
                    ?.let { sleepMarkerLabel(it, hasChapters) },
                onSleepMarkerClick = onJumpToSleepMarker,
                onSleepTimerClick = { showSleepTimer = true },
                onChapterClick = if (hasChapters) ({ showChapters = true }) else null,
                onBookmarkClick = {
                    if (uiState.currentTrack != null) {
                        onBookmarkClick(if (hasChapters) "Chapter $chapterNumber" else bookTitle, null)
                    }
                }
            )
        }
        val controls: @Composable ColumnScope.() -> Unit = {
            // Title & Metadata
            BookMetadataSection(
                title = bookTitle.ifEmpty { "No audiobook selected" },
                author = uiState.audiobook?.author ?: uiState.currentTrack?.metadata?.author,
                narrator = uiState.audiobook?.narrator,
                // Duration, then publisher and year when known
                totalDuration = listOfNotNull(
                    formatDuration(uiState.audiobook?.duration?.takeIf { it > 0 } ?: durationMs),
                    uiState.audiobook?.publisher,
                    uiState.audiobook?.publishedYear?.toString(),
                ).joinToString(" · "),
                onInfoClick = if (uiState.audiobook != null) ({ showBookDetails = true }) else null
            )

            Spacer(modifier = Modifier.height(if (isWide) 4.dp else 12.dp))

            TimelineScrubber(
                positionMs = positionMs,
                durationMs = durationMs,
                chapterInfo = if (track != null && hasChapters) {
                    "Ch. $chapterNumber of ${uiState.tracks.size}"
                } else {
                    ""
                },
                bookmarks = uiState.bookmarks.filter { it.trackId == track?.id },
                onBookmarkMarkerClick = { timelineBookmark = it },
                sleepMarkerMs = sleepMarker
                    ?.takeIf { chapterNumber != null && it.itemIndex == chapterNumber - 1 }
                    ?.positionMs,
                onSleepMarkerClick = onJumpToSleepMarker,
                onSeek = onPositionChanged
            )

            Spacer(modifier = Modifier.height(if (isWide) 8.dp else 16.dp))

            // Playback Controls - ALWAYS VISIBLE
            PlaybackControlsRow(
                isPlaying = uiState.isPlaying,
                onPlayPauseToggle = onPlayPause,
                onRewind15 = onSkipBackward10,
                onFastForward30 = onSkipForward30,
                skipBackSeconds = skipBackSeconds,
                skipForwardSeconds = skipForwardSeconds,
                // Single-file books have no chapters: outer buttons become -1 min / +1 min
                hasChapters = hasChapters,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
                onRewind60 = onSkipBackward60,
                onFastForward60 = onSkipForward60
            )

            Spacer(modifier = Modifier.height(if (isWide) 8.dp else 20.dp))

            // Audio Utilities - ALWAYS VISIBLE
            AudioUtilitiesRow(
                speed = formatSpeed(uiState.speed),
                sleepTimer = sleepTimerTimeLabel(sleepTimerState) ?: "Sleep",
                sleepTimerActive = sleepTimerState != SleepTimerState.Off,
                audioOutput = currentAudioOutput,
                onAudioOutputClick = {
                    onOpenAudioOutputs()
                    showAudioOutputs = true
                },
                onSpeedClick = { showSpeed = true },
                onSleepTimerClick = { showSleepTimer = true }
            )
        }

        if (isWide) {
            // Landscape: cover on the left, controls beside it
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoxWithConstraints(
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    cover(Modifier.size(min(maxWidth, maxHeight)))
                }
                Spacer(modifier = Modifier.width(24.dp))
                BoxWithConstraints(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        controls()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                cover(Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(modifier = Modifier.height(16.dp))
                controls()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** True when the window is wider than it is tall (landscape phones, most tablets). */
@Composable
private fun isWideLayout(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}

// ============================
// Sub-Components
// ============================

@Composable
private fun BookCoverViewport(
    modifier: Modifier,
    coverUrl: String,
    chapterTitle: String?,
    onChapterClick: (() -> Unit)? = null,
    sleepTimerRemaining: String?,
    onSleepTimerClick: () -> Unit = {},
    sleepMarkerLabel: String? = null,
    onSleepMarkerClick: () -> Unit = {},
    onBookmarkClick: () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(24.dp),
                ambientColor = TerracottaPrimary.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceContainerLow)
    ) {
        // Placeholder gradient if no cover art
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(TerracottaContainer, SurfaceBright)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = TerracottaPrimary.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            )
        }

        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Book cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Vignette gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        // Top badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (sleepTimerRemaining != null) Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClickLabel = "Sleep timer settings", onClick = onSleepTimerClick),
                color = Color.Black.copy(alpha = CHIP_SCRIM_ALPHA),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = OverlayAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sleepTimerRemaining,
                        modifier = Modifier.semantics { contentDescription = "Sleep timer $sleepTimerRemaining" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OverlayAccent
                    )
                }
            }
            if (sleepTimerRemaining == null && sleepMarkerLabel != null) Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClickLabel = "Go back to where you fell asleep", onClick = onSleepMarkerClick),
                color = Color.Black.copy(alpha = CHIP_SCRIM_ALPHA),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = OverlayAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Fell asleep? $sleepMarkerLabel",
                        modifier = Modifier.semantics {
                            contentDescription = "Sleep timer started at $sleepMarkerLabel"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OverlayAccent
                    )
                }
            }
        }

        // Bottom chapter tag (opens the chapter list); hidden for books without chapters
        if (chapterTitle != null) Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp, end = 84.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (onChapterClick != null) {
                        Modifier.clickable(onClickLabel = "Show chapters", onClick = onChapterClick)
                    } else {
                        Modifier
                    },
                ),
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TerracottaPrimaryVariant)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = chapterTitle,
                    color = OverlayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onChapterClick != null) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = OverlayTextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Floating bookmark FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(52.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(TerracottaPrimaryVariant, TerracottaPrimary)
                    )
                )
                .clickable { onBookmarkClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = "Add bookmark at current position",
                tint = OnPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun BookMetadataSection(
    title: String,
    author: String?,
    narrator: String?,
    totalDuration: String,
    onInfoClick: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Book details, rename and notes",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        val credits = listOfNotNull(
            author?.let { "By $it" },
            narrator?.let { "Narrated by $it" },
        ).joinToString(" • ")
        if (credits.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = credits,
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = totalDuration,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun TimelineScrubber(
    positionMs: Long,
    durationMs: Long,
    chapterInfo: String,
    bookmarks: List<Bookmark> = emptyList(),
    onBookmarkMarkerClick: (Bookmark) -> Unit = {},
    sleepMarkerMs: Long? = null,
    onSleepMarkerClick: () -> Unit = {},
    onSeek: (Long) -> Unit
) {
    // While dragging, show the drag position locally and only seek on release
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val playerFraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val progress = (dragFraction ?: playerFraction).coerceIn(0f, 1f)
    val shownPositionMs = (progress * durationMs).toLong()
    val currentTime = formatDuration(shownPositionMs)
    val remainingTime = "-" + formatDuration((durationMs - shownPositionMs).coerceAtLeast(0L))

    Column(modifier = Modifier.fillMaxWidth()) {
        // Bookmark markers for the current track, aligned with the slider track
        BookmarkMarkers(
            bookmarks = bookmarks,
            durationMs = durationMs,
            onClick = onBookmarkMarkerClick,
            sleepMarkerMs = sleepMarkerMs,
            onSleepMarkerClick = onSleepMarkerClick,
        )

        // Seek slider
        Slider(
            value = progress,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = TerracottaPrimary,
                activeTrackColor = TerracottaPrimary,
                inactiveTrackColor = SurfaceBright
            ),
            modifier = Modifier
                .height(20.dp)
                .semantics {
                    contentDescription = "Playback position"
                    stateDescription = "${spokenDuration(shownPositionMs)} of ${spokenDuration(durationMs)}"
                }
        )

        // Timestamps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = currentTime, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (chapterInfo.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Adjust,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = chapterInfo, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextMuted)
            }
            Text(text = remainingTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        }
    }
}

/** Keeps cover chip text at 4.5:1 even over a white cover. */
private const val CHIP_SCRIM_ALPHA = 0.65f
private val SLIDER_TRACK_INSET = 10.dp
private val MARKER_TOUCH_SIZE = 28.dp

@Composable
private fun BookmarkMarkers(
    bookmarks: List<Bookmark>,
    durationMs: Long,
    onClick: (Bookmark) -> Unit,
    sleepMarkerMs: Long? = null,
    onSleepMarkerClick: () -> Unit = {},
) {
    val visible = if (durationMs > 0) bookmarks else emptyList()
    val sleepMs = sleepMarkerMs?.takeIf { durationMs > 0 }
    // Centres each marker over its position on the slider track
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .height(MARKER_TOUCH_SIZE),
        content = {
            visible.forEach { bookmark -> BookmarkMarker(bookmark = bookmark, onClick = onClick) }
            if (sleepMs != null) SleepMarkerIcon(positionMs = sleepMs, onClick = onSleepMarkerClick)
        },
    ) { measurables, constraints ->
        val inset = SLIDER_TRACK_INSET.roundToPx()
        val trackWidth = (constraints.maxWidth - inset * 2).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints()) }
        val size = MARKER_TOUCH_SIZE.roundToPx()
        fun centerOf(ms: Long) = inset + (trackWidth * (ms.toFloat() / durationMs).coerceIn(0f, 1f)).toInt()
        // The sleep marker stays exact; bookmarks only need to be near their spot, so they step aside
        val sleepCenter = sleepMs?.let(::centerOf)
        val centers = bookmarkCenters(
            visible.map { centerOf(it.positionMs) }, sleepCenter, size, size / 2, constraints.maxWidth - size / 2,
        ) + listOfNotNull(sleepCenter)
        layout(constraints.maxWidth, size) {
            placeables.forEachIndexed { i, placeable -> placeable.place(centers[i] - placeable.width / 2, 0) }
        }
    }
}

@Composable
private fun SleepMarkerIcon(positionMs: Long, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MARKER_TOUCH_SIZE)
            .clip(CircleShape)
            .clickable(onClickLabel = "Go back to where you fell asleep", onClick = onClick)
            .semantics { contentDescription = "Sleep timer started at ${formatDuration(positionMs)}" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            imageVector = Icons.Outlined.Bedtime,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun BookmarkMarker(bookmark: Bookmark, onClick: (Bookmark) -> Unit) {
    val hasNote = !bookmark.description.isNullOrBlank()
    Box(
        modifier = Modifier
            .size(MARKER_TOUCH_SIZE)
            .clip(CircleShape)
            .clickable(onClickLabel = "Open bookmark") { onClick(bookmark) }
            .semantics {
                contentDescription = "Bookmark ${bookmark.title} at ${formatDuration(bookmark.positionMs)}" +
                    if (hasNote) ", has notes" else ""
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            imageVector = if (hasNote) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            tint = TerracottaPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Skip button icon showing the configured interval (e.g. a replay arrow with "15"). */
@Composable
private fun SkipIcon(seconds: Int, forward: Boolean) {
    val description = if (forward) "Skip forward $seconds seconds" else "Rewind $seconds seconds"
    val numbered = when {
        forward && seconds == 5 -> Icons.Outlined.Forward5
        forward && seconds == 10 -> Icons.Outlined.Forward10
        forward && seconds == 30 -> Icons.Outlined.Forward30
        !forward && seconds == 5 -> Icons.Outlined.Replay5
        !forward && seconds == 10 -> Icons.Outlined.Replay10
        !forward && seconds == 30 -> Icons.Outlined.Replay30
        else -> null
    }
    if (numbered != null) {
        Icon(numbered, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(30.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Replay,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { if (forward) scaleX = -1f },
        )
        Text(
            text = "$seconds",
            color = TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun PlaybackControlsRow(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onRewind15: () -> Unit,
    onFastForward30: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    hasChapters: Boolean = true,
    onRewind60: () -> Unit = {},
    onFastForward60: () -> Unit = {},
    skipBackSeconds: Int = UserPreferences.DEFAULT_SKIP_BACK_SECONDS,
    skipForwardSeconds: Int = UserPreferences.DEFAULT_SKIP_FORWARD_SECONDS,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = if (hasChapters) onPreviousChapter else onRewind60) {
            Icon(
                if (hasChapters) Icons.Outlined.SkipPrevious else Icons.Outlined.FastRewind,
                contentDescription = if (hasChapters) "Previous Chapter" else "Rewind 1 minute",
                tint = TextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(onClick = onRewind15) {
            SkipIcon(seconds = skipBackSeconds, forward = false)
        }

        // Play/Pause Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(16.dp, CircleShape, ambientColor = TerracottaPrimary.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TerracottaPrimaryVariant, TerracottaPrimary)
                    )
                )
                .clickable { onPlayPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = OnPrimary,
                modifier = Modifier.size(38.dp)
            )
        }

        IconButton(onClick = onFastForward30) {
            SkipIcon(seconds = skipForwardSeconds, forward = true)
        }

        IconButton(onClick = if (hasChapters) onNextChapter else onFastForward60) {
            Icon(
                if (hasChapters) Icons.Outlined.SkipNext else Icons.Outlined.FastForward,
                contentDescription = if (hasChapters) "Next Chapter" else "Fast Forward 1 minute",
                tint = TextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun AudioUtilitiesRow(
    speed: String,
    sleepTimer: String,
    audioOutput: AudioOutput?,
    onAudioOutputClick: () -> Unit = {},
    sleepTimerActive: Boolean = false,
    onSpeedClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
) {
    // Intrinsic height keeps all three pills as tall as the tallest when text is scaled up
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UtilityPill(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClickLabel = "Change playback speed") { onSpeedClick() },
            icon = null,
            title = speed,
            label = "Speed",
            isHighlighted = true
        )
        UtilityPill(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClickLabel = "Set sleep timer") { onSleepTimerClick() }
                .semantics {
                    contentDescription = if (sleepTimerActive) "Sleep timer, $sleepTimer remaining" else "Sleep timer off"
                },
            icon = Icons.Outlined.Bedtime,
            iconTint = if (sleepTimerActive) AccentYellow else TextPrimary,
            title = "",
            label = sleepTimer
        )
        val outputName = audioOutput?.name ?: "Phone speaker"
        UtilityPill(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clickable(onClickLabel = "Choose audio output") { onAudioOutputClick() }
                .semantics { contentDescription = "Audio output: $outputName" },
            icon = audioOutputIcon(audioOutput?.kind ?: AudioOutput.Kind.PHONE_SPEAKER),
            title = "",
            label = outputName,
        )
    }
}

@Composable
private fun UtilityPill(
    modifier: Modifier = Modifier,
    icon: ImageVector?,
    title: String,
    label: String,
    isHighlighted: Boolean = false,
    iconTint: Color = TextPrimary,
) {
    Surface(
        // Grows with large font sizes instead of clipping the label
        modifier = modifier.heightIn(min = 60.dp),
        color = SurfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SurfaceBright.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            } else if (title.isNotEmpty()) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isHighlighted) TerracottaPrimary else TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayerBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    if (!isWideLayout()) {
        PlayerNavigationBar(selectedTab, onTabSelected)
        return
    }
    // Wide screens are short on height: tuck the bar away behind a chevron handle
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expanded) Modifier
                    else Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                )
                .height(48.dp)
                .clickable(
                    onClickLabel = if (expanded) "Hide navigation" else "Show navigation",
                    onClick = { expanded = !expanded },
                )
                .semantics {
                    contentDescription = "Navigation"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = null,
                tint = TextSecondary,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            PlayerNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    expanded = false
                    onTabSelected(tab)
                },
            )
        }
    }
}

@Composable
private fun PlayerNavigationBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        val items = listOf(
            Triple("Bookshelf", Icons.AutoMirrored.Outlined.MenuBook, "Bookshelf"),
            Triple("Player", Icons.Outlined.Headphones, "Player"),
            Triple("Bookmarks", Icons.Outlined.BookmarkBorder, "Bookmarks"),
            Triple("Settings", Icons.Outlined.Settings, "Settings")
        )
        items.forEach { (tabName, icon, label) ->
            val isSelected = selectedTab == tabName
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tabName) },
                icon = {
                    // The label below names the tab; a description here would be read twice
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) TerracottaPrimary else TextSecondary
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) TerracottaPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

// ============================
// Chapters & Speed
// ============================

internal val SPEED_OPTIONS = listOf(0.75f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

internal fun formatSpeed(speed: Float): String =
    "%s×".format(("%.2f".format(java.util.Locale.US, speed)).trimEnd('0').trimEnd('.'))

@Composable
private fun ChapterListDialog(
    tracks: List<Track>,
    currentTrackId: String?,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapters") },
        text = {
            val currentIndex = tracks.indexOfFirst { it.id == currentTrackId }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState(
                initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0),
            )
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
                items(tracks.size) { index ->
                    val track = tracks[index]
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) TerracottaPrimary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable(onClickLabel = "Play chapter") { onChapterSelected(index) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (isCurrent) TerracottaPrimary else TextMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(
                            text = track.title,
                            color = if (isCurrent) TerracottaPrimary else TextPrimary,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (track.duration > 0) {
                            Text(formatDuration(track.duration), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SpeedPickerDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                SPEED_OPTIONS.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { speed ->
                            val selected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                            FilterChip(
                                selected = selected,
                                onClick = { onSpeedSelected(speed) },
                                label = {
                                    Text(
                                        formatSpeed(speed),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// ============================
// Sleep timer
// ============================

private const val SLEEP_MIN_MINUTES = 10
private const val SLEEP_MAX_MINUTES = 90
private const val SLEEP_STEP_MINUTES = 5
internal const val SLEEP_DEFAULT_MINUTES = 30
private const val SLEEP_CUSTOM_MAX_MINUTES = 12 * 60

/** Remaining time for display, rounded up to the second; null when the timer is off. */
internal fun sleepTimerTimeLabel(state: SleepTimerState): String? = when (state) {
    SleepTimerState.Off -> null
    is SleepTimerState.Countdown -> formatDuration((state.remainingMs + 999) / 1000 * 1000)
    is SleepTimerState.EndOfChapter ->
        if (state.remainingMs == Long.MAX_VALUE) "Ch. end" else formatDuration((state.remainingMs + 999) / 1000 * 1000)
}

/**
 * Centres for bookmark markers of width [size] (same order as [centers]) so none overlap each other
 * or the sleep marker at [sleepCenter]. Bookmarks keep their side of the sleep marker unless there's
 * no room there; overlapping ones spread evenly around their shared spot.
 */
internal fun bookmarkCenters(
    centers: List<Int>,
    sleepCenter: Int?,
    size: Int,
    minCenter: Int,
    maxCenter: Int,
): List<Int> {
    if (sleepCenter == null) return spreadCenters(centers, size, minCenter, maxCenter)
    val leftMax = sleepCenter - size
    val rightMin = sleepCenter + size
    val leftFits = leftMax >= minCenter
    val rightFits = rightMin <= maxCenter
    val goesLeft = centers.map { (it <= sleepCenter && leftFits) || !rightFits }
    val left = centers.filterIndexed { i, _ -> goesLeft[i] }
    val right = centers.filterIndexed { i, _ -> !goesLeft[i] }
    val placedLeft = spreadCenters(left, size, minCenter, leftMax).iterator()
    val placedRight = spreadCenters(right, size, rightMin, maxCenter).iterator()
    return goesLeft.map { if (it) placedLeft.next() else placedRight.next() }
}

/** Spaces [centers] at least [size] apart within [lo]..[hi], moving each as little as possible. */
private fun spreadCenters(centers: List<Int>, size: Int, lo: Int, hi: Int): List<Int> {
    if (centers.isEmpty()) return centers
    val order = centers.indices.sortedBy { centers[it] }
    val n = order.size
    val placed = DoubleArray(n)
    if (n > 1 && (n - 1).toLong() * size > hi - lo) {
        // Not enough room: share the width evenly (markers will overlap a little)
        val step = (hi - lo).toDouble() / (n - 1)
        for (k in 0 until n) placed[k] = lo + k * step
    } else {
        // Merge overlapping runs into clusters centred on their members' average spot
        val starts = ArrayList<Double>()
        val counts = ArrayList<Int>()
        for (k in 0 until n) {
            var start = centers[order[k]].toDouble()
            var count = 1
            while (starts.isNotEmpty() && starts.last() + counts.last() * size > start) {
                val prevCount = counts.removeAt(counts.lastIndex)
                val prevStart = starts.removeAt(starts.lastIndex)
                start = (prevCount * prevStart + count * (start - prevCount * size)) / (prevCount + count)
                count += prevCount
            }
            starts += start
            counts += count
        }
        var k = 0
        starts.forEachIndexed { c, start -> repeat(counts[c]) { placed[k] = start + it * size; k++ } }
        // Push back inside the bounds, keeping the spacing
        placed[0] = maxOf(placed[0], lo.toDouble())
        for (i in 1 until n) placed[i] = maxOf(placed[i], placed[i - 1] + size)
        placed[n - 1] = minOf(placed[n - 1], hi.toDouble())
        for (i in n - 2 downTo 0) placed[i] = minOf(placed[i], placed[i + 1] - size)
    }
    val result = IntArray(n)
    for (k in 0 until n) result[order[k]] = kotlin.math.round(placed[k]).toInt()
    return result.toList()
}

/** Time for screen readers, e.g. "1 hour 5 minutes 3 seconds", instead of "01:05:03". */
internal fun spokenDuration(ms: Long): String {
    val total = (ms / 1_000).coerceAtLeast(0)
    val parts = listOfNotNull(
        (total / 3_600).takeIf { it > 0 }?.let { "$it ${if (it == 1L) "hour" else "hours"}" },
        (total % 3_600 / 60).takeIf { it > 0 }?.let { "$it ${if (it == 1L) "minute" else "minutes"}" },
        (total % 60).takeIf { it > 0 || total == 0L }?.let { "$it ${if (it == 1L) "second" else "seconds"}" },
    )
    return parts.joinToString(" ")
}

/** Cover chip text for the sleep marker, e.g. "Ch. 3 · 12:04". */
internal fun sleepMarkerLabel(marker: SleepMarker, hasChapters: Boolean): String {
    val time = formatDuration(marker.positionMs)
    return if (hasChapters) "Ch. ${marker.itemIndex + 1} · $time" else time
}

internal fun sleepTimerCoverLabel(state: SleepTimerState): String? {
    val time = sleepTimerTimeLabel(state) ?: return null
    return if (state is SleepTimerState.EndOfChapter && time != "Ch. end") "Ch. end • $time" else time
}

@Composable
private fun SleepTimerDialog(
    state: SleepTimerState,
    canStopAtEndOfChapter: Boolean,
    shakeToExtendEnabled: Boolean,
    defaultMinutes: Int = SLEEP_DEFAULT_MINUTES,
    onStart: (Int) -> Unit,
    onEndOfChapter: () -> Unit,
    onExtend: () -> Unit,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shakeHint = if (shakeToExtendEnabled) "Shake your phone near the end of the sleep timer to add 10 minutes." else null

    if (state != SleepTimerState.Off) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sleep timer") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = sleepTimerTimeLabel(state).orEmpty(),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentYellow,
                    )
                    Text(
                        text = if (state is SleepTimerState.EndOfChapter) {
                            "Pauses at the end of this chapter"
                        } else {
                            "Counts down while playing, then pauses and rewinds 1 minute"
                        },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    shakeHint?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onExtend) { Text("+10 min") } },
            dismissButton = { TextButton(onClick = onTurnOff) { Text("Turn off") } },
        )
        return
    }

    var minutes by rememberSaveable {
        mutableIntStateOf(defaultMinutes.coerceIn(SLEEP_MIN_MINUTES, SLEEP_MAX_MINUTES))
    }
    var custom by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    val customMinutes = customText.toIntOrNull()?.takeIf { it in 1..SLEEP_CUSTOM_MAX_MINUTES }
    val steps = (SLEEP_MAX_MINUTES - SLEEP_MIN_MINUTES) / SLEEP_STEP_MINUTES - 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (custom) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { text -> customText = text.filter { it.isDigit() }.take(3) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        isError = customText.isNotEmpty() && customMinutes == null,
                        supportingText = { Text("1 to $SLEEP_CUSTOM_MAX_MINUTES minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "$minutes min",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = {
                            minutes = (Math.round(it / SLEEP_STEP_MINUTES) * SLEEP_STEP_MINUTES)
                                .coerceIn(SLEEP_MIN_MINUTES, SLEEP_MAX_MINUTES)
                        },
                        valueRange = SLEEP_MIN_MINUTES.toFloat()..SLEEP_MAX_MINUTES.toFloat(),
                        steps = steps,
                        colors = SliderDefaults.colors(
                            thumbColor = TerracottaPrimary,
                            activeTrackColor = TerracottaPrimary,
                            inactiveTrackColor = SurfaceBright,
                        ),
                        modifier = Modifier.semantics { stateDescription = "$minutes minutes" },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Wraps to one button per line when large text doesn't fit side by side
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { custom = !custom },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(if (custom) "Slider" else "Custom") }
                    if (canStopAtEndOfChapter) {
                        OutlinedButton(
                            onClick = onEndOfChapter,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("End of chapter") }
                    }
                }
                shakeHint?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = if (custom) customMinutes else minutes
                    if (chosen != null) onStart(chosen)
                },
                enabled = !custom || customMinutes != null,
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ============================
// Utility Functions & Screens
// ============================

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
internal fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = TerracottaPrimary)
    }
}

@Composable
internal fun ErrorScreen(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Error",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF5350)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun BlankPlaybackContent(
    onBookshelfClick: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf("Player") }

    Scaffold(
        containerColor = SurfaceDark,
        bottomBar = {
            PlayerBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab == "Bookshelf") {
                        onBookshelfClick()
                    } else {
                        selectedTab = tab
                    }
                }
            )
        },
    ) { innerPadding ->
        if (selectedTab == "Settings") {
            SettingsContent(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Headphones,
                contentDescription = null,
                tint = TerracottaPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Nothing playing",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose a book from your bookshelf to start listening.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onBookshelfClick,
                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = OnPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open bookshelf", color = OnPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ============================
// Previews
// ============================

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun PreviewBlankPlaybackContent() {
    AudiobookPlayerTheme {
        BlankPlaybackContent(
            onBookshelfClick = {},
        )
    }
}
