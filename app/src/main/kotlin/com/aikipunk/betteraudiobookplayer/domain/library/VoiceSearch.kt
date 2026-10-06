package com.aikipunk.betteraudiobookplayer.domain.library

/**
 * Finds the book a voice request means, such as "play Frankenstein by Mary Shelley" from Google
 * Assistant, Gemini or Android Auto.
 */
object VoiceSearch {
    /** android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH */
    const val ACTION_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"

    private val FILLER_WORDS = setOf(
        "a", "an", "the", "by", "of", "on", "to", "my", "and", "from",
        "play", "listen", "resume", "continue", "read", "narrated",
        "audiobook", "audiobooks", "audio", "book", "books", "novel",
    )

    /** The spoken query from a play-from-search intent ("" means "play something"), or null for other intents. */
    fun queryFrom(action: String?, query: String?): String? =
        if (action == ACTION_PLAY_FROM_SEARCH) query.orEmpty() else null

    /**
     * A blank query resumes the most recently played book. Otherwise every word must match, as in
     * the library search; failing that, the book matching the most meaningful words wins as long
     * as it matches at least half of them. Ties go to the most recently played book.
     */
    fun bestMatch(books: List<ShelfBook>, query: String): ShelfBook? {
        val recentFirst = LibraryShelf.arrange(books, "", LibraryFilter.ALL, LibrarySort.RECENT)
        if (query.isBlank()) return recentFirst.firstOrNull()

        recentFirst.firstOrNull { LibraryShelf.matches(it.audiobook, query) }?.let { return it }

        val words = query.lowercase()
            .replace("'s", "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 && it !in FILLER_WORDS }
            .distinct()
        if (words.isEmpty()) return null

        val scored = recentFirst.map { book ->
            val text = LibraryShelf.searchText(book.audiobook)
            book to words.count { it in text }
        }
        val best = scored.maxOfOrNull { it.second } ?: return null
        if (best == 0 || best * 2 < words.size) return null
        return scored.first { it.second == best }.first
    }
}
