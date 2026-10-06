package com.aikipunk.betteraudiobookplayer.domain.usecase

import com.aikipunk.betteraudiobookplayer.testutil.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CreateBookmarkUseCaseTest {
    @Test
    fun `create bookmark persists generated bookmark`() = runTest {
        val repository = FakeBookmarkRepository()
        val bookmark = CreateBookmarkUseCase(repository)("book1", "t1", 123, "Note", "Details")
        assertNotNull(bookmark.id)
        assertEquals(bookmark, repository.added.single())
        assertEquals("Details", bookmark.description)
    }
}
