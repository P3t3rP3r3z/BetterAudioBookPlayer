package com.aikipunk.betteraudiobookplayer.domain.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoTest {

    @Test
    fun `copyright and license terms identify developer ownership and privacy stance`() {
        assertEquals("Aikipunk Creations", AppInfo.DEVELOPER)
        assertEquals("© 2026 Aikipunk Creations. All rights reserved.", AppInfo.COPYRIGHT)
        assertTrue(AppInfo.LICENSE_TERMS.any { it.contains("proprietary software") })
        assertTrue(AppInfo.LICENSE_TERMS.any { it.contains("progress, bookmarks and notes on your device") })
    }
}
