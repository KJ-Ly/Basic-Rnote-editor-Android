package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomColorsTest {

    private val blue = 0xFF1C71D8.toInt()
    private val red = 0xFFE01B24.toInt()

    @Test
    fun `colours survive being stored`() {
        val colors = listOf(blue, red, 0x801C71D8.toInt())
        assertEquals(colors, CustomColors.decode(CustomColors.encode(colors)))
        assertEquals(emptyList<Int>(), CustomColors.decode(null))
        assertEquals(listOf(blue), CustomColors.decode("$blue,nonsense"))
    }

    @Test
    fun `a new colour goes first, without repeats, and the oldest drops off`() {
        assertEquals(listOf(red, blue), CustomColors.added(listOf(blue), red))
        assertEquals(listOf(blue, red), CustomColors.added(listOf(red, blue), blue))
        val full = (1..CustomColors.MAX).map { 0xFF000000.toInt() or it }
        val added = CustomColors.added(full, blue)
        assertEquals(CustomColors.MAX, added.size)
        assertEquals(blue, added.first())
        assertEquals(full[CustomColors.MAX - 2], added.last())
    }

    @Test
    fun `hex as GTK writes it, with the opacity only when there is some`() {
        assertEquals("#1c71d8", CustomColors.toHex(blue))
        assertEquals("#1c71d880", CustomColors.toHex(0x801C71D8.toInt()))
    }

    @Test
    fun `hex typed in reads back, with or without the hash and in any case`() {
        assertEquals(blue, CustomColors.parseHex("#1C71D8"))
        assertEquals(blue, CustomColors.parseHex(" 1c71d8 "))
        assertEquals(0x801C71D8.toInt(), CustomColors.parseHex("#1c71d880"))
        assertNull(CustomColors.parseHex("#1c71d"))
        assertNull(CustomColors.parseHex("#1c71zz"))
        assertNull(CustomColors.parseHex(""))
    }
}
