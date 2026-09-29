package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.DigestInputStream

class ContentHashTest {

    private val bytes = ByteArray(200_000) { (it * 31 % 251).toByte() }

    @Test
    fun `the same bytes give the same fingerprint, read at once or streamed`() {
        assertEquals(ContentHash.of(bytes), ContentHash.of(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `one byte different is a different fingerprint`() {
        val other = bytes.copyOf().also { it[123_456] = (it[123_456] + 1).toByte() }
        assertNotEquals(ContentHash.of(bytes), ContentHash.of(other))
    }

    @Test
    fun `a file read only partly by a parser is still fingerprinted whole`() {
        // As loading does: a parser stops before the end, the rest is drained.
        val digest = ContentHash.newDigest()
        val input = DigestInputStream(ByteArrayInputStream(bytes), digest)
        input.read(ByteArray(1000))
        ContentHash.drain(input)
        assertEquals(ContentHash.of(bytes), ContentHash.hex(digest))
    }
}
