package com.aikipunk.betteraudiobookplayer.presentation.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikipunk.betteraudiobookplayer.domain.bookmark.BookmarkExport
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.*

private enum class BookmarkSort(val label: String) { POSITION("Position in book"), NEWEST("Newest first") }

/** A chapter header or a bookmark in the (possibly grouped) list. */
private sealed interface BookmarkListEntry {
    val key: String

    data class Header(val chapterIndex: Int, val title: String?) : BookmarkListEntry {
        override val key = "chapter-$chapterIndex"
    }

    data class Item(val bookmark: Bookmark) : BookmarkListEntry {
        override val key = bookmark.id
    }
}

@Composable
internal fun BookmarksContent(
    bookTitle: String,
    bookmarks: List<Bookmark>,
    tracks: List<Track>,
    onBookmarkSelected: (Bookmark) -> Unit,
    onBookmarkUpdated: (Bookmark) -> Unit,
    onDeleteBookmarks: (Set<String>) -> Unit,
    onExport: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(BookmarkSort.POSITION) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Drop selections whose bookmarks no longer exist (e.g. after deleting)
    val existingIds = bookmarks.map { it.id }.toSet()
    if (!existingIds.containsAll(selectedIds)) selectedIds = selectedIds intersect existingIds
    if (selectionMode && bookmarks.isEmpty()) selectionMode = false

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let(onExport) }

    val entries = remember(bookmarks, tracks, query, sort) { buildEntries(bookmarks, tracks, query, sort) }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onClose = {
                    selectionMode = false
                    selectedIds = emptySet()
                },
                onSelectAll = {
                    selectedIds = entries.filterIsInstance<BookmarkListEntry.Item>().map { it.bookmark.id }.toSet()
                },
                onDelete = { if (selectedIds.isNotEmpty()) confirmDelete = true },
            )
        } else {
            BookmarksHeader(
                hasBookmarks = bookmarks.isNotEmpty(),
                sort = sort,
                onSortChange = { sort = it },
                onExport = { exportLauncher.launch(exportFileName(bookTitle)) },
            )
        }

        if (bookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No bookmarks yet.\nTap the bookmark button on the cover to save your place.",
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search bookmarks and notes") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TerracottaPrimary,
                unfocusedBorderColor = SurfaceBright,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = TerracottaPrimary,
                focusedLeadingIconColor = TextSecondary,
                unfocusedLeadingIconColor = TextSecondary,
                focusedPlaceholderColor = TextMuted,
                unfocusedPlaceholderColor = TextMuted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No bookmarks match \u201c$query\u201d", color = TextSecondary, textAlign = TextAlign.Center)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!selectionMode) {
                item(key = "hint") {
                    Text(
                        text = "Tap to jump \u2022 Press and hold to select",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            }
            items(entries, key = { it.key }) { entry ->
                when (entry) {
                    is BookmarkListEntry.Header -> ChapterHeader(entry)
                    is BookmarkListEntry.Item -> {
                        val bookmark = entry.bookmark
                        val selected = bookmark.id in selectedIds
                        BookmarkRow(
                            bookmark = bookmark,
                            location = BookmarkExport.location(bookmark, tracks),
                            selectionMode = selectionMode,
                            selected = selected,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (selected) selectedIds - bookmark.id else selectedIds + bookmark.id
                                } else {
                                    onBookmarkSelected(bookmark)
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedIds = selectedIds + bookmark.id
                            },
                            onEdit = { editing = bookmark },
                        )
                    }
                }
            }
        }
    }

    editing?.let { bookmark ->
        BookmarkEditDialog(
            bookmark = bookmark,
            onSave = {
                onBookmarkUpdated(it)
                editing = null
            },
            onDelete = {
                onDeleteBookmarks(setOf(bookmark.id))
                editing = null
            },
            onShare = { shareBookmark(context, bookTitle, it, tracks) },
            onDismiss = { editing = null },
        )
    }

    if (confirmDelete) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (count == 1) "Delete bookmark?" else "Delete $count bookmarks?") },
            text = { Text("Their notes will be deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBookmarks(selectedIds)
                    selectedIds = emptySet()
                    selectionMode = false
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun buildEntries(
    bookmarks: List<Bookmark>,
    tracks: List<Track>,
    query: String,
    sort: BookmarkSort,
): List<BookmarkListEntry> {
    val q = query.trim()
    val matching = if (q.isEmpty()) {
        bookmarks
    } else {
        bookmarks.filter { it.title.contains(q, ignoreCase = true) || it.description.orEmpty().contains(q, ignoreCase = true) }
    }
    val chapterIndex = tracks.withIndex().associate { (i, t) -> t.id to i }
    return when (sort) {
        BookmarkSort.NEWEST -> matching.sortedByDescending { it.createdAt }.map { BookmarkListEntry.Item(it) }
        BookmarkSort.POSITION -> {
            val sorted = matching.sortedWith(compareBy({ chapterIndex[it.trackId] ?: Int.MAX_VALUE }, { it.positionMs }))
            if (tracks.size <= 1) {
                sorted.map { BookmarkListEntry.Item(it) }
            } else {
                sorted.groupBy { chapterIndex[it.trackId] ?: -1 }.flatMap { (index, group) ->
                    val header = BookmarkListEntry.Header(index, tracks.getOrNull(index)?.title?.takeIf { it.isNotBlank() })
                    listOf(header) + group.map { BookmarkListEntry.Item(it) }
                }
            }
        }
    }
}

@Composable
private fun BookmarksHeader(
    hasBookmarks: Boolean,
    sort: BookmarkSort,
    onSortChange: (BookmarkSort) -> Unit,
    onExport: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Bookmarks",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (hasBookmarks) {
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort: ${sort.label}", tint = TextPrimary)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    BookmarkSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                            onClick = {
                                onSortChange(option)
                                sortMenu = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Outlined.Download, contentDescription = "Export bookmarks", tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "Cancel selection", tint = TextPrimary)
        }
        Text(
            text = "$count selected",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = onSelectAll) {
            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all", tint = TextPrimary)
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete selected",
                tint = if (count > 0) TerracottaPrimary else TextMuted,
            )
        }
    }
}

@Composable
private fun ChapterHeader(header: BookmarkListEntry.Header) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = if (header.chapterIndex >= 0) "Chapter ${header.chapterIndex + 1}" else "Other",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TerracottaPrimary,
        )
        header.title?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(it, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    location: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TerracottaContainer else SurfaceContainerLow)
            .combinedClickable(
                onClick = onClick,
                onClickLabel = when {
                    !selectionMode -> "Jump to bookmark"
                    selected -> "Deselect"
                    else -> "Select"
                },
                onLongClick = if (selectionMode) null else onLongClick,
                onLongClickLabel = if (selectionMode) null else "Select bookmarks",
            )
            .then(
                if (selectionMode) {
                    Modifier.semantics { stateDescription = if (selected) "Selected" else "Not selected" }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = TerracottaPrimary, checkmarkColor = OnPrimary),
                modifier = Modifier.size(22.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = TerracottaPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                text = bookmark.title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = location, color = TextSecondary, fontSize = 12.sp)
            if (!bookmark.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookmark.description,
                    color = TextMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!selectionMode) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.EditNote, contentDescription = "Edit ${bookmark.title}", tint = TextSecondary)
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookmarkEditDialog(
    bookmark: Bookmark,
    onSave: (Bookmark) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    dialogTitle: String = "Edit bookmark",
    onJump: (() -> Unit)? = null,
    onShare: ((Bookmark) -> Unit)? = null,
) {
    var title by remember(bookmark.id) { mutableStateOf(bookmark.title) }
    var note by remember(bookmark.id) { mutableStateOf(bookmark.description.orEmpty()) }
    val edited = {
        bookmark.copy(
            title = title.trim().ifEmpty { bookmark.title },
            description = note.trim().ifEmpty { null },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow {
                    if (onJump != null) {
                        TextButton(onClick = onJump) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play from here")
                        }
                    }
                    if (onShare != null) {
                        TextButton(onClick = { onShare(edited()) }) {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

internal fun shareBookmark(context: Context, bookTitle: String, bookmark: Bookmark, tracks: List<Track>) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "$bookTitle — ${bookmark.title}")
        .putExtra(Intent.EXTRA_TEXT, BookmarkExport.shareText(bookTitle, bookmark, tracks))
    context.startActivity(Intent.createChooser(send, "Share bookmark"))
}

private fun exportFileName(bookTitle: String): String =
    bookTitle.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Audiobook" } + " bookmarks.md"
