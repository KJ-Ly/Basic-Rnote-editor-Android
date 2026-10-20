package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Xournal++'s file format, read and written as Rnote's `xoppformat.rs` reads and writes it. */
class XoppFileTest {

    private val xml = """<?xml version="1.0" standalone="no"?>
<xournal creator="Xournal++ 1.2.3" fileversion="4">
<title>Xournal++ document - see https://xournalpp.github.io/</title>
<preview>
iVBORw0KGgo=
</preview>
<page width="595.27559100" height="841.88976400">
<background type="solid" color="#ffffffff" style="graph"/>
<layer>
<stroke tool="pen" color="#3333ccff" width="1.41 1.2 1.3">10 20 30 40 50 60</stroke>
<stroke tool="highlighter" color="yellow" width="8.5">1 2 3 4</stroke>
<text font="Sans" size="12" x="100" y="120" color="#000000ff">Hallo &amp; tschüss</text>
<image left="10" top="20" right="110" bottom="70">
AAAA
</image>
</layer>
</page>
<page width="595.27559100" height="841.88976400">
<background name="p2" type="solid" color="blue" style="lined"/>
<layer name="Ebene 1"/>
</page>
</xournal>
"""

    @Test
    fun `a Xournal++ file is read as Rnote reads it`() {
        val root = XoppFile.parse(xml.toByteArray())
        assertEquals("4", root.fileversion)
        assertEquals("Xournal++ document - see https://xournalpp.github.io/", root.title)
        assertEquals("iVBORw0KGgo=", root.preview)
        assertEquals(2, root.pages.size)

        val page = root.pages[0]
        assertEquals(595.275591, page.width, 1e-9)
        assertEquals(XoppFile.Background.Solid(XoppFile.Color(255, 255, 255, 255), "graph"), page.background)
        val layer = page.layers.single()

        val pen = layer.strokes[0]
        assertEquals(XoppFile.Tool.PEN, pen.tool)
        assertEquals(XoppFile.Color(0x33, 0x33, 0xcc, 0xff), pen.color)
        assertEquals(listOf(1.41, 1.2, 1.3), pen.width)
        assertEquals(listOf(10.0 to 20.0, 30.0 to 40.0, 50.0 to 60.0), pen.coords)

        // Xournal's named colours.
        val marker = layer.strokes[1]
        assertEquals(XoppFile.Tool.HIGHLIGHTER, marker.tool)
        assertEquals(XoppFile.Color(0xff, 0xff, 0xf0, 0xff), marker.color)

        val text = layer.texts.single()
        assertEquals("Hallo & tschüss", text.text)
        assertEquals("Sans", text.font)
        assertEquals(12.0, text.size, 0.0)

        val image = layer.images.single()
        assertEquals("AAAA", image.data)
        assertEquals(110.0, image.right, 0.0)

        val second = root.pages[1]
        assertEquals("p2", second.backgroundName)
        assertEquals(XoppFile.Background.Solid(XoppFile.Color(0xa0, 0xe8, 0xff, 0xff), "lined"), second.background)
        assertEquals("Ebene 1", second.layers.single().name)
    }

    @Test
    fun `numbers go out as Rust writes them to three places`() {
        assertEquals("1.000", XoppFile.value(1.0))
        assertEquals("0.938", XoppFile.value(0.9375))
        // An exact tie rounds to even, as Rust's formatting does, not up.
        assertEquals("0.062", XoppFile.value(0.0625))
        assertEquals("-0.000", XoppFile.value(-0.0001))
        assertEquals("-12.346", XoppFile.value(-12.3456))
        assertEquals("595.276", XoppFile.value(595.275591))
    }

    @Test
    fun `a file is written as Rnote writes one, and reads back`() {
        val root = XoppFile.Root(
            fileversion = "4",
            title = "T <1>",
            pages = listOf(
                XoppFile.Page(
                    width = 595.5, height = 842.0,
                    background = XoppFile.Background.Solid(XoppFile.Color(255, 255, 255, 255), "plain"),
                    layers = listOf(
                        // Left out: Rnote doesn't write an empty layer.
                        XoppFile.Layer(),
                        XoppFile.Layer(
                            strokes = listOf(
                                XoppFile.Stroke(
                                    color = XoppFile.Color(0, 0, 0, 255),
                                    width = listOf(1.5, 0.75),
                                    coords = listOf(1.0 to 2.0, 3.25 to 4.5)
                                )
                            ),
                            texts = listOf(XoppFile.Text("Sans", 12.0, 1.0, 2.0, XoppFile.Color(1, 2, 3, 4), "a<b"))
                        )
                    )
                )
            )
        )
        val xml = XoppFile.write(root)
        assertEquals(
            "<xournal fileversion=\"4\"><title>T &lt;1&gt;</title>" +
                "<page width=\"595.500\" height=\"842.000\">" +
                "<background type=\"solid\" color=\"#ffffffff\" style=\"plain\"/>" +
                "<layer><stroke tool=\"pen\" color=\"#000000ff\" width=\"1.500 0.750\">1.000 2.000 3.250 4.500</stroke>" +
                "<text font=\"Sans\" size=\"12.000\" x=\"1.000\" y=\"2.000\" color=\"#01020304\">a&lt;b</text></layer>" +
                "</page></xournal>",
            xml
        )
        val back = XoppFile.load(XoppFile.save(root))
        assertEquals(1, back.pages.single().layers.size)
        assertEquals(root.pages[0].layers[1].strokes, back.pages[0].layers[0].strokes)
        assertEquals("a<b", back.pages[0].layers[0].texts.single().text)
    }

    @Test
    fun `a gzipped file is told apart from Rnote's by its first character`() {
        assertTrue(XoppFile.looksLikeXml("<?xml".toByteArray()))
        assertTrue(XoppFile.looksLikeXml("\n  <xournal".toByteArray()))
        assertFalse(XoppFile.looksLikeXml("{\"data\"".toByteArray()))
        assertFalse(XoppFile.looksLikeXml(ByteArray(0)))
    }

    @Test
    fun `a NaN among the numbers is dropped, and a page of NaN size is an error`() {
        val stroke = XoppFile.parse(
            "<xournal><page width=\"1\" height=\"1\"><layer><stroke tool=\"pen\" color=\"black\" width=\"1 NaN\">1 2 NaN 3 4</stroke></layer></page></xournal>".toByteArray()
        ).pages.single().layers.single().strokes.single()
        assertEquals(listOf(1.0), stroke.width)
        assertEquals(listOf(1.0 to 2.0, 3.0 to 4.0), stroke.coords)
    }

    @Test(expected = XoppFile.ParseException::class)
    fun `a page of NaN size is an error`() {
        XoppFile.parse("<xournal><page width=\"NaN\" height=\"1\"/></xournal>".toByteArray())
    }

    @Test(expected = XoppFile.ParseException::class)
    fun `a page without a size is an error, as in Rnote`() {
        XoppFile.parse("<xournal><page height=\"1\"/></xournal>".toByteArray())
    }
}
