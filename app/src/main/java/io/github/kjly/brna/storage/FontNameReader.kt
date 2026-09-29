package io.github.kjly.brna.storage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Reads a font file's own family name out of its `name` table, so a font picked in
 * [CustomFonts]' font manager doesn't have to have its family retyped by hand — the
 * file already says what it is. Understands TrueType/OpenType (`.ttf`/`.otf`) and font
 * collections (`.ttc`, using the first font in it). Anything this can't parse — an
 * unsupported container, a truncated or malformed table, a font with no usable Windows
 * or Macintosh record — comes back null, and the caller falls back to the picked
 * file's own name, same as before this existed.
 */
object FontNameReader {

    private const val WINDOWS_PLATFORM = 3
    private const val UNICODE_PLATFORM = 0
    private const val MACINTOSH_PLATFORM = 1
    private const val US_ENGLISH_LANGUAGE = 0x0409
    private const val MAX_TABLES = 512

    /** The Typographic Family Name (nameID 16) if the font has one, else the plain Family Name (1). */
    fun familyName(bytes: ByteArray): String? = try {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val directoryOffset = if (bytes.size >= 16 && tag(buf, 0) == "ttcf") {
            // Font collection: tag(4) majorVersion(2) minorVersion(2) numFonts(4) then the
            // offset of each font's sfnt table directory, so the first font's is at byte 12.
            // The table offsets inside it are absolute file offsets already.
            if (u32(buf, 8) < 1) return null
            u32(buf, 12)
        } else 0L
        val tables = tableDirectory(buf, directoryOffset) ?: return null
        val nameTableOffset = tables["name"] ?: return null
        familyFromNameTable(buf, nameTableOffset)
    } catch (e: Exception) {
        null
    }

    private fun tag(buf: ByteBuffer, offset: Int): String {
        val bytes = ByteArray(4)
        for (i in 0 until 4) bytes[i] = buf.get(offset + i)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun u16(buf: ByteBuffer, offset: Int): Int = buf.getShort(offset).toInt() and 0xFFFF
    private fun u32(buf: ByteBuffer, offset: Int): Long = buf.getInt(offset).toLong() and 0xFFFFFFFFL

    private fun tableDirectory(buf: ByteBuffer, directoryOffset: Long): Map<String, Long>? {
        // Compared as a Long: an offset past 2 GB must not wrap round into the file.
        if (directoryOffset < 0 || directoryOffset + 12 > buf.capacity()) return null
        val base = directoryOffset.toInt()
        val numTables = u16(buf, base + 4)
        if (numTables !in 1..MAX_TABLES) return null
        val recordsEnd = base + 12 + numTables * 16
        if (recordsEnd > buf.capacity()) return null
        val tables = HashMap<String, Long>(numTables)
        for (i in 0 until numTables) {
            val entry = base + 12 + i * 16
            tables[tag(buf, entry)] = u32(buf, entry + 8)
        }
        return tables
    }

    private data class NameRecord(
        val platformId: Int,
        val languageId: Int,
        val nameId: Int,
        val length: Int,
        val offset: Int
    )

    private fun familyFromNameTable(buf: ByteBuffer, tableOffset: Long): String? {
        if (tableOffset < 0 || tableOffset + 6 > buf.capacity()) return null
        val base = tableOffset.toInt()
        val count = u16(buf, base + 2)
        val stringAreaOffset = base + u16(buf, base + 4)
        val recordsEnd = base + 6 + count * 12
        if (count <= 0 || recordsEnd > buf.capacity()) return null

        val records = (0 until count).map { i ->
            val r = base + 6 + i * 12
            NameRecord(
                platformId = u16(buf, r),
                languageId = u16(buf, r + 4),
                nameId = u16(buf, r + 6),
                length = u16(buf, r + 8),
                offset = u16(buf, r + 10)
            )
        }

        fun decode(rec: NameRecord): String? {
            val start = stringAreaOffset + rec.offset
            if (rec.length <= 0 || start < 0 || start + rec.length > buf.capacity()) return null
            val bytes = ByteArray(rec.length)
            for (i in 0 until rec.length) bytes[i] = buf.get(start + i)
            // Windows/Unicode records are UTF-16BE; the old Macintosh Roman records
            // used here are ASCII for any name likely to appear, so ISO-8859-1 is a
            // fine approximation without pulling in a Mac Roman charset table.
            val charset = if (rec.platformId == MACINTOSH_PLATFORM) Charsets.ISO_8859_1 else Charset.forName("UTF-16BE")
            return String(bytes, charset).trim().takeIf { it.isNotEmpty() }
        }

        // Typographic Family (16) is the right family for a font with several weights or
        // widths, where the plain Family Name (1) sometimes bakes a variant into it (e.g.
        // "Example Condensed Light" vs the typographic "Example Condensed"). Within
        // either, prefer an English Windows record — what most desktop font pickers show.
        fun best(nameId: Int): String? {
            val candidates = records.filter { it.nameId == nameId }
            val preferred = candidates.firstOrNull { it.platformId == WINDOWS_PLATFORM && it.languageId == US_ENGLISH_LANGUAGE }
                ?: candidates.firstOrNull { it.platformId == WINDOWS_PLATFORM }
                ?: candidates.firstOrNull { it.platformId == UNICODE_PLATFORM }
                ?: candidates.firstOrNull { it.platformId == MACINTOSH_PLATFORM }
                ?: candidates.firstOrNull()
            return preferred?.let { decode(it) }
        }

        return best(16) ?: best(1)
    }
}
