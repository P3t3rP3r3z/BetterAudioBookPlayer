package com.aikipunk.betteraudiobookplayer.domain.library

import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook

enum class LibrarySort(val label: String) {
    RECENT("Recently played"),
    TITLE("Title"),
    AUTHOR("Author"),
    DATE_ADDED("Date added"),
    PROGRESS("Progress"),
}

enum class LibraryFilter(val label: String) {
    ALL("All"),
    IN_PROGRESS("In progress"),
    NOT_STARTED("Not started"),
    FINISHED("Finished"),
}

enum class LibraryViewMode { GRID, LIST }

data class ShelfBook(
    val audiobook: Audiobook,
    val progress: BookProgress,
) {
    val status: BookStatus get() = progress.status
}

object LibraryShelf {

    /** Applies search, filter and sort, in that order. */
    fun arrange(
        books: List<ShelfBook>,
        query: String,
        filter: LibraryFilter,
        sort: LibrarySort,
    ): List<ShelfBook> = books
        .filter { matches(it.audiobook, query) }
        .filter { matches(it.status, filter) }
        .sortedWith(comparator(sort))

    /** The most recently played book that's still in progress. */
    fun continueListening(books: List<ShelfBook>): ShelfBook? = books
        .filter { it.status == BookStatus.IN_PROGRESS }
        .mapNotNull { book -> book.progress.lastPlayedAt?.let { book to it } }
        .maxByOrNull { it.second }
        ?.first

    fun matches(book: Audiobook, query: String): Boolean {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val haystack = searchText(book)
        return terms.all { it in haystack }
    }

    /** Lower-case text a search looks through: titles, author, narrator, publisher and year. */
    fun searchText(book: Audiobook): String = listOfNotNull(
        book.displayTitle,
        book.title,
        book.author,
        book.narrator,
        book.publisher,
        book.publishedYear?.toString(),
    )
        .joinToString(" ")
        .lowercase()

    private fun matches(status: BookStatus, filter: LibraryFilter): Boolean = when (filter) {
        LibraryFilter.ALL -> true
        LibraryFilter.IN_PROGRESS -> status == BookStatus.IN_PROGRESS
        LibraryFilter.NOT_STARTED -> status == BookStatus.NOT_STARTED
        LibraryFilter.FINISHED -> status == BookStatus.FINISHED
    }

    private val byTitle: Comparator<ShelfBook> =
        compareBy(String.CASE_INSENSITIVE_ORDER) { sortableTitle(it.audiobook.displayTitle) }

    private fun comparator(sort: LibrarySort): Comparator<ShelfBook> = when (sort) {
        // Never-played books follow, newest additions first
        LibrarySort.RECENT -> compareByDescending<ShelfBook> { it.progress.lastPlayedAt }
            .thenByDescending { it.audiobook.createdAt }
        LibrarySort.TITLE -> byTitle
        // Books without an author go last
        LibrarySort.AUTHOR -> compareBy<ShelfBook> { it.audiobook.author.isNullOrBlank() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.audiobook.author.orEmpty() }
            .then(byTitle)
        LibrarySort.DATE_ADDED -> compareByDescending<ShelfBook> { it.audiobook.createdAt }
        LibrarySort.PROGRESS -> compareByDescending<ShelfBook> { it.progress.fraction }.then(byTitle)
    }

    /** "The Hobbit" sorts under H. */
    internal fun sortableTitle(title: String): String {
        val trimmed = title.trim()
        val article = listOf("the ", "a ", "an ").firstOrNull { trimmed.lowercase().startsWith(it) }
        return if (article != null && trimmed.length > article.length) trimmed.substring(article.length) else trimmed
    }
}
