package com.aikipunk.betteraudiobookplayer.domain.transfer

import kotlinx.serialization.Serializable

/**
 * What the user has added to their bookshelf, without the audio itself: kept in app storage
 * so Android backup / new-phone transfer can bring it to another device.
 */
@Serializable
data class ShelfSnapshot(
    val version: Int = CURRENT_VERSION,
    val savedAt: String,
    val books: List<SavedBook>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class SavedBook(
    /** Title read from the files (the book's identity); [customTitle] is the user's rename. */
    val title: String,
    val customTitle: String? = null,
    val author: String? = null,
    val narrator: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val notes: String? = null,
    /** Folder holding the audio, relative to its storage volume, e.g. "Audiobooks/Dune". */
    val folderPath: String? = null,
    val durationMs: Long = 0,
    val addedAt: String? = null,
    val tracks: List<SavedTrack> = emptyList(),
    val progress: SavedProgress? = null,
    val bookmarks: List<SavedBookmark> = emptyList(),
) {
    val displayTitle: String get() = customTitle?.takeIf { it.isNotBlank() } ?: title
    val folderName: String? get() = folderPath?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    /** Fraction listened, for showing progress before the book is restored. */
    val progressFraction: Float
        get() {
            val progress = progress ?: return 0f
            if (durationMs <= 0) return 0f
            val listened = tracks.take(progress.trackIndex).sumOf { it.durationMs } + progress.positionMs
            return (listened.toFloat() / durationMs).coerceIn(0f, 1f)
        }
}

@Serializable
data class SavedTrack(
    val fileName: String?,
    val title: String,
    val durationMs: Long,
)

@Serializable
data class SavedProgress(
    val trackIndex: Int,
    val positionMs: Long,
    val speed: Float,
    val lastPlayedAt: String,
)

@Serializable
data class SavedBookmark(
    val trackIndex: Int,
    val positionMs: Long,
    val title: String,
    val description: String? = null,
    val createdAt: String,
)
