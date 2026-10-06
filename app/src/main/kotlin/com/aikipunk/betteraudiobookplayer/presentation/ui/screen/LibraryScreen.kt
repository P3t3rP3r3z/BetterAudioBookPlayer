package com.aikipunk.betteraudiobookplayer.presentation.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.aikipunk.betteraudiobookplayer.data.transfer.AudioFileFinder
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBook
import com.aikipunk.betteraudiobookplayer.presentation.ui.components.PendingBookRow
import com.aikipunk.betteraudiobookplayer.presentation.ui.components.RestoreSearchDialog
import com.aikipunk.betteraudiobookplayer.presentation.ui.components.RestoreSectionHeader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderImportMode
import com.aikipunk.betteraudiobookplayer.domain.library.BookProgress
import com.aikipunk.betteraudiobookplayer.domain.library.BookStatus
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryFilter
import com.aikipunk.betteraudiobookplayer.domain.library.LibrarySort
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryViewMode
import com.aikipunk.betteraudiobookplayer.domain.library.ShelfBook
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.presentation.ui.components.BookCover
import com.aikipunk.betteraudiobookplayer.presentation.ui.components.BookDetailsDialog
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.*
import com.aikipunk.betteraudiobookplayer.presentation.viewmodel.FolderImportPrompt
import com.aikipunk.betteraudiobookplayer.presentation.viewmodel.LibraryUiState
import com.aikipunk.betteraudiobookplayer.presentation.viewmodel.LibraryViewModel
import java.time.LocalDateTime

/** [onAudiobookSelected] opens the book in the player; the flag starts playback right away. */
@Composable
fun LibraryScreen(
    onAudiobookSelected: (audiobookId: String, autoplay: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
    onBackClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.importFolder(treeUri)
        } else {
            viewModel.setRestoreTarget(null)
        }
    }
    val onBrowseClick: () -> Unit = {
        viewModel.setRestoreTarget(null)
        folderPickerLauncher.launch(null)
    }

    // Restoring a previous phone's bookshelf: search the media index for a book's folder
    val context = LocalContext.current
    var findRequest by remember { mutableStateOf<SavedBook?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val book = findRequest ?: return@rememberLauncherForActivityResult
        findRequest = null
        if (granted) {
            viewModel.findOnPhone(book)
        } else {
            // Without access to the media index, the user browses for it
            viewModel.setRestoreTarget(book)
            folderPickerLauncher.launch(null)
        }
    }
    val onFind: (SavedBook) -> Unit = { book ->
        if (ContextCompat.checkSelfPermission(context, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.findOnPhone(book)
        } else {
            findRequest = book
            audioPermissionLauncher.launch(AUDIO_PERMISSION)
        }
    }
    var confirmDiscardRestore by remember { mutableStateOf(false) }

    uiState.restoreSearch?.let { search ->
        RestoreSearchDialog(
            book = search.book,
            isSearching = search.isSearching,
            candidates = search.candidates,
            onPick = { candidate ->
                viewModel.setRestoreTarget(search.book)
                folderPickerLauncher.launch(AudioFileFinder.folderPickerUri(candidate))
            },
            onBrowse = {
                viewModel.setRestoreTarget(search.book)
                folderPickerLauncher.launch(null)
            },
            onDismiss = viewModel::dismissRestoreSearch,
        )
    }
    if (confirmDiscardRestore) {
        ConfirmDialog(
            title = "Don't restore?",
            text = "The progress, bookmarks and notes saved from your previous phone for these books will be deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.discardRestore()
                confirmDiscardRestore = false
            },
            onDismiss = { confirmDiscardRestore = false },
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    var detailsFor by remember { mutableStateOf<Audiobook?>(null) }
    var confirmResetFor by remember { mutableStateOf<Audiobook?>(null) }
    var confirmRemoveFor by remember { mutableStateOf<Audiobook?>(null) }

    detailsFor?.let { book ->
        BookDetailsDialog(
            audiobook = book,
            onSave = { details ->
                viewModel.saveDetails(book, details)
                detailsFor = null
            },
            onRemove = {
                viewModel.removeAudiobook(book)
                detailsFor = null
            },
            onDismiss = { detailsFor = null },
        )
    }
    confirmResetFor?.let { book ->
        ConfirmDialog(
            title = "Reset progress?",
            text = "\"${book.displayTitle}\" will start again from the beginning. Bookmarks and notes are kept.",
            confirmLabel = "Reset",
            onConfirm = {
                viewModel.resetProgress(book)
                confirmResetFor = null
            },
            onDismiss = { confirmResetFor = null },
        )
    }
    confirmRemoveFor?.let { book ->
        ConfirmDialog(
            title = "Remove \"${book.displayTitle}\"?",
            text = "Its bookmarks, notes and saved position will be removed. Your audio files are not deleted.",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.removeAudiobook(book)
                confirmRemoveFor = null
            },
            onDismiss = { confirmRemoveFor = null },
        )
    }

    uiState.folderImportPrompt?.let { prompt ->
        FolderImportDialog(
            prompt = prompt,
            onChapters = { viewModel.confirmFolderImport(FolderImportMode.CHAPTERS) },
            onBooks = { viewModel.confirmFolderImport(FolderImportMode.BOOKS) },
            onDismiss = { viewModel.cancelFolderImport() },
        )
    }

    var searching by rememberSaveable { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        viewModel.setQuery("")
    }
    BackHandler(enabled = searching, onBack = closeSearch)

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.audiobooks.isEmpty() -> {
                LoadingScreen()
            }
            uiState.error != null -> {
                ErrorScreen(
                    message = uiState.error ?: "Unknown error",
                    onDismiss = { viewModel.clearError() },
                )
            }
            else -> {
                val hasBooks = uiState.audiobooks.isNotEmpty()
                Scaffold(
                    containerColor = SurfaceDark,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        LibraryTopAppBar(
                            isScanning = uiState.isScanning,
                            showShelfControls = hasBooks,
                            searching = searching,
                            query = uiState.query,
                            sort = uiState.sort,
                            viewMode = uiState.viewMode,
                            onQueryChange = viewModel::setQuery,
                            onOpenSearch = { searching = true },
                            onCloseSearch = closeSearch,
                            onSortChange = viewModel::setSort,
                            onViewModeChange = viewModel::setViewMode,
                            onBackClick = onBackClick,
                            onAddClick = onBrowseClick,
                        )
                    },
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        if (hasBooks) {
                            LibraryContent(
                                uiState = uiState,
                                showHero = !searching,
                                onOpen = { onAudiobookSelected(it.id, false) },
                                onResume = { onAudiobookSelected(it.id, true) },
                                onFilterChange = viewModel::setFilter,
                                onClearFilters = {
                                    viewModel.setFilter(LibraryFilter.ALL)
                                    viewModel.setQuery("")
                                },
                                onDetails = { detailsFor = it },
                                onMarkFinished = viewModel::markFinished,
                                onResetProgress = { confirmResetFor = it },
                                onRemove = { confirmRemoveFor = it },
                                onFindPending = onFind,
                                onBrowse = onBrowseClick,
                                onDiscardPending = { confirmDiscardRestore = true },
                            )
                        } else if (uiState.pendingRestore.isNotEmpty()) {
                            RestoreOnlyContent(
                                pending = uiState.pendingRestore,
                                onFind = onFind,
                                onBrowse = onBrowseClick,
                                onDiscard = { confirmDiscardRestore = true },
                            )
                        } else {
                            EmptyLibraryContent(
                                isScanning = uiState.isScanning,
                                onBrowseClick = onBrowseClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(
    uiState: LibraryUiState,
    showHero: Boolean,
    onOpen: (Audiobook) -> Unit,
    onResume: (Audiobook) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onClearFilters: () -> Unit,
    onDetails: (Audiobook) -> Unit,
    onMarkFinished: (Audiobook) -> Unit,
    onResetProgress: (Audiobook) -> Unit,
    onRemove: (Audiobook) -> Unit,
    onFindPending: (SavedBook) -> Unit,
    onBrowse: () -> Unit,
    onDiscardPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = uiState.viewMode == LibraryViewMode.GRID
    val showPending = uiState.pendingRestore.isNotEmpty() &&
        uiState.filter == LibraryFilter.ALL && uiState.query.isBlank()
    val hero = uiState.continueListening
        ?.takeIf { showHero && uiState.filter == LibraryFilter.ALL && uiState.query.isBlank() }

    LazyVerticalGrid(
        // Two covers across on a phone, more on wider screens; list rows go two-up on tablets
        columns = GridCells.Adaptive(minSize = if (grid) 150.dp else 340.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(if (grid) 14.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 18.dp else 4.dp),
    ) {
        if (hero != null) {
            fullWidth("hero") {
                ContinueListeningCard(
                    book = hero,
                    onOpen = { onOpen(hero.audiobook) },
                    onResume = { onResume(hero.audiobook) },
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }
        }
        fullWidth("filters") {
            FilterChipsRow(selected = uiState.filter, onSelected = onFilterChange)
        }
        if (showPending) {
            fullWidth("restore-header") {
                RestoreSectionHeader(
                    bookCount = uiState.pendingRestore.size,
                    onBrowse = onBrowse,
                    onDiscard = onDiscardPending,
                )
            }
            uiState.pendingRestore.forEachIndexed { index, book ->
                fullWidth("restore-$index-${book.title}") {
                    PendingBookRow(book = book, onFind = { onFindPending(book) })
                }
            }
            fullWidth("restore-divider") {
                HorizontalDivider(color = TextMuted.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        if (uiState.visibleBooks.isEmpty()) {
            fullWidth("no-results") {
                NoMatches(onClear = onClearFilters)
            }
        }
        items(uiState.visibleBooks, key = { it.audiobook.id }) { book ->
            BookWithActions(
                book = book,
                onDetails = onDetails,
                onMarkFinished = onMarkFinished,
                onResetProgress = onResetProgress,
                onRemove = onRemove,
            ) { showMenu ->
                if (grid) {
                    BookGridItem(book = book, onClick = { onOpen(book.audiobook) }, onLongClick = showMenu)
                } else {
                    BookListItem(book = book, onClick = { onOpen(book.audiobook) }, onLongClick = showMenu)
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopAppBar(
    isScanning: Boolean,
    showShelfControls: Boolean,
    searching: Boolean,
    query: String,
    sort: LibrarySort,
    viewMode: LibraryViewMode,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onAddClick: () -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
        navigationIcon = {
            if (searching) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search", tint = TextPrimary)
                }
            } else if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.Close, contentDescription = "Back", tint = TextPrimary)
                }
            }
        },
        title = {
            if (searching) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Title, author, narrator, publisher, year", color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = TerracottaPrimary,
                        unfocusedIndicatorColor = TextMuted,
                        cursorColor = TerracottaPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            } else {
                Text(
                    "My Audiobooks",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            }
        },
        actions = {
            if (showShelfControls && !searching) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search library", tint = TextPrimary)
                }
                SortMenuButton(sort = sort, onSortChange = onSortChange)
                val toList = viewMode == LibraryViewMode.GRID
                IconButton(onClick = { onViewModeChange(if (toList) LibraryViewMode.LIST else LibraryViewMode.GRID) }) {
                    Icon(
                        if (toList) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = if (toList) "Show as list" else "Show as grid",
                        tint = TextPrimary,
                    )
                }
            }
            if (!searching) {
                IconButton(onClick = onAddClick, enabled = !isScanning) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = TerracottaPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(20.dp)
                                .semantics { contentDescription = "Importing" },
                        )
                    } else {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add audiobook folder",
                            tint = TerracottaPrimary,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SortMenuButton(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort, currently ${sort.label}",
                tint = TextPrimary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Sort by",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSortChange(option)
                        open = false
                    },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = TerracottaPrimary)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: LibraryFilter,
    onSelected: (LibraryFilter) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SurfaceDark,
                    labelColor = TextSecondary,
                    selectedContainerColor = TerracottaContainer,
                    selectedLabelColor = TextPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter == selected,
                    borderColor = TextMuted,
                    selectedBorderColor = TerracottaPrimary,
                ),
            )
        }
    }
}

@Composable
private fun ContinueListeningCard(
    book: ShelfBook,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val audiobook = book.audiobook
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(listOf(TerracottaContainer.copy(alpha = 0.35f), Color.Transparent)),
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                audiobook = audiobook,
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CONTINUE LISTENING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TerracottaPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    audiobook.displayTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                audiobook.author?.let {
                    Text(it, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(10.dp))
                ProgressBar(fraction = book.progress.fraction)
                Spacer(modifier = Modifier.height(4.dp))
                Text(progressLabel(book.progress), fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary, contentColor = OnPrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resume", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Wraps a shelf item with its long-press menu. */
@Composable
private fun BookWithActions(
    book: ShelfBook,
    onDetails: (Audiobook) -> Unit,
    onMarkFinished: (Audiobook) -> Unit,
    onResetProgress: (Audiobook) -> Unit,
    onRemove: (Audiobook) -> Unit,
    content: @Composable (showMenu: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        content { menuOpen = true }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val pick: (() -> Unit) -> () -> Unit = { action -> { menuOpen = false; action() } }
            DropdownMenuItem(
                text = { Text("Details & notes") },
                leadingIcon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                onClick = pick { onDetails(book.audiobook) },
            )
            if (book.status != BookStatus.FINISHED) {
                DropdownMenuItem(
                    text = { Text("Mark as finished") },
                    leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                    onClick = pick { onMarkFinished(book.audiobook) },
                )
            }
            if (book.status != BookStatus.NOT_STARTED) {
                DropdownMenuItem(
                    text = { Text("Reset progress") },
                    leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                    onClick = pick { onResetProgress(book.audiobook) },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from library") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = pick { onRemove(book.audiobook) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: ShelfBook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val audiobook = book.audiobook
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onClickLabel = "Open",
                onLongClick = onLongClick,
                onLongClickLabel = "Book options",
            )
            .semantics(mergeDescendants = true) { contentDescription = bookDescription(book) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            BookCover(audiobook = audiobook, modifier = Modifier.fillMaxSize())
            when (book.status) {
                BookStatus.IN_PROGRESS -> Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                        .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                ) {
                    ProgressBar(fraction = book.progress.fraction, trackColor = Color.White.copy(alpha = 0.3f))
                }
                BookStatus.FINISHED -> FinishedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                BookStatus.NOT_STARTED -> Unit
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = audiobook.displayTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
        )
        audiobook.author?.let {
            Text(it, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusLine(book = book)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListItem(
    book: ShelfBook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val audiobook = book.audiobook
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onClickLabel = "Open",
                onLongClick = onLongClick,
                onLongClickLabel = "Book options",
            )
            .semantics(mergeDescendants = true) { contentDescription = bookDescription(book) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            BookCover(
                audiobook = audiobook,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (book.status == BookStatus.FINISHED) {
                FinishedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), size = 18.dp)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = audiobook.displayTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            audiobook.author?.let {
                Text(it, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (book.status == BookStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(6.dp))
                ProgressBar(fraction = book.progress.fraction)
            }
            StatusLine(book = book)
        }
    }
}

@Composable
private fun StatusLine(book: ShelfBook) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Text(
            text = statusLabel(book),
            fontSize = 11.sp,
            color = if (book.status == BookStatus.FINISHED) AccentGreen else TextMuted,
            maxLines = 1,
        )
        if (!book.audiobook.notes.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.StickyNote2,
                contentDescription = null,
                tint = TerracottaPrimary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, trackColor: Color = TextMuted.copy(alpha = 0.3f)) {
    LinearProgressIndicator(
        progress = { fraction },
        color = TerracottaPrimary,
        trackColor = trackColor,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
    )
}

@Composable
private fun FinishedBadge(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AccentGreen),
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.7f))
    }
}

@Composable
private fun NoMatches(onClear: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
    ) {
        Text("No books match", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onClear) {
            Text("Show all books", color = TerracottaPrimary)
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLow,
        title = { Text(title, color = TextPrimary) },
        text = { Text(text, color = TextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = TerracottaPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

private fun statusLabel(book: ShelfBook): String = when (book.status) {
    BookStatus.NOT_STARTED -> "New · ${formatDuration(book.progress.durationMs)}"
    BookStatus.IN_PROGRESS -> progressLabel(book.progress)
    BookStatus.FINISHED -> "Finished"
}

private fun progressLabel(progress: BookProgress): String =
    "${(progress.fraction * 100).toInt()}% · ${formatDuration(progress.remainingMs)} left"

private fun bookDescription(book: ShelfBook): String {
    val a = book.audiobook
    return listOfNotNull(
        a.displayTitle,
        a.author?.let { "by $it" },
        when (book.status) {
            BookStatus.NOT_STARTED -> "not started, ${formatDuration(book.progress.durationMs)}"
            BookStatus.IN_PROGRESS -> "${(book.progress.fraction * 100).toInt()} percent listened, " +
                "${formatDuration(book.progress.remainingMs)} left"
            BookStatus.FINISHED -> "finished"
        },
        "has notes".takeIf { !a.notes.isNullOrBlank() },
    ).joinToString(", ")
}

/** Empty bookshelf on a new phone: only the books waiting to be restored. */
@Composable
private fun RestoreOnlyContent(
    pending: List<SavedBook>,
    onFind: (SavedBook) -> Unit,
    onBrowse: () -> Unit,
    onDiscard: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item(key = "restore-header") {
            RestoreSectionHeader(bookCount = pending.size, onBrowse = onBrowse, onDiscard = onDiscard)
        }
        itemsIndexed(pending, key = { index, book -> "restore-$index-${book.title}" }) { _, book ->
            PendingBookRow(book = book, onFind = { onFind(book) })
        }
    }
}

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
private fun EmptyLibraryContent(
    isScanning: Boolean,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = "No audiobooks",
                tint = TextMuted,
                modifier = Modifier.size(64.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No audiobooks yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick a folder that contains an audiobook. Chapter files and a cover image in the folder are picked up automatically.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBrowseClick,
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerracottaPrimary,
                    disabledContainerColor = TextMuted,
                ),
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = OnPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isScanning) "Importing…" else "Add audiobook folder",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnPrimary,
                )
            }

        }
    }


@Composable
private fun FolderImportDialog(
    prompt: FolderImportPrompt,
    onChapters: () -> Unit,
    onBooks: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLow,
        title = {
            Text(
                "Multiple audio files detected",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                "\"${prompt.folderName}\" contains ${prompt.audioFileCount} audio files. Are these files:",
                color = TextSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onChapters,
                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
            ) {
                Text("Chapters", color = OnPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onBooks,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaPrimary),
            ) {
                Text("Books", fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

private fun formatDuration(durationMs: Long): String {
    val hours = durationMs / (1000 * 60 * 60)
    val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "0m"
    }
}

// ============================

// ============================
// Previews
// ============================

private fun previewBook(id: String, title: String, author: String?, listened: Long, duration: Long) = ShelfBook(
    audiobook = Audiobook(id = id, title = title, author = author, duration = duration, coverArtPath = null, filePath = "mock"),
    progress = BookProgress(listenedMs = listened, durationMs = duration, lastPlayedAt = LocalDateTime.now().takeIf { listened > 0 }),
)

private val previewShelf = listOf(
    previewBook("1", "The Hobbit", "J.R.R. Tolkien", 12_000_000, 34_560_000),
    previewBook("2", "Dune", "Frank Herbert", 0, 75_600_000),
    previewBook("3", "Project Hail Mary", "Andy Weir", 57_600_000, 57_600_000),
    previewBook("4", "He Who Fights with Monsters - Book 03", null, 9_000_000, 88_740_000),
)

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun PreviewLibraryGrid() {
    AudiobookPlayerTheme {
        LibraryContent(
            uiState = LibraryUiState(
                audiobooks = previewShelf.map { it.audiobook },
                shelf = previewShelf,
                visibleBooks = previewShelf,
                continueListening = previewShelf.first(),
            ),
            showHero = true,
            onOpen = {}, onResume = {}, onFilterChange = {}, onClearFilters = {},
            onDetails = {}, onMarkFinished = {}, onResetProgress = {}, onRemove = {},
            onFindPending = {}, onBrowse = {}, onDiscardPending = {},
            modifier = Modifier.background(SurfaceDark),
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun PreviewLibraryList() {
    AudiobookPlayerTheme {
        LibraryContent(
            uiState = LibraryUiState(
                audiobooks = previewShelf.map { it.audiobook },
                shelf = previewShelf,
                visibleBooks = previewShelf,
                viewMode = LibraryViewMode.LIST,
            ),
            showHero = true,
            onOpen = {}, onResume = {}, onFilterChange = {}, onClearFilters = {},
            onDetails = {}, onMarkFinished = {}, onResetProgress = {}, onRemove = {},
            onFindPending = {}, onBrowse = {}, onDiscardPending = {},
            modifier = Modifier.background(SurfaceDark),
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun PreviewEmptyLibraryContent() {
    AudiobookPlayerTheme {
        EmptyLibraryContent(
            isScanning = false,
            onBrowseClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewFolderImportDialog() {
    AudiobookPlayerTheme {
        FolderImportDialog(
            prompt = FolderImportPrompt(folderName = "The Hobbit", audioFileCount = 19),
            onChapters = {},
            onBooks = {},
            onDismiss = {},
        )
    }
}
