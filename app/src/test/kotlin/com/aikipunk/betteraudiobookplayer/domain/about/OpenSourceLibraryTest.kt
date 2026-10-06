package com.aikipunk.betteraudiobookplayer.domain.about

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSourceLibraryTest {

    private fun lib(id: String, name: String) =
        OpenSourceLibrary(id, name, version = "1.0", author = null, website = null, licenses = emptyList())

    @Test
    fun `drops BOMs and lint-rule artifacts`() {
        val shown = OpenSourceLibrary.forDisplay(
            listOf(
                lib("org.jetbrains.kotlinx:kotlinx-coroutines-bom", "kotlinx-coroutines-bom"),
                lib("com.google.dagger:dagger-lint-aar", "Dagger Lint Rules"),
                lib("com.google.dagger:dagger", "Dagger"),
            ),
        )
        assertEquals(listOf("Dagger"), shown.map { it.name })
    }

    @Test
    fun `sorts by name ignoring case and merges duplicate names`() {
        val shown = OpenSourceLibrary.forDisplay(
            listOf(
                lib("io.coil-kt:coil", "coil"),
                lib("androidx.activity:activity", "Activity"),
                lib("com.mikepenz:aboutlibraries-core-android", "AboutLibraries Library"),
                lib("com.mikepenz:aboutlibraries-core", "AboutLibraries library"),
            ),
        )
        assertEquals(listOf("AboutLibraries Library", "Activity", "coil"), shown.map { it.name })
    }
}
