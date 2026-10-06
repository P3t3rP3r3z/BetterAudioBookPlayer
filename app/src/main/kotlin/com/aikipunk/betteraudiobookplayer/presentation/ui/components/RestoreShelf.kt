package com.aikipunk.betteraudiobookplayer.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBook
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedProgress
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedTrack
import com.aikipunk.betteraudiobookplayer.domain.transfer.ShelfMatcher
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.OnPrimary
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.SurfaceContainerLow
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.SurfaceDark
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.TerracottaPrimary
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.TextMuted
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.TextPrimary
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.TextSecondary

/** Asked at startup when a previous phone's bookshelf was found. */
@Composable
fun RestoreBookshelfDialog(
    bookCount: Int,
    onRestore: () -> Unit,
    onNotNow: () -> Unit,
    onStartFresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        containerColor = SurfaceContainerLow,
        icon = { Icon(Icons.Outlined.PhonelinkSetup, contentDescription = null, tint = TerracottaPrimary) },
        title = { Text("Restore your bookshelf?", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "We found your bookshelf from before, with ${plural(bookCount, "book")}. Restoring brings back " +
                    "your progress, bookmarks, notes and book details as you add each book's audio files again.",
                color = TextSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onRestore,
                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
            ) { Text("Restore", color = OnPrimary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onStartFresh) { Text("Start fresh", color = TextSecondary) }
                TextButton(onClick = onNotNow) { Text("Not now", color = TextSecondary) }
            }
        },
    )
}

/** Heading of the "From your previous phone" section on the bookshelf. */
@Composable
fun RestoreSectionHeader(
    bookCount: Int,
    onBrowse: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            "From your previous phone",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "${plural(bookCount, "book")} waiting for audio files. Find each one, or add the folder that holds " +
                "them all — matching books get their progress, bookmarks and notes back.",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBrowse,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaPrimary),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add folder")
            }
            TextButton(onClick = onDiscard) { Text("Don't restore", color = TextSecondary) }
        }
    }
}

/** A saved book that hasn't been imported yet. */
@Composable
fun PendingBookRow(
    book: SavedBook,
    onFind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = (book.progressFraction * 100).toInt()
    val status = listOfNotNull(
        if (percent > 0) "$percent% listened" else "Not started",
        book.bookmarks.size.takeIf { it > 0 }?.let { plural(it, "bookmark") },
        "notes".takeIf { !book.notes.isNullOrBlank() },
    ).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(book.displayTitle, book.author?.let { "by $it" }, status)
                    .joinToString(", ")
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GeneratedCover(
            title = book.displayTitle,
            author = book.author,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                book.displayTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let { Text(it, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(status, fontSize = 11.sp, color = TextMuted, maxLines = 1)
        }
        // 48dp minimum touch target
        TextButton(onClick = onFind, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Find", color = TerracottaPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Folders on the phone that may hold [book]'s audio; picking one opens the folder picker there. */
@Composable
fun RestoreSearchDialog(
    book: SavedBook,
    isSearching: Boolean,
    candidates: List<ShelfMatcher.FolderCandidate>,
    onPick: (ShelfMatcher.FolderCandidate) -> Unit,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLow,
        title = {
            Text(
                "Find \"${book.displayTitle}\"",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    isSearching -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TerracottaPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Searching this phone…", color = TextSecondary)
                    }
                    candidates.isEmpty() -> Text(
                        book.folderPath?.let { "Couldn't find it on this phone. It was in \"$it\" before — copy that folder over, or browse for it." }
                            ?: "Couldn't find it on this phone. Copy its audio files over, or browse for them.",
                        color = TextSecondary,
                    )
                    else -> {
                        Text("Choose the folder, then tap \"Use this folder\" to add it.", color = TextSecondary)
                        candidates.take(MAX_CANDIDATES).forEach { candidate ->
                            CandidateRow(candidate, onClick = { onPick(candidate) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onBrowse) { Text("Browse…", color = TerracottaPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

@Composable
private fun CandidateRow(candidate: ShelfMatcher.FolderCandidate, onClick: () -> Unit) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Open folder", onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = TerracottaPrimary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    candidate.folderPath.substringAfterLast('/').ifEmpty { "Internal storage" },
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${candidate.folderPath} · ${plural(candidate.audioFileCount, "audio file")}",
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val MAX_CANDIDATES = 4

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

@Preview
@Composable
private fun PreviewPendingBookRow() {
    Box(Modifier.padding(16.dp)) {
        PendingBookRow(
            book = SavedBook(
                title = "Dune",
                author = "Frank Herbert",
                durationMs = 10_000_000,
                tracks = listOf(SavedTrack("01.mp3", "Chapter 1", 5_000_000), SavedTrack("02.mp3", "Chapter 2", 5_000_000)),
                progress = SavedProgress(1, 1_000_000, 1.2f, "2026-09-01T10:00"),
            ),
            onFind = {},
        )
    }
}

@Preview
@Composable
private fun PreviewRestoreSearchDialog() {
    RestoreSearchDialog(
        book = SavedBook(title = "Dune", folderPath = "Audiobooks/Dune"),
        isSearching = false,
        candidates = listOf(ShelfMatcher.FolderCandidate("external_primary", "Audiobooks/Dune", 24, 24)),
        onPick = {},
        onBrowse = {},
        onDismiss = {},
    )
}
