package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of [CustomFonts] that are only text: the saved list of fonts, and the copy's name. */
class CustomFontsTest {

    @Test
    fun `the list of fonts comes back as it was saved`() {
        val fonts = listOf(
            CustomFonts.Entry("Cantarell", "font_1.ttf", "Cantarell-Regular.ttf"),
            CustomFonts.Entry("Bäume Größe", "font_2.otf", "Bäume.otf")
        )
        assertEquals(fonts, CustomFonts.decode(CustomFonts.encode(fonts)))
    }

    @Test
    fun `nothing saved is no fonts`() {
        assertTrue(CustomFonts.decode(null).isEmpty())
        assertTrue(CustomFonts.decode("").isEmpty())
        assertTrue(CustomFonts.decode(CustomFonts.encode(emptyList())).isEmpty())
    }

    @Test
    fun `a tab or a line break in a name can't break the list`() {
        val saved = CustomFonts.encode(listOf(CustomFonts.Entry("Two\tPart\nName", "font_1.ttf", "a\tb.ttf")))
        val back = CustomFonts.decode(saved)
        assertEquals(1, back.size)
        assertEquals("Two Part Name", back[0].family)
        assertEquals("font_1.ttf", back[0].fileName)
        assertEquals("a b.ttf", back[0].originalName)
    }

    @Test
    fun `a line without a file name is left out, one without an original name takes the file's`() {
        val back = CustomFonts.decode("Broken\n\nFine\tfont_7.ttf")
        assertEquals(listOf(CustomFonts.Entry("Fine", "font_7.ttf", "font_7.ttf")), back)
    }

    @Test
    fun `the copy keeps a plain extension and nothing else from the file name`() {
        assertEquals(".ttf", CustomFonts.extensionOf("Cantarell-Regular.ttf"))
        assertEquals(".otf", CustomFonts.extensionOf("A.B.otf"))
        assertEquals(".TTC", CustomFonts.extensionOf("Collection.TTC"))
        assertEquals("", CustomFonts.extensionOf("NoExtension"))
        assertEquals("", CustomFonts.extensionOf("trailing."))
        // A name that tries to steer the copy somewhere else gets no extension at all.
        assertEquals("", CustomFonts.extensionOf("x.y/../../z"))
        assertEquals("", CustomFonts.extensionOf("x.ttf/../y"))
        assertEquals("", CustomFonts.extensionOf("font.veryLongExtension"))
    }
}
