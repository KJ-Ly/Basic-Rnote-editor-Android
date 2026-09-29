package io.github.kjly.brna.storage

import com.google.gson.JsonObject
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.RangedTextAttr
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.TextAttr
import io.github.kjly.brna.model.TextFormatting
import io.github.kjly.brna.model.TextToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFormattingTest {

    private val black = RnoteNativeColor(0f, 0f, 0f, 1f)

    private fun text(s: String) = NativeEditing.createText(s, 0f, 0f, 32f, black, null)!!

    /** The element's ranged attributes as its JSON holds them: (start, end, key=value). */
    private fun jsonRanges(el: NativeTextElement): List<Triple<Int, Int, String>> =
        el.raw!!.asJsonObject.getAsJsonObject("text_style").getAsJsonArray("ranged_text_attributes").map {
            val o = it.asJsonObject
            val r = o.getAsJsonObject("range")
            val (key, value) = o.getAsJsonObject("attribute").entrySet().single()
            Triple(r.get("start").asInt, r.get("end").asInt, "$key=$value")
        }

    @Test
    fun `offsets convert between UTF-8 bytes and chars, umlauts and emoji included`() {
        val s = "Grüße 🙂!"
        // G r ü(2 bytes) ß(2) e space: 8 bytes in 6 chars; then 🙂 (4 bytes, 2 chars) and !.
        assertEquals(2, TextFormatting.byteIndex(s, 2))
        assertEquals(4, TextFormatting.byteIndex(s, 3))
        assertEquals(8, TextFormatting.byteIndex(s, 6))
        assertEquals(12, TextFormatting.byteIndex(s, 8))
        // Never halfway into the emoji.
        assertEquals(8, TextFormatting.byteIndex(s, 7))
        assertEquals(3, TextFormatting.charIndex(s, 4))
        assertEquals(6, TextFormatting.charIndex(s, 8))
        assertEquals(6, TextFormatting.charIndex(s, 10))
        assertEquals(8, TextFormatting.charIndex(s, 12))
        assertEquals(s.length, TextFormatting.charIndex(s, 99))
    }

    @Test
    fun `desktop Rnote's ranges are read and laid over the box's style`() {
        val json = """{"textstroke":{"text":"Hallo Welt","transform":{"affine":[1,0,0,0,1,0,0,0,1]},""" +
            """"text_style":{"font_family":"serif","font_size":32.0,"font_weight":500,"font_style":"regular",""" +
            """"color":{"r":0,"g":0,"b":0,"a":1},"max_width":null,"alignment":"start","ranged_text_attributes":[""" +
            """{"range":{"start":0,"end":5},"attribute":{"font_weight":700}},""" +
            """{"range":{"start":3,"end":10},"attribute":{"underline":true}},""" +
            """{"range":{"start":6,"end":10},"attribute":{"some_future_thing":1}}]}}}"""
        val el = RnoteNativeParser.parseElementJson(json) as NativeTextElement
        assertEquals(
            listOf(
                RangedTextAttr(0, 5, TextAttr.Weight(700)),
                RangedTextAttr(3, 10, TextAttr.Underline(true))
            ),
            el.ranges
        )
        val runs = TextFormatting.runs(el)
        assertEquals(listOf(0 to 3, 3 to 5, 5 to 10), runs.map { it.start to it.end })
        assertEquals(listOf(700, 700, 500), runs.map { it.weight })
        assertEquals(listOf(false, true, true), runs.map { it.underline })
    }

    @Test
    fun `a later range of the same kind wins where they overlap`() {
        val el = text("abcdef").copy(
            ranges = listOf(RangedTextAttr(0, 6, TextAttr.Italic(true)), RangedTextAttr(2, 4, TextAttr.Italic(false)))
        )
        assertEquals(listOf(true, false, true), TextFormatting.runs(el).map { it.italic })
    }

    @Test
    fun `bold goes on over the selection, in bytes, and comes off again`() {
        val el = text("Grüße Welt")
        val bold = NativeEditing.toggleFormat(el, 0, 5, TextToggle.BOLD)
        assertEquals(listOf(Triple(0, 7, "font_weight=700")), jsonRanges(bold))
        assertEquals(TextFormatting.BOLD_WEIGHT, TextFormatting.runs(bold).first().weight)
        val plain = NativeEditing.toggleFormat(bold, 0, 5, TextToggle.BOLD)
        assertTrue(jsonRanges(plain).isEmpty())
    }

    @Test
    fun `toggling part of a bold stretch cuts it, as Rnote does`() {
        val bold = NativeEditing.toggleFormat(text("abcdefgh"), 0, 8, TextToggle.BOLD)
        // Rnote takes bold off where the selection meets it, and keeps it on either side.
        val cut = NativeEditing.toggleFormat(bold, 3, 5, TextToggle.BOLD)
        assertEquals(
            listOf(Triple(0, 3, "font_weight=700"), Triple(5, 8, "font_weight=700")),
            jsonRanges(cut).sortedBy { it.first }
        )
        // Across a bold and a plain stretch, the bold one meeting it decides: it comes off.
        val across = NativeEditing.toggleFormat(cut, 2, 4, TextToggle.BOLD)
        assertEquals(
            listOf(Triple(0, 2, "font_weight=700"), Triple(5, 8, "font_weight=700")),
            jsonRanges(across).sortedBy { it.first }
        )
    }

    @Test
    fun `italic, underline and strikethrough flip the smallest range they meet`() {
        val italic = NativeEditing.toggleFormat(text("abcdef"), 0, 6, TextToggle.ITALIC)
        assertEquals(listOf(Triple(0, 6, "font_style=\"italic\"")), jsonRanges(italic))
        val regular = NativeEditing.toggleFormat(italic, 2, 4, TextToggle.ITALIC)
        assertEquals(
            listOf(Triple(0, 2, "font_style=\"italic\""), Triple(2, 4, "font_style=\"regular\""), Triple(4, 6, "font_style=\"italic\"")),
            jsonRanges(regular).sortedBy { it.first }
        )
        val underlined = NativeEditing.toggleFormat(text("abc"), 0, 3, TextToggle.UNDERLINE)
        assertEquals(listOf(Triple(0, 3, "underline=false")), jsonRanges(NativeEditing.toggleFormat(underlined, 0, 3, TextToggle.UNDERLINE)))
    }

    @Test
    fun `an empty selection changes nothing`() {
        val el = text("abc")
        assertTrue(NativeEditing.toggleFormat(el, 1, 1, TextToggle.BOLD) === el)
    }

    @Test
    fun `setting a switch for typed text is idempotent and keeps the ranges tidy`() {
        var el = text("a")
        // Typing with bold switched on: a character at a time.
        el = NativeEditing.setFormat(el, 0, 1, TextToggle.BOLD, true)
        el = NativeEditing.withText(el, "ab")!!
        el = NativeEditing.setFormat(el, 1, 2, TextToggle.BOLD, true)
        el = NativeEditing.withText(el, "abc")!!
        el = NativeEditing.setFormat(el, 2, 3, TextToggle.BOLD, true)
        assertEquals(listOf(Triple(0, 3, "font_weight=700")), jsonRanges(el))
        // Autocorrect replacing the word sets it again rather than flipping it off.
        el = NativeEditing.setFormat(el, 0, 3, TextToggle.BOLD, true)
        assertEquals(listOf(Triple(0, 3, "font_weight=700")), jsonRanges(el))
        // Switched off, the stretch loses it and the rest keeps it.
        el = NativeEditing.setFormat(el, 1, 2, TextToggle.BOLD, false)
        assertEquals(listOf(Triple(0, 1, "font_weight=700"), Triple(2, 3, "font_weight=700")), jsonRanges(el).sortedBy { it.first })
    }

    @Test
    fun `other attributes are left where they were`() {
        val el = text("abcdef")
        val obj = el.raw!!.deepCopy().asJsonObject
        obj.getAsJsonObject("text_style").getAsJsonArray("ranged_text_attributes").add(
            com.google.gson.JsonParser.parseString("""{"range":{"start":1,"end":3},"attribute":{"text_color":{"r":1,"g":0,"b":0,"a":1}}}""")
        )
        val colored = RnoteNativeParser.parseElementTree(JsonObject().apply { add("textstroke", obj) }) as NativeTextElement
        val bold = NativeEditing.toggleFormat(colored, 0, 6, TextToggle.BOLD)
        assertTrue(jsonRanges(bold).any { it.first == 1 && it.second == 3 && it.third.startsWith("text_color") })
        assertEquals(RnoteNativeColor(1f, 0f, 0f, 1f), TextFormatting.runs(bold)[1].color)
    }

    @Test
    fun `the switches show what the selection has, and for a bare cursor the char after it`() {
        val el = NativeEditing.toggleFormat(text("abcdef"), 2, 4, TextToggle.BOLD)
        assertEquals(setOf(TextToggle.BOLD), TextFormatting.togglesAt(el, 2, 4))
        assertEquals(emptySet<TextToggle>(), TextFormatting.togglesAt(el, 1, 4))
        // Typed at the start of the bold word, text comes out bold; at its end, it doesn't.
        assertEquals(setOf(TextToggle.BOLD), TextFormatting.togglesAt(el, 2, 2))
        assertFalse(TextToggle.BOLD in TextFormatting.togglesAt(el, 4, 4))
        assertEquals(emptySet<TextToggle>(), TextFormatting.togglesAt(el, 6, 6))
    }

    @Test
    fun `an edit is the part between the common start and end`() {
        assertEquals(2 to 3, TextFormatting.changedRange("ab", "abc"))
        assertEquals(1 to 3, TextFormatting.changedRange("teh", "the"))
        assertEquals(1 to 1, TextFormatting.changedRange("abc", "ac"))
    }
}
