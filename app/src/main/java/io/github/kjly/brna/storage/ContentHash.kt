package io.github.kjly.brna.storage

import java.io.InputStream
import java.security.MessageDigest

/**
 * A fingerprint of a note file's bytes (SHA-256), to tell a file that was really changed
 * elsewhere from one whose modification time merely moved. Drive, for one, can touch a
 * file's time after uploading it, with not a byte different from what this app wrote.
 */
object ContentHash {

    fun newDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    /** [digest]'s result as text, which is all a fingerprint needs to be compared as. */
    fun hex(digest: MessageDigest): String = digest.digest().joinToString("") { "%02x".format(it) }

    fun of(bytes: ByteArray): String = hex(newDigest().apply { update(bytes) })

    /** The fingerprint of everything [input] still has to give; reads it to the end. Blocking. */
    fun of(input: InputStream): String {
        val digest = newDigest()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return hex(digest)
    }

    /** Reads what is left of [input], so a digest it feeds sees the whole file. Blocking. */
    fun drain(input: InputStream) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (input.read(buffer) >= 0) Unit
    }

    private const val BUFFER_BYTES = 64 * 1024
}
