package com.aikipunk.betteraudiobookplayer.domain.about

data class OpenSourceLicense(val name: String, val url: String?, val text: String?)

data class OpenSourceLibrary(
    val id: String,
    val name: String,
    val version: String?,
    val author: String?,
    val website: String?,
    val licenses: List<OpenSourceLicense>,
) {
    companion object {
        /**
         * Drops entries that ship no code (BOMs, lint-rule jars), merges duplicate names, and sorts
         * alphabetically so the credits read cleanly.
         */
        fun forDisplay(libraries: List<OpenSourceLibrary>): List<OpenSourceLibrary> =
            libraries
                .filterNot { lib -> NON_CODE_SUFFIXES.any { lib.id.substringBefore('@').endsWith(it) } }
                .distinctBy { it.name.lowercase() }
                .sortedBy { it.name.lowercase() }

        private val NON_CODE_SUFFIXES = listOf("-bom", "-lint-aar")
    }
}
