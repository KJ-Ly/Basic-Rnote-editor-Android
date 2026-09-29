package io.github.kjly.brna.storage

import io.github.kjly.brna.storage.FolderListing.Entry
import io.github.kjly.brna.storage.FolderListing.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderListingTest {

    @Test
    fun `notes PDFs pictures and folders are recognised, by type or by extension`() {
        // Drive reports a .rnote as a plain binary file: the extension decides.
        assertEquals(Kind.NOTE, FolderListing.kindOf("Mathe.rnote", "application/octet-stream"))
        assertEquals(Kind.NOTE, FolderListing.kindOf("Mathe.RNOTE", null))
        assertEquals(Kind.PDF, FolderListing.kindOf("Arbeitsblatt.PDF", null))
        assertEquals(Kind.PDF, FolderListing.kindOf("scan", "application/pdf"))
        assertEquals(Kind.IMAGE, FolderListing.kindOf("Tafel.jpg", "image/jpeg"))
        assertEquals(Kind.IMAGE, FolderListing.kindOf("Tafel.jpeg", null))
        assertEquals(Kind.IMAGE, FolderListing.kindOf("Skizze.png", null))
        assertEquals(Kind.FOLDER, FolderListing.kindOf("Physik", FolderListing.FOLDER_MIME))
        // A folder is a folder whatever it is called.
        assertEquals(Kind.FOLDER, FolderListing.kindOf("Alt.rnote", FolderListing.FOLDER_MIME))
        // Xournal++'s, which Rnote opens as a note.
        assertEquals(Kind.XOPP, FolderListing.kindOf("Tafelbild.xopp", null))
        assertEquals(Kind.XOPP, FolderListing.kindOf("Tafelbild", "application/x-xopp"))
    }

    @Test
    fun `hidden files and other types are left out`() {
        assertNull(FolderListing.kindOf(".Mathe.rnote", null))
        assertNull(FolderListing.kindOf(".trash", FolderListing.FOLDER_MIME))
        assertNull(FolderListing.kindOf("Notizen.txt", "text/plain"))
        assertNull(FolderListing.kindOf("Referat.docx", null))
    }

    @Test
    fun `folders come first, then notes, then PDFs and pictures`() {
        val entries = listOf(
            Entry("1", "Blatt.pdf", Kind.PDF, null),
            Entry("2", "Mathe.rnote", Kind.NOTE, null),
            Entry("3", "Anhang.png", Kind.IMAGE, null),
            Entry("4", "Physik", Kind.FOLDER, null),
            Entry("5", "Bio.rnote", Kind.NOTE, null),
            Entry("6", "Archiv", Kind.FOLDER, null)
        )
        assertEquals(
            listOf("Archiv", "Physik", "Bio.rnote", "Mathe.rnote", "Anhang.png", "Blatt.pdf"),
            FolderListing.sorted(entries).map { it.name }
        )
    }

    @Test
    fun `numbers sort the way people count, letters regardless of case`() {
        val names = listOf("Stunde 10.rnote", "Stunde 2.rnote", "stunde 1.rnote", "Anfang.rnote", "Stunde 02b.rnote")
        val sorted = names.sortedWith { a, b -> FolderListing.naturalCompare(a, b) }
        assertEquals(
            listOf("Anfang.rnote", "stunde 1.rnote", "Stunde 2.rnote", "Stunde 02b.rnote", "Stunde 10.rnote"),
            sorted
        )
        assertTrue(FolderListing.naturalCompare("Seite 9", "Seite 10") < 0)
        assertTrue(FolderListing.naturalCompare("Seite", "Seite 1") < 0)
    }

    @Test
    fun `natural order never calls two different names equal`() {
        // Otherwise a sort could put them in any order, and the list would jump around.
        assertTrue(FolderListing.naturalCompare("Blatt 01", "Blatt 1") != 0)
        assertTrue(FolderListing.naturalCompare("mathe", "Mathe") != 0)
        assertEquals(0, FolderListing.naturalCompare("Mathe", "Mathe"))
    }

    @Test
    fun `a copy is named as Rnote names it, with the next free number`() {
        assertEquals("Mathe - 1.rnote", FolderListing.duplicateName("Mathe.rnote", setOf("Mathe.rnote")))
        assertEquals(
            "Mathe - 2.rnote",
            FolderListing.duplicateName("Mathe.rnote", setOf("Mathe.rnote", "Mathe - 1.rnote"))
        )
        assertEquals("README - 1", FolderListing.duplicateName("README", setOf("README")))
    }

    @Test
    fun `a copy of a copy counts on from the original's name`() {
        assertEquals(
            "Mathe - 2.rnote",
            FolderListing.duplicateName("Mathe - 1.rnote", setOf("Mathe.rnote", "Mathe - 1.rnote"))
        )
    }

    @Test
    fun `renaming keeps the extension, typed or not`() {
        assertEquals("Physik.rnote", FolderListing.renamed("Mathe.rnote", "Physik"))
        assertEquals("Physik.rnote", FolderListing.renamed("Mathe.rnote", "Physik.rnote"))
        assertEquals("Bio 2.rnote", FolderListing.renamed("Mathe.rnote", "  Bio 2 "))
        assertEquals("Neu", FolderListing.renamed("Ordner", "Neu"))
    }
}
