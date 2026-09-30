package io.github.kjly.brna.storage

import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.TextFormatting
import io.github.kjly.brna.model.TextToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Typewriter's font size, font family and wrap width: what the size buttons, the font
 * menu and the box's handles write, and what they read back. Sizes and families on a
 * selection are ranged attributes, on the whole box the box's own style, as in the file.
 */
class TextFontTest {
    private val black = RnoteNativeColor(0f, 0f, 0f, 1f)

    private fun text(s: String = "Hello world", maxWidth: Float? = 600f) =
        NativeEditing.createText(s, 10f, 20f, 32f, black, maxWidth)!!

    private fun style(el: io.github.kjly.brna.model.NativeTextElement) =
        el.raw!!.asJsonObject.getAsJsonObject("text_style")

    @Test fun `a new box takes the family it is given`() {
        val el = NativeEditing.createText("Hi", 0f, 0f, 32f, black, 600f, "start", "Cantarell")!!
        assertEquals("Cantarell", el.fontFamily)
        assertEquals("serif", text().fontFamily)
    }

    @Test fun `max width is set, cleared and keeps everything else`() {
        val el = text()
        val narrow = NativeEditing.withMaxWidth(el, 200.12345f)
        assertEquals(200.123f, narrow.maxWidth!!, 1e-3f)
        assertEquals(el.text, narrow.text)
        assertEquals(el.minX, narrow.minX, 0f)
        assertEquals(el.minY, narrow.minY, 0f)
        assertEquals(200.123, style(narrow).get("max_width").asDouble, 1e-6)
        val free = NativeEditing.withMaxWidth(narrow, null)
        assertNull(free.maxWidth)
        assertTrue(style(free).get("max_width").isJsonNull)
    }

    @Test fun `a size on a selection is a ranged attribute and only there`() {
        val big = NativeEditing.withFontSize(text(), 2, 5, 48f)
        assertEquals(48f, TextFormatting.sizeAt(big, 2, 5)!!, 0f)
        assertNull(TextFormatting.sizeAt(big, 0, 11)) // mixed
        assertEquals(32f, TextFormatting.sizeAt(big, 0, 2)!!, 0f)
        assertEquals(32f, TextFormatting.sizeAt(big, 5, 11)!!, 0f)
        val attrs = style(big).getAsJsonArray("ranged_text_attributes")
        assertEquals(1, attrs.size())
        val a = attrs[0].asJsonObject
        assertEquals(2, a.getAsJsonObject("range").get("start").asInt)
        assertEquals(5, a.getAsJsonObject("range").get("end").asInt)
        assertEquals(48.0, a.getAsJsonObject("attribute").get("font_size").asDouble, 0.0)
        assertEquals(32.0, style(big).get("font_size").asDouble, 0.0)
    }

    @Test fun `a second size replaces the first where they overlap and bold survives`() {
        val el = NativeEditing.toggleFormat(text(), 0, 5, TextToggle.BOLD)
        val a = NativeEditing.withFontSize(el, 0, 5, 48f)
        val b = NativeEditing.withFontSize(a, 3, 8, 20f)
        assertEquals(48f, TextFormatting.sizeAt(b, 0, 3)!!, 0f)
        assertEquals(20f, TextFormatting.sizeAt(b, 3, 8)!!, 0f)
        assertTrue(TextFormatting.togglesAt(b, 0, 3).contains(TextToggle.BOLD))
        assertTrue(TextFormatting.togglesAt(b, 3, 5).contains(TextToggle.BOLD))
    }

    @Test fun `a family on a selection`() {
        val f = NativeEditing.withFontFamily(text(), 6, 11, "Cantarell")
        assertEquals("Cantarell", TextFormatting.familyAt(f, 6, 11))
        assertNull(TextFormatting.familyAt(f, 0, 11))
        assertEquals("serif", TextFormatting.familyAt(f, 0, 6))
    }

    @Test fun `a size and a family do not touch each other`() {
        val el = NativeEditing.withFontFamily(NativeEditing.withFontSize(text(), 0, 5, 48f), 0, 5, "Cantarell")
        assertEquals(48f, TextFormatting.sizeAt(el, 0, 5)!!, 0f)
        assertEquals("Cantarell", TextFormatting.familyAt(el, 0, 5))
        assertEquals(2, style(el).getAsJsonArray("ranged_text_attributes").size())
    }

    @Test fun `character offsets past ASCII land on the right chars`() {
        val big = NativeEditing.withFontSize(text("Äpfel süß"), 6, 9, 50f) // "süß"
        assertEquals(50f, TextFormatting.sizeAt(big, 6, 9)!!, 0f)
        assertEquals(32f, TextFormatting.sizeAt(big, 0, 5)!!, 0f)
    }

    @Test fun `an empty selection changes nothing`() {
        val same = NativeEditing.withFontSize(text(), 3, 3, 90f)
        assertEquals(32f, TextFormatting.sizeAt(same, 0, 11)!!, 0f)
        assertEquals(0, style(same).getAsJsonArray("ranged_text_attributes").size())
    }

    @Test fun `typing inside a sized range keeps its size`() {
        val el = NativeEditing.withFontSize(text(), 0, 5, 48f)
        val typed = NativeEditing.withText(el, "HelXlo world")
        assertNotNull(typed)
        assertEquals(48f, TextFormatting.sizeAt(typed, 0, 6)!!, 0f)
    }

    @Test fun `a size on the whole box is the box's own and replaces the ranged ones`() {
        val mixed = NativeEditing.withFontSize(NativeEditing.toggleFormat(text(), 0, 5, TextToggle.BOLD), 2, 8, 20f)
        val all = NativeEditing.withBoxFontSize(mixed, 40f)
        assertEquals(40f, all.fontSize, 0f)
        assertEquals(40.0, style(all).get("font_size").asDouble, 0.0)
        assertEquals(40f, TextFormatting.sizeAt(all, 0, 11)!!, 0f)
        // Only the sizes went; the bold range is still there.
        val attrs = style(all).getAsJsonArray("ranged_text_attributes")
        assertEquals(1, attrs.size())
        assertTrue(attrs[0].asJsonObject.getAsJsonObject("attribute").has("font_weight"))
        assertTrue(TextFormatting.togglesAt(all, 0, 5).contains(TextToggle.BOLD))
    }

    @Test fun `text typed at the end of a resized box has the box's size`() {
        val all = NativeEditing.withBoxFontSize(text(), 48f)
        val typed = NativeEditing.withText(all, "Hello world!")!!
        assertEquals(48f, TextFormatting.sizeAt(typed, 11, 12)!!, 0f)
        assertEquals(48f, TextFormatting.sizeAt(typed, 0, 12)!!, 0f)
    }

    @Test fun `a family on the whole box is the box's own and replaces the ranged ones`() {
        val mixed = NativeEditing.withFontFamily(text(), 0, 5, "Cantarell")
        val all = NativeEditing.withBoxFontFamily(mixed, "monospace")
        assertEquals("monospace", all.fontFamily)
        assertEquals("monospace", style(all).get("font_family").asString)
        assertEquals("monospace", TextFormatting.familyAt(all, 0, 11))
        assertEquals(0, style(all).getAsJsonArray("ranged_text_attributes").size())
    }
}
