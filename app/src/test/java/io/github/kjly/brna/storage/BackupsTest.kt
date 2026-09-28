package io.github.kjly.brna.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The copies kept of a note's file before this app writes over it (see [Backups]). */
class BackupsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_790_000_000_000L

    private fun keep(dir: File, text: String, at: Long = now, skipHash: String? = null) =
        Backups.keep(dir, at, skipHash) { text.toByteArray().inputStream() }

    @Test
    fun `a version holds the file's bytes, under their fingerprint`() {
        val dir = tmp.newFolder()
        val version = keep(dir, "Toni's version")
        assertNotNull(version)
        assertArrayEquals("Toni's version".toByteArray(), version!!.file.readBytes())
        assertEquals(ContentHash.of("Toni's version".toByteArray()), version.hash)
        assertEquals(now, version.takenAt)
        assertEquals(version.file.length(), version.size)
        assertEquals(listOf(version), Backups.list(dir, now))
    }

    @Test
    fun `nothing is kept of an empty file, of what this app wrote there, or of the newest version again`() {
        val dir = tmp.newFolder()
        assertNull(keep(dir, ""))
        assertNull(keep(dir, "ours", skipHash = ContentHash.of("ours".toByteArray())))
        assertNotNull(keep(dir, "theirs"))
        assertNull(keep(dir, "theirs", at = now + 1))
        assertEquals(1, Backups.list(dir, now + 1).size)
        // Nothing is left behind of the ones not kept.
        assertFalse(File(dir, "incoming.tmp").exists())
    }

    @Test
    fun `a version made again after another is kept again`() {
        val dir = tmp.newFolder()
        keep(dir, "A", at = now)
        keep(dir, "B", at = now + 1)
        assertNotNull(keep(dir, "A", at = now + 2))
        assertEquals(3, Backups.list(dir, now + 2).size)
    }

    @Test
    fun `the newest five of a file are kept, each for a week`() {
        val dir = tmp.newFolder()
        for (i in 0 until 7) keep(dir, "version $i", at = now + i)
        val versions = Backups.list(dir, now + 6)
        assertEquals(Backups.KEEP_PER_FILE, versions.size)
        assertEquals((6 downTo 2).map { now + it }, versions.map { it.takenAt })
        // The older ones are gone from the folder too, not just from the list.
        assertEquals(Backups.KEEP_PER_FILE, dir.listFiles()!!.count { it.name.endsWith(".bak") })

        assertEquals(listOf(now + 6), Backups.list(dir, now + 6 + Backups.KEEP_MS).map { it.takenAt })
        assertTrue(Backups.list(dir, now + 7 + Backups.KEEP_MS).isEmpty())
    }

    @Test
    fun `which versions stay`() {
        val times = listOf(now - 8 * day, now - 6 * day, now - 5, now - 4, now - 3, now - 2, now - 1)
        assertEquals(listOf(now - 1, now - 2, now - 3, now - 4, now - 5), Backups.kept(times, now))
        assertEquals(listOf(now - 6 * day), Backups.kept(listOf(now - 8 * day, now - 6 * day), now))
        assertEquals(emptyList<Long>(), Backups.kept(emptyList(), now))
    }

    @Test
    fun `two versions in the same millisecond keep apart`() {
        val dir = tmp.newFolder()
        val first = keep(dir, "first")!!
        val second = keep(dir, "second")!!
        assertNotEquals(first.file, second.file)
        assertEquals(listOf(second, first), Backups.list(dir, now + 1))
    }

    @Test
    fun `only versions are listed, newest first`() {
        val dir = tmp.newFolder()
        keep(dir, "older", at = now - 10)
        keep(dir, "newer", at = now)
        File(dir, "written").writeText("abc")
        File(dir, "incoming.tmp").writeText("half")
        File(dir, "notes.bak").writeText("not one of ours")
        File(dir, "Mathe.rnote").writeText("nor this")
        assertEquals(listOf(now, now - 10), Backups.list(dir, now).map { it.takenAt })
        assertTrue(Backups.list(File(dir, "missing"), now).isEmpty())
    }

    @Test
    fun `a file that can't be read makes no version`() {
        val dir = tmp.newFolder()
        assertNull(Backups.keep(dir, now, null) { null })
        assertTrue(Backups.list(dir, now).isEmpty())
    }

    @Test
    fun `a file holding what this app wrote there last isn't read again`() {
        // Opened, or changed on the laptop since: it holds someone else's version.
        assertTrue(Backups.mayDiffer(knownHash = "theirs", written = "ours", changedSince = false))
        assertTrue(Backups.mayDiffer(knownHash = "theirs", written = null, changedSince = false))
        assertTrue(Backups.mayDiffer(knownHash = null, written = "ours", changedSince = false))
        // Saved here last, and nobody wrote it since.
        assertFalse(Backups.mayDiffer(knownHash = "ours", written = "ours", changedSince = false))
        // Written elsewhere since, and about to be overwritten anyway.
        assertTrue(Backups.mayDiffer(knownHash = "ours", written = "ours", changedSince = true))
    }

    @Test
    fun `each file has a folder of its own`() {
        val drive = "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3Dencoded%3DAbC"
        val key = Backups.keyOf(drive)
        assertEquals(32, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(key, Backups.keyOf(drive))
        assertNotEquals(key, Backups.keyOf("$drive-2"))
    }
}
