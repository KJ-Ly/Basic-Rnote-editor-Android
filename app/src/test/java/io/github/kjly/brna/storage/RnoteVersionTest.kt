package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Which files come from an Rnote newer than this app knows, and so are never saved over. */
class RnoteVersionTest {

    @Test
    fun `the versions this app knows the format of are not protected`() {
        for (known in listOf("0.5.9", "0.13.1", "0.14.2", "0.15.0", "0.15.3", "0.15.0-dev", "0.15.0+build.7")) {
            assertFalse(known, RnoteVersion.isNewerThanKnown(known))
        }
    }

    @Test
    fun `a newer version is, a pre-release of one too`() {
        for (newer in listOf("0.16.0", "0.16.0-dev", " 0.17.2 ", "1.0.0", "2.3")) {
            assertTrue(newer, RnoteVersion.isNewerThanKnown(newer))
        }
    }

    @Test
    fun `a file naming no version, or none that reads as one, is not`() {
        for (other in listOf(null, "", "rnote", "v0.16.0", "0", "0.x.1")) {
            assertFalse("$other", RnoteVersion.isNewerThanKnown(other))
        }
    }

    private fun gzipped(json: String): ByteArrayInputStream =
        ByteArrayInputStream(ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.toByteArray()) } }.toByteArray())

    @Test
    fun `the version a file names is read with it`() {
        val snapshot = """"data":{"engine_snapshot":{"document":{"x":0.0,"y":0.0,"width":800.0,"height":1100.0},""" +
            """"stroke_components":[{"value":null,"version":0}],"chrono_components":[{"value":null,"version":0}],"chrono_counter":0}}"""
        assertEquals("0.16.0", RnoteNativeParser.parse(gzipped("""{"version":"0.16.0",$snapshot}""")).fileVersion)
        // Wherever it stands among the keys.
        assertEquals("0.15.0", RnoteNativeParser.parse(gzipped("""{$snapshot,"version":"0.15.0"}""")).fileVersion)
        assertNull(RnoteNativeParser.parse(gzipped("""{$snapshot}""")).fileVersion)
    }
}
