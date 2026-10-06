package com.aikipunk.betteraudiobookplayer.domain.library

import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import java.time.Year

/** The user-editable details of a book, as typed into the details dialog. */
data class BookDetails(
    val title: String,
    val author: String = "",
    val narrator: String = "",
    val publisher: String = "",
    val year: String = "",
    val notes: String = "",
) {
    /** True when [year] is blank or a plausible publication year. */
    val isYearValid: Boolean get() = year.isBlank() || parseYear(year) != null

    /**
     * A blank or unchanged title clears the custom title so the book falls back to its
     * file-derived name. Blank fields are stored as null.
     */
    fun applyTo(audiobook: Audiobook): Audiobook {
        val trimmedTitle = title.trim()
        return audiobook.copy(
            customTitle = trimmedTitle.takeIf { it.isNotEmpty() && it != audiobook.title },
            author = author.cleaned(),
            narrator = narrator.cleaned(),
            publisher = publisher.cleaned(),
            publishedYear = parseYear(year),
            notes = notes.cleaned(),
        )
    }

    companion object {
        fun of(audiobook: Audiobook) = BookDetails(
            title = audiobook.displayTitle,
            author = audiobook.author.orEmpty(),
            narrator = audiobook.narrator.orEmpty(),
            publisher = audiobook.publisher.orEmpty(),
            year = audiobook.publishedYear?.toString().orEmpty(),
            notes = audiobook.notes.orEmpty(),
        )

        /**
         * Reads a year from user input or a tag such as "2019" or "2019-05-14". Anything outside
         * 1000..next year is rejected.
         */
        fun parseYear(raw: String?): Int? {
            val year = raw?.trim()?.take(4)?.takeIf { it.length == 4 }?.toIntOrNull() ?: return null
            return year.takeIf { it in 1000..(Year.now().value + 1) }
        }

        private fun String.cleaned(): String? = trim().ifEmpty { null }
    }
}
