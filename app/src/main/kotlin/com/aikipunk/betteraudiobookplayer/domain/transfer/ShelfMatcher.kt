package com.aikipunk.betteraudiobookplayer.domain.transfer

/** Pairs books imported on this phone with books saved from another one. */
object ShelfMatcher {

    /** A book as imported here, described the same way as [SavedBook]. */
    data class ImportedBook(
        val title: String,
        val folderName: String?,
        val trackFileNames: List<String?>,
    )

    /** An audio file found on the phone (from the media index). */
    data class AudioFile(
        val volume: String,
        val folderPath: String,
        val fileName: String,
        val album: String?,
    )

    data class FolderCandidate(
        val volume: String,
        val folderPath: String,
        val audioFileCount: Int,
        val matchedFiles: Int,
    )

    /**
     * The saved book [imported] is, or null. A title/folder name match wins; otherwise most of the
     * saved file names must be present, with no other saved book matching as well.
     */
    fun findSaved(imported: ImportedBook, saved: List<SavedBook>): SavedBook? {
        val importedFiles = imported.trackFileNames.mapNotNull { it?.let(::normalize) }.toSet()
        val importedNames = setOfNotNull(normalize(imported.title), imported.folderName?.let(::normalize))
            .filter { it.isNotEmpty() }
        val scored = saved.mapNotNull { book ->
            val nameMatch = bookNames(book).any { it in importedNames }
            val overlap = fileOverlap(book, importedFiles)
            val filesMatch = book.tracks.isNotEmpty() && overlap * 2 >= book.tracks.size
            when {
                nameMatch -> book to (2_000 + overlap - trackCountGap(book, imported))
                filesMatch && book.tracks.size == imported.trackFileNames.size -> book to (1_000 + overlap)
                else -> null
            }
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        val ambiguous = best.second < 2_000 && scored.count { it.second == best.second } > 1
        return if (ambiguous) null else best.first
    }

    /** Where a saved chapter is in the imported book: same file name, else same position. */
    fun importedTrackIndex(saved: SavedBook, savedIndex: Int, importedFileNames: List<String?>): Int? {
        val fileName = saved.tracks.getOrNull(savedIndex)?.fileName?.let(::normalize)
        if (fileName != null) {
            val byName = importedFileNames.indexOfFirst { it != null && normalize(it) == fileName }
            if (byName >= 0) return byName
        }
        return savedIndex.takeIf { it in importedFileNames.indices }
    }

    /** Folders on the phone that look like [book], best first. */
    fun candidateFolders(book: SavedBook, files: List<AudioFile>): List<FolderCandidate> {
        val names = bookNames(book)
        val savedFiles = book.tracks.mapNotNull { it.fileName?.let(::normalize) }.toSet()
        return files
            .groupBy { it.volume to it.folderPath.trimEnd('/') }
            .mapNotNull { (key, folderFiles) ->
                val folderName = normalize(key.second.substringAfterLast('/'))
                val matched = folderFiles.count { normalize(it.fileName) in savedFiles }
                val nameMatch = folderName in names ||
                    folderFiles.any { file -> file.album?.let(::normalize)?.let { it in names } == true }
                val filesMatch = savedFiles.isNotEmpty() && matched * 2 >= savedFiles.size
                if (!nameMatch && !filesMatch) return@mapNotNull null
                FolderCandidate(key.first, key.second, folderFiles.size, matched) to
                    ((if (nameMatch) 1 else 0) to matched)
            }
            .sortedWith(compareByDescending<Pair<FolderCandidate, Pair<Int, Int>>> { it.second.first }.thenByDescending { it.second.second })
            .map { it.first }
    }

    private fun bookNames(book: SavedBook): Set<String> =
        setOfNotNull(book.title, book.customTitle, book.folderName)
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toSet()

    private fun fileOverlap(book: SavedBook, importedFiles: Set<String>): Int =
        book.tracks.count { track -> track.fileName?.let(::normalize)?.let { it in importedFiles } == true }

    private fun trackCountGap(book: SavedBook, imported: ImportedBook): Int =
        kotlin.math.abs(book.tracks.size - imported.trackFileNames.size)

    /** Case, spacing and punctuation don't matter: "The Hobbit (Unabridged)" ~ "the_hobbit_unabridged". */
    internal fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
}
