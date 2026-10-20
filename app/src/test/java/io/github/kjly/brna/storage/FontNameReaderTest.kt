package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * The family name read out of a font file (see [FontNameReader]). The fonts here are made
 * byte by byte — an sfnt with nothing but a `name` table — so what each test says about
 * the layout is in the test, and no font file has to be kept in the repository.
 */
class FontNameReaderTest {

    /** One record of a `name` table. */
    private class Name(
        val platform: Int,
        val language: Int,
        val id: Int,
        val text: String,
        val charset: Charset = Charsets.UTF_16BE
    )

    private fun windows(id: Int, text: String, language: Int = 0x0409) = Name(3, language, id, text)

    private fun nameTable(names: List<Name>): ByteArray {
        val strings = ByteArrayOutputStream()
        val records = ByteBuffer.allocate(names.size * 12)
        for (n in names) {
            val bytes = n.text.toByteArray(n.charset)
            records.putShort(n.platform.toShort()).putShort(0)
                .putShort(n.language.toShort()).putShort(n.id.toShort())
                .putShort(bytes.size.toShort()).putShort(strings.size().toShort())
            strings.write(bytes)
        }
        val head = ByteBuffer.allocate(6)
            .putShort(0).putShort(names.size.toShort()).putShort((6 + names.size * 12).toShort())
        return head.array() + records.array() + strings.toByteArray()
    }

    /**
     * An sfnt with a `name` table only, meant to sit at [at] in its file: a font file has it
     * at 0, a font in a collection somewhere after the collection's header. The offsets in
     * its table directory are file offsets, as they are in a real font.
     */
    private fun font(names: List<Name>, at: Int = 0): ByteArray {
        val table = nameTable(names)
        val directory = ByteBuffer.allocate(12 + 16)
            .putInt(0x00010000).putShort(1).putShort(16).putShort(0).putShort(0) // sfnt header, 1 table
            .put("name".toByteArray(Charsets.US_ASCII)).putInt(0).putInt(at + 28).putInt(table.size)
        return directory.array() + table
    }

    /** A collection of [fonts], each named by its records: the header, then the fonts one after another. */
    private fun collection(vararg fonts: List<Name>): ByteArray {
        val headerSize = 12 + 4 * fonts.size
        val offsets = ArrayList<Int>()
        val body = ByteArrayOutputStream()
        for (names in fonts) {
            offsets += headerSize + body.size()
            body.write(font(names, at = headerSize + body.size()))
        }
        val header = ByteBuffer.allocate(headerSize)
            .put("ttcf".toByteArray(Charsets.US_ASCII)).putShort(1).putShort(0).putInt(fonts.size)
        offsets.forEach { header.putInt(it) }
        return header.array() + body.toByteArray()
    }

    @Test
    fun `a font gives the family it is named by`() {
        assertEquals("Example Sans", FontNameReader.familyName(font(listOf(windows(1, "Example Sans")))))
    }

    @Test
    fun `the typographic family wins over the plain one`() {
        // "Example Condensed Light" is only the family of one weight of "Example Condensed".
        val names = listOf(windows(1, "Example Condensed Light"), windows(16, "Example Condensed"))
        assertEquals("Example Condensed", FontNameReader.familyName(font(names)))
    }

    @Test
    fun `an English Windows name wins over another language`() {
        val names = listOf(windows(1, "Beispielschrift", language = 0x0407), windows(1, "Example"))
        assertEquals("Example", FontNameReader.familyName(font(names)))
    }

    @Test
    fun `a font with only a Macintosh name is read too`() {
        val names = listOf(Name(1, 0, 1, "Example Mac", Charsets.ISO_8859_1))
        assertEquals("Example Mac", FontNameReader.familyName(font(names)))
    }

    @Test
    fun `letters beyond ASCII are kept`() {
        assertEquals("Bäume Größe", FontNameReader.familyName(font(listOf(windows(1, "Bäume Größe")))))
    }

    @Test
    fun `the first font of a collection is the one read`() {
        // Its table offsets are file offsets, so reading them from the wrong place — or as
        // if the font began at 0 — finds nothing.
        val ttc = collection(listOf(windows(1, "First")), listOf(windows(1, "Second")))
        assertEquals("First", FontNameReader.familyName(ttc))
    }

    @Test
    fun `a font with no family name gives none`() {
        // Only a subfamily ("Regular", nameID 2).
        assertNull(FontNameReader.familyName(font(listOf(windows(2, "Regular")))))
    }

    @Test
    fun `what is not a font gives none, and never an error`() {
        assertNull(FontNameReader.familyName(ByteArray(0)))
        assertNull(FontNameReader.familyName("not a font at all, just some text".toByteArray()))
        val good = font(listOf(windows(1, "Example Sans")))
        // Cut short at every length: the reader may find nothing, but not fail.
        for (length in 0 until good.size) FontNameReader.familyName(good.copyOf(length))
        assertNull(FontNameReader.familyName(good.copyOf(20)))
    }

    @Test
    fun `a directory that claims more tables than a font has is refused`() {
        val bytes = font(listOf(windows(1, "Example Sans")))
        ByteBuffer.wrap(bytes).putShort(4, 60000.toShort())
        assertNull(FontNameReader.familyName(bytes))
    }

    @Test
    fun `a collection with no fonts, or with its first font out of the file, gives none`() {
        val none = collection(listOf(windows(1, "First")))
        ByteBuffer.wrap(none).putInt(8, 0)
        assertNull(FontNameReader.familyName(none))
        val lost = collection(listOf(windows(1, "First")))
        ByteBuffer.wrap(lost).putInt(12, -1) // 4 GB in
        assertNull(FontNameReader.familyName(lost))
    }
}
