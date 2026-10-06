package com.aikipunk.betteraudiobookplayer.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.TextSecondary

/**
 * Book details: title (stored as a preferred title; files are untouched), author, narrator,
 * publisher, year, notes, and remove from library. A blank title restores the original.
 */
@Composable
fun BookDetailsDialog(
    audiobook: Audiobook,
    onSave: (BookDetails) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(audiobook.id) { mutableStateOf(audiobook.displayTitle) }
    var author by rememberSaveable(audiobook.id) { mutableStateOf(audiobook.author.orEmpty()) }
    var narrator by rememberSaveable(audiobook.id) { mutableStateOf(audiobook.narrator.orEmpty()) }
    var publisher by rememberSaveable(audiobook.id) { mutableStateOf(audiobook.publisher.orEmpty()) }
    var year by rememberSaveable(audiobook.id) { mutableStateOf(audiobook.publishedYear?.toString().orEmpty()) }
    var notes by rememberSaveable(audiobook.id) { mutableStateOf(audiobook.notes.orEmpty()) }
    val details = BookDetails(title, author, narrator, publisher, year, notes)
    var confirmRemove by rememberSaveable(audiobook.id) { mutableStateOf(false) }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove \"${audiobook.displayTitle}\"?") },
            text = {
                Text("Its bookmarks, notes and saved position will be removed. Your audio files are not deleted.")
            },
            confirmButton = { TextButton(onClick = onRemove) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(audiobook.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val summary = listOf(
                    "${audiobook.totalTracks} ${if (audiobook.totalTracks == 1) "track" else "tracks"}",
                    formatBookDuration(audiobook.duration),
                ).joinToString(" • ")
                Text(summary, fontSize = 13.sp, color = TextSecondary)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    supportingText = if (audiobook.customTitle != null || title.trim() != audiobook.title) {
                        { Text("Original: ${audiobook.title}. Clear to restore it.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DetailField("Author", author, { author = it })
                DetailField("Narrator", narrator, { narrator = it })
                DetailField("Publisher", publisher, { publisher = it })
                OutlinedTextField(
                    value = year,
                    // Digits only, at most four
                    onValueChange = { input -> year = input.filter(Char::isDigit).take(4) },
                    label = { Text("Year published") },
                    singleLine = true,
                    isError = !details.isYearValid,
                    supportingText = if (!details.isYearValid) ({ Text("Enter a four-digit year") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Thoughts, characters, where you heard about it…") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { confirmRemove = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remove book from library")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(details) }, enabled = details.isYearValid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DetailField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatBookDuration(durationMs: Long): String {
    val hours = durationMs / (1000 * 60 * 60)
    val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
