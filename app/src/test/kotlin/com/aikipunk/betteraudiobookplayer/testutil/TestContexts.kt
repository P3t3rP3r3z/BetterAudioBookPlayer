package com.aikipunk.betteraudiobookplayer.testutil

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.io.File

fun testContext(
    filesDir: File = File("build/test-files/context-${System.nanoTime()}").apply { mkdirs() },
    prefs: SharedPreferences = InMemorySharedPreferences(),
): Context = mockk(relaxed = true) {
    every { this@mockk.filesDir } returns filesDir
    every { getSharedPreferences(any(), any()) } returns prefs
    every { packageName } returns "com.aikipunk.betteraudiobookplayer.test"
}
