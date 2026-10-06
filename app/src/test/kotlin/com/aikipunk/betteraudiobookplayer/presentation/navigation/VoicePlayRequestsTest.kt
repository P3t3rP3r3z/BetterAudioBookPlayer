package com.aikipunk.betteraudiobookplayer.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePlayRequestsTest {
    @Test
    fun `request is pending until handled`() {
        val requests = VoicePlayRequests()
        assertNull(requests.pendingBookId.value)

        requests.request("book1")
        assertEquals("book1", requests.pendingBookId.value)

        requests.handled("book1")
        assertNull(requests.pendingBookId.value)
    }

    @Test
    fun `handling an older request keeps a newer one`() {
        val requests = VoicePlayRequests()
        requests.request("book1")
        requests.request("book2")

        requests.handled("book1")
        assertEquals("book2", requests.pendingBookId.value)
    }
}
