package com.aikipunk.betteraudiobookplayer.data.filediscovery

/**
 * Pure rules for turning the contents of a user-selected folder into one audiobook.
 * Kept free of Android types so they can be unit tested on the JVM.
 */
internal object FolderBookRules {

    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "flac", "ogg", "opus", "aac", "wav")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

    // Checked in priority order against the file name without extension
    private val PREFERRED_COVER_NAMES = listOf("cover", "folder", "front", "album", "artwork")

    fun isAudio(name: String, mimeType: String?): Boolean =
        !isHidden(name) && (mimeType?.startsWith("audio/") == true || extensionOf(name) in AUDIO_EXTENSIONS)

    fun isImage(name: String, mimeType: String?): Boolean =
        !isHidden(name) && (mimeType?.startsWith("image/") == true || extensionOf(name) in IMAGE_EXTENSIONS)

    /** Returns the index of the best cover image in [imageNames], or null if the list is empty. */
    fun pickCoverIndex(imageNames: List<String>): Int? {
        if (imageNames.isEmpty()) return null
        val baseNames = imageNames.map { titleFromFileName(it).lowercase() }
        for (preferred in PREFERRED_COVER_NAMES) {
            val index = baseNames.indexOf(preferred)
            if (index >= 0) return index
        }
        return imageNames.indices.minWith { a, b -> naturalOrder.compare(imageNames[a], imageNames[b]) }
    }

    fun titleFromFileName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /**
     * When a folder's files are treated as separate books: a loose file is its own book,
     * and everything under a top-level sub-folder belongs to that sub-folder's book.
     */
    fun bookGroupKey(relativePath: String): String = relativePath.substringBefore('/')

    /** Index of an image whose base name matches [audioName] (e.g. Dune.mp3 -> Dune.jpg). */
    fun matchingCoverIndex(audioName: String, imageNames: List<String>): Int? {
        val base = titleFromFileName(audioName).lowercase()
        return imageNames.indexOfFirst { titleFromFileName(it).lowercase() == base }.takeIf { it >= 0 }
    }

    /** Case-insensitive ordering that compares digit runs numerically ("Ch 2" < "Ch 10"). */
    val naturalOrder: Comparator<String> = Comparator { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return@Comparator numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return@Comparator cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return@Comparator cmp
                i++
                j++
            }
        }
        (a.length - i) - (b.length - j)
    }

    private fun isHidden(name: String) = name.startsWith(".")

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
}
