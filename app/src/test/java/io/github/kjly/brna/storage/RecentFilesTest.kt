package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFilesTest {

    private fun entry(n: Int, at: Long = n.toLong()) = RecentFiles.Entry("content://notes/$n", "Note $n", at)

    @Test
    fun `the note opened last comes first`() {
        val list = RecentFiles.withEntry(listOf(entry(1), entry(2)), entry(3))
        assertEquals(listOf(3, 1, 2), list.map { it.title.removePrefix("Note ").toInt() })
    }

    @Test
    fun `opening a listed note again moves it up instead of listing it twice`() {
        val list = RecentFiles.withEntry(listOf(entry(1), entry(2), entry(3)), entry(3, at = 99))
        assertEquals(listOf("Note 3", "Note 1", "Note 2"), list.map { it.title })
        assertEquals(99L, list.first().openedAt)
    }

    @Test
    fun `the list keeps only the newest ten`() {
        var list = emptyList<RecentFiles.Entry>()
        for (i in 1..15) list = RecentFiles.withEntry(list, entry(i))
        assertEquals(RecentFiles.MAX_ENTRIES, list.size)
        assertEquals("Note 15", list.first().title)
        assertEquals("Note 6", list.last().title)
    }

    @Test
    fun `entries survive being written out and read back`() {
        val entries = listOf(
            RecentFiles.Entry("content://com.google.android.apps.docs/doc%3D1", "Mathe \"1\" – Ü", 1_700_000_000_000L),
            entry(2)
        )
        assertEquals(entries, RecentFiles.decode(RecentFiles.encode(entries)))
    }

    @Test
    fun `a damaged list reads as empty rather than failing`() {
        assertEquals(emptyList<RecentFiles.Entry>(), RecentFiles.decode("{not json"))
        assertEquals(emptyList<RecentFiles.Entry>(), RecentFiles.decode(null))
        assertEquals(1, RecentFiles.decode("""[{"uri":"content://x"},{"title":"no uri"}]""").size)
    }

    @Test
    fun `a note opened in a workspace stays listed under the folder's grant`() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3ASchule"
        val granted = setOf(tree, "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D7")
        assertTrue(RecentFiles.isGranted("$tree/document/primary%3ASchule%2FMathe.rnote", granted))
        assertTrue(RecentFiles.isGranted("content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D7", granted))
        // A folder whose name only starts the same is another folder.
        assertFalse(RecentFiles.isGranted("${tree}2/document/primary%3ASchule2%2FMathe.rnote", granted))
        assertFalse(RecentFiles.isGranted("content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D8", granted))
    }
}
