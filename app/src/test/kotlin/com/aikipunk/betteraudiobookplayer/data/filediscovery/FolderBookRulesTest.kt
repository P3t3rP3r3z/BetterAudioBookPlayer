package com.aikipunk.betteraudiobookplayer.data.filediscovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderBookRulesTest {

    @Test
    fun `isAudio accepts audio mime types and known extensions`() {
        assertTrue(FolderBookRules.isAudio("Chapter 01.mp3", null))
        assertTrue(FolderBookRules.isAudio("book.M4B", "application/octet-stream"))
        assertTrue(FolderBookRules.isAudio("track", "audio/mpeg"))
        assertFalse(FolderBookRules.isAudio("cover.jpg", "image/jpeg"))
        assertFalse(FolderBookRules.isAudio("notes.txt", "text/plain"))
    }

    @Test
    fun `isAudio ignores hidden files`() {
        assertFalse(FolderBookRules.isAudio("._Chapter 01.mp3", "audio/mpeg"))
    }

    @Test
    fun `isImage accepts image mime types and known extensions`() {
        assertTrue(FolderBookRules.isImage("cover.JPG", null))
        assertTrue(FolderBookRules.isImage("art", "image/png"))
        assertFalse(FolderBookRules.isImage("Chapter 01.mp3", "audio/mpeg"))
        assertFalse(FolderBookRules.isImage(".cover_art.jpg", "image/jpeg"))
    }

    @Test
    fun `pickCoverIndex prefers conventional cover names`() {
        val names = listOf("back.jpg", "Folder.png", "cover.jpg")
        assertEquals(2, FolderBookRules.pickCoverIndex(names))
    }

    @Test
    fun `pickCoverIndex falls back to folder then front`() {
        assertEquals(1, FolderBookRules.pickCoverIndex(listOf("back.jpg", "folder.jpg", "front.jpg")))
        assertEquals(0, FolderBookRules.pickCoverIndex(listOf("front.webp", "back.jpg")))
    }

    @Test
    fun `pickCoverIndex uses first image alphabetically when no conventional name`() {
        assertEquals(1, FolderBookRules.pickCoverIndex(listOf("zebra.jpg", "Artwork Scan.png", "photo.jpg")))
    }

    @Test
    fun `pickCoverIndex returns null when there are no images`() {
        assertNull(FolderBookRules.pickCoverIndex(emptyList()))
    }

    @Test
    fun `naturalOrder sorts numbers numerically and is case insensitive`() {
        val paths = listOf("Chapter 10.mp3", "chapter 2.mp3", "Chapter 1.mp3")
        assertEquals(
            listOf("Chapter 1.mp3", "chapter 2.mp3", "Chapter 10.mp3"),
            paths.sortedWith(FolderBookRules.naturalOrder),
        )
    }

    @Test
    fun `naturalOrder keeps disc subfolders in order`() {
        val paths = listOf("CD2/01.mp3", "CD10/01.mp3", "CD1/02.mp3", "CD1/01.mp3")
        assertEquals(
            listOf("CD1/01.mp3", "CD1/02.mp3", "CD2/01.mp3", "CD10/01.mp3"),
            paths.sortedWith(FolderBookRules.naturalOrder),
        )
    }

    @Test
    fun `titleFromFileName strips the extension`() {
        assertEquals("Chapter 01", FolderBookRules.titleFromFileName("Chapter 01.mp3"))
        assertEquals("NoExtension", FolderBookRules.titleFromFileName("NoExtension"))
    }

    @Test
    fun `bookGroupKey groups subfolder files by their top folder and keeps loose files separate`() {
        assertEquals("Dune.mp3", FolderBookRules.bookGroupKey("Dune.mp3"))
        assertEquals("Foundation", FolderBookRules.bookGroupKey("Foundation/01.mp3"))
        assertEquals("Foundation", FolderBookRules.bookGroupKey("Foundation/CD1/01.mp3"))
    }

    @Test
    fun `matchingCoverIndex finds an image with the same base name as the audio file`() {
        val images = listOf("cover.jpg", "dune.PNG", "Foundation.jpg")
        assertEquals(1, FolderBookRules.matchingCoverIndex("Dune.mp3", images))
        assertNull(FolderBookRules.matchingCoverIndex("Hyperion.m4b", images))
    }

    @Test
    fun `isAudio accepts less common audiobook extensions and rejects extensionless non audio files`() {
        assertTrue(FolderBookRules.isAudio("chapter.opus", null))
        assertTrue(FolderBookRules.isAudio("chapter.aac", null))
        assertFalse(FolderBookRules.isAudio("README", null))
    }

    @Test
    fun `isImage accepts gif extension and rejects hidden image despite mime type`() {
        assertTrue(FolderBookRules.isImage("animated.GIF", null))
        assertFalse(FolderBookRules.isImage(".hidden-cover.png", "image/png"))
    }

    @Test
    fun `naturalOrder handles leading zero digit runs and prefix lengths`() {
        val paths = listOf("Chapter 0010.mp3", "Chapter 2.mp3", "Chapter 0002.mp3", "Chapter 1.mp3")
        assertEquals(
            listOf("Chapter 1.mp3", "Chapter 2.mp3", "Chapter 0002.mp3", "Chapter 0010.mp3"),
            paths.sortedWith(FolderBookRules.naturalOrder),
        )
    }

    @Test
    fun `titleFromFileName keeps leading dot and names ending with dot unchanged`() {
        assertEquals(".nomedia", FolderBookRules.titleFromFileName(".nomedia"))
        assertEquals("trailing", FolderBookRules.titleFromFileName("trailing."))
    }

}
