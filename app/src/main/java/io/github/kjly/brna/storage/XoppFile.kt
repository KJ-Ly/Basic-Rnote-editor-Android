package io.github.kjly.brna.storage

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A Xournal++ `.xopp` file, read and written as Rnote's `xoppformat.rs` does: gzipped XML
 * of pages, each with a background and layers of strokes, texts and images, in units of
 * 1/72 inch. What Rnote leaves out — a stroke's time stamp and audio, a page's PDF — is
 * left out here too.
 */
internal object XoppFile {

    /** The DPI of `.xopp` files, which is hardcoded to 72 DPI. */
    const val DPI = 72.0

    /** The decimal places values are written with, as Rnote's `VALS_DEC_PLACES`. */
    private const val DEC_PLACES = 3

    class ParseException(message: String) : Exception(message)

    data class Color(val red: Int, val green: Int, val blue: Int, val alpha: Int) {
        /** `#RRGGBBAA`, in lower case. */
        fun toAttr(): String = "#%02x%02x%02x%02x".format(red, green, blue, alpha)
    }

    enum class Tool(val attr: String) { PEN("pen"), HIGHLIGHTER("highlighter"), ERASER("eraser") }

    /**
     * A stroke. [width] is the stroke's width, then — where the pen had pressure — one
     * absolute width for each point; [coords] its points.
     */
    data class Stroke(
        val tool: Tool = Tool.PEN,
        val color: Color,
        val fill: Int? = null,
        val width: List<Double>,
        val coords: List<Pair<Double, Double>>
    )

    /** A text: one font, one size, one colour, with its top-left corner at ([x], [y]). */
    data class Text(
        val font: String,
        val size: Double,
        val x: Double,
        val y: Double,
        val color: Color,
        val text: String
    )

    /** An image, its [data] a base64 PNG, stretched over the rectangle given. */
    data class Image(val left: Double, val top: Double, val right: Double, val bottom: Double, val data: String)

    data class Layer(
        val name: String? = null,
        val strokes: List<Stroke> = emptyList(),
        val texts: List<Text> = emptyList(),
        val images: List<Image> = emptyList()
    )

    sealed class Background {
        data class Solid(val color: Color, val style: String) : Background()
        data class Pixmap(val domain: String, val filename: String) : Background()
        object Pdf : Background()
    }

    data class Page(
        val width: Double,
        val height: Double,
        val background: Background = Background.Solid(Color(0, 0, 0, 0xff), "plain"),
        val backgroundName: String? = null,
        val layers: List<Layer> = emptyList()
    )

    data class Root(
        val fileversion: String = "",
        val title: String = "",
        val preview: String = "",
        val pages: List<Page> = emptyList()
    )

    // ── Reading ───────────────────────────────────────────────────────────────

    /** Whether gunzipped [head] — the file's first bytes — is XML rather than Rnote's JSON. */
    fun looksLikeXml(head: ByteArray): Boolean =
        String(head, Charsets.UTF_8).trimStart { it.isWhitespace() || it == '\uFEFF' }.startsWith('<')

    fun load(bytes: ByteArray): Root {
        val xml = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        return parse(xml)
    }

    fun parse(xml: ByteArray): Root {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // A file is data, never a way to reach other files or the network.
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            isExpandEntityReferences = false
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
        var title = ""
        var preview = ""
        val pages = ArrayList<Page>()
        for (child in root.elements()) when (child.tagName) {
            "title" -> child.text()?.let { title = it }
            "preview" -> child.text()?.let { preview = it.trim(' ', '\n') }
            "page" -> pages += parsePage(child)
        }
        return Root(root.getAttribute("fileversion"), title, preview, pages)
    }

    private fun parsePage(node: Element): Page {
        val width = node.required("width", "XoppPage").toDoubleOrThrow()
        val height = node.required("height", "XoppPage").toDoubleOrThrow()
        var background: Background = Background.Solid(Color(0, 0, 0, 0xff), "plain")
        var backgroundName: String? = null
        val layers = ArrayList<Layer>()
        for (child in node.elements()) when (child.tagName) {
            "background" -> {
                backgroundName = child.optional("name")
                background = parseBackground(child)
            }
            "layer" -> layers += parseLayer(child)
        }
        return Page(width, height, background, backgroundName, layers)
    }

    private fun parseBackground(node: Element): Background = when (val type = node.required("type", "XoppBackground")) {
        "solid" -> {
            // A style Rnote doesn't know is taken as plain; it has no styles of its own to show anyway.
            val style = node.required("style", "XoppBackground").takeIf { it in SOLID_STYLES } ?: "plain"
            Background.Solid(backgroundColor(node.required("color", "XoppBackground")), style)
        }
        "pixmap" -> {
            val domain = node.required("domain", "XoppBackground")
            if (domain !in listOf("absolute", "attach", "clone")) throw ParseException("background domain $domain")
            Background.Pixmap(domain, node.required("filename", "XoppBackground"))
        }
        "pdf" -> Background.Pdf
        else -> throw ParseException("background type $type")
    }

    private fun parseLayer(node: Element): Layer {
        val strokes = ArrayList<Stroke>()
        val texts = ArrayList<Text>()
        val images = ArrayList<Image>()
        for (child in node.elements()) when (child.tagName) {
            "stroke" -> strokes += parseStroke(child)
            "text" -> texts += parseText(child)
            "image" -> images += parseImage(child)
        }
        return Layer(node.optional("name"), strokes, texts, images)
    }

    private fun parseStroke(node: Element): Stroke {
        val tool = when (node.required("tool", "XoppStroke")) {
            "highlighter" -> Tool.HIGHLIGHTER
            "eraser" -> Tool.ERASER
            // "pen", and anything else, as Rnote leaves its default.
            else -> Tool.PEN
        }
        val color = strokeColor(node.required("color", "XoppStroke"))
        val fill = node.optional("fill")?.let { it.toIntOrNull() ?: throw ParseException("fill $it") }
        val width = node.required("width", "XoppStroke").split(' ').mapNotNull { it.toDoubleOrNull() }
        // Rnote splits on single spaces and drops what isn't a number, then pairs them up.
        val numbers = node.text()?.trim(' ', '\n')?.split(' ')?.mapNotNull { it.toDoubleOrNull() } ?: emptyList()
        val coords = (0 until numbers.size - 1 step 2).map { numbers[it] to numbers[it + 1] }
        return Stroke(tool, color, fill, width, coords)
    }

    private fun parseText(node: Element) = Text(
        font = node.required("font", "XoppText"),
        size = node.required("size", "XoppText").toDoubleOrThrow(),
        x = node.required("x", "XoppText").toDoubleOrThrow(),
        y = node.required("y", "XoppText").toDoubleOrThrow(),
        color = strokeColor(node.required("color", "XoppText")),
        text = node.text() ?: ""
    )

    private fun parseImage(node: Element) = Image(
        left = node.required("left", "XoppImage").toDoubleOrThrow(),
        top = node.required("top", "XoppImage").toDoubleOrThrow(),
        right = node.required("right", "XoppImage").toDoubleOrThrow(),
        bottom = node.required("bottom", "XoppImage").toDoubleOrThrow(),
        data = node.text()?.trim(' ', '\n') ?: ""
    )

    private val SOLID_STYLES = setOf("plain", "ruled", "lined", "staves", "graph", "dotted", "isodotted", "isograph")

    /** Xournal's named stroke colours, and `#RRGGBBAA`. */
    fun strokeColor(s: String): Color = when (s) {
        "black" -> Color(0x00, 0x00, 0x00, 0xff)
        "blue" -> Color(0x33, 0x33, 0xcc, 0xff)
        "red" -> Color(0xff, 0x00, 0x00, 0xff)
        "green" -> Color(0x00, 0x80, 0x00, 0xff)
        "gray" -> Color(0x80, 0x80, 0x80, 0xff)
        "lightblue" -> Color(0x80, 0xc0, 0xff, 0xff)
        "lightgreen" -> Color(0x00, 0xff, 0x00, 0xff)
        "magenta" -> Color(0xff, 0x00, 0xff, 0xff)
        "orange" -> Color(0xff, 0x80, 0x00, 0xff)
        "yellow" -> Color(0xff, 0xff, 0xf0, 0xff)
        "white" -> Color(0xff, 0xff, 0xff, 0xff)
        else -> hexColor(s)
    }

    /** Xournal's named background colours, and `#RRGGBBAA`. */
    private fun backgroundColor(s: String): Color = when (s) {
        "white" -> Color(0xff, 0xff, 0xff, 0xff)
        "blue" -> Color(0xa0, 0xe8, 0xff, 0xff)
        "pink" -> Color(0xff, 0xc0, 0xd4, 0xff)
        "green" -> Color(0x80, 0xff, 0xc0, 0xff)
        "orange" -> Color(0xff, 0xc0, 0x80, 0xff)
        "yellow" -> Color(0xff, 0xff, 0x80, 0xff)
        else -> hexColor(s)
    }

    private fun hexColor(s: String): Color {
        val value = s.trim().replace("#", "").toLongOrNull(16)?.takeIf { it in 0..0xffffffffL }
            ?: throw ParseException("color $s")
        return Color(
            ((value shr 24) and 0xff).toInt(),
            ((value shr 16) and 0xff).toInt(),
            ((value shr 8) and 0xff).toInt(),
            (value and 0xff).toInt()
        )
    }

    private fun Element.elements(): List<Element> {
        val list = ArrayList<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) list += n as Element
        }
        return list
    }

    /** roxmltree's `Node::text`: the element's first child, if that is text. */
    private fun Element.text(): String? {
        val first = firstChild ?: return null
        return if (first.nodeType == Node.TEXT_NODE || first.nodeType == Node.CDATA_SECTION_NODE) first.nodeValue else null
    }

    private fun Element.optional(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null

    private fun Element.required(name: String, what: String): String =
        optional(name) ?: throw ParseException("$what without `$name`")

    private fun String.toDoubleOrThrow(): Double = toDoubleOrNull() ?: throw ParseException("number $this")

    // ── Writing ───────────────────────────────────────────────────────────────

    fun save(root: Root): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(write(root).toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** The XML, as Rnote's writer puts it out: no declaration, no indentation, no preview. */
    fun write(root: Root): String = buildString {
        append("<xournal fileversion=\"").append(escape(root.fileversion, true)).append("\">")
        append("<title>").append(escape(root.title, false)).append("</title>")
        for (page in root.pages) {
            append("<page width=\"").append(value(page.width)).append("\" height=\"").append(value(page.height)).append("\">")
            append("<background")
            page.backgroundName?.let { attr("name", it) }
            when (val bg = page.background) {
                is Background.Solid -> { attr("type", "solid"); attr("color", bg.color.toAttr()); attr("style", bg.style) }
                is Background.Pixmap -> { attr("type", "pixmap"); attr("domain", bg.domain); attr("filename", bg.filename) }
                Background.Pdf -> attr("type", "pdf")
            }
            append("/>")
            for (layer in page.layers) {
                // Rnote leaves an empty layer out (its #985).
                if (layer.strokes.isEmpty() && layer.texts.isEmpty() && layer.images.isEmpty()) continue
                append("<layer")
                layer.name?.let { attr("name", it) }
                append('>')
                for (s in layer.strokes) {
                    append("<stroke")
                    attr("tool", s.tool.attr)
                    attr("color", s.color.toAttr())
                    s.fill?.let { attr("fill", it.toString()) }
                    attr("width", s.width.joinToString(" ") { value(it) })
                    append('>')
                    append(s.coords.joinToString(" ") { "${value(it.first)} ${value(it.second)}" })
                    append("</stroke>")
                }
                for (t in layer.texts) {
                    append("<text")
                    attr("font", t.font)
                    attr("size", value(t.size))
                    attr("x", value(t.x))
                    attr("y", value(t.y))
                    attr("color", t.color.toAttr())
                    append('>').append(escape(t.text, false)).append("</text>")
                }
                for (i in layer.images) {
                    append("<image")
                    attr("left", value(i.left))
                    attr("top", value(i.top))
                    attr("right", value(i.right))
                    attr("bottom", value(i.bottom))
                    append('>').append(escape(i.data, false)).append("</image>")
                }
                append("</layer>")
            }
            append("</page>")
        }
        append("</xournal>")
    }

    private fun StringBuilder.attr(name: String, value: String) {
        append(' ').append(name).append("=\"").append(escape(value, true)).append('"')
    }

    private fun escape(s: String, attribute: Boolean): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> if (attribute) append("&quot;") else append(c)
            else -> append(c)
        }
    }

    /**
     * Rust's `{:.3}`: the double's exact value rounded half to even to three places, and a
     * minus sign kept on anything negative, even what rounds to zero.
     */
    fun value(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "inf" else "-inf"
        val digits = BigDecimal(kotlin.math.abs(v)).setScale(DEC_PLACES, RoundingMode.HALF_EVEN).toPlainString()
        return if (v < 0.0 || (v == 0.0 && 1.0 / v < 0.0)) "-$digits" else digits
    }
}
