package io.github.kjly.brna.storage

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.github.kjly.brna.model.NativeBackgroundConfig
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativePatternType
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeStrokePoint
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RnoteNativeDocument
import io.github.kjly.brna.model.RoughStyle
import kotlin.math.floor

/**
 * A Xournal++ file to a note and back, as Rnote does it: its `load_from_xopp_bytes` with
 * `Stroke::from_xopp*` one way, its `export_doc_as_xopp_bytes` with `Stroke::into_xopp`
 * the other. Rnote's choices are kept, quirks and all, so a `.xopp` opened here becomes the
 * same note it would on the laptop, and one written here is the file Rnote would write.
 */
internal object XoppConvert {

    /** Rnote's `XoppImportPrefs` default: a `.xopp`'s 72 DPI taken to the document's 96. */
    const val IMPORT_DPI = 96.0

    /** The DPI of the documents this app writes, as `format.dpi`. */
    const val DOCUMENT_DPI = 96.0

    /** Rnote's `Engine::STROKE_EXPORT_IMAGE_SCALE`: what shapes, text and images are drawn at. */
    const val IMAGE_SCALE = 1.8

    /** Rnote's `Engine::STROKE_BOUNDS_INTERSECTION_TOLERANCE`. */
    private const val INTERSECTION_TOLERANCE = 1e-3

    private const val TITLE =
        "Xournal++ document - see https://github.com/xournalpp/xournalpp (exported from Rnote - see https://github.com/flxzt/rnote)"

    /** A decoded picture, as Rnote keeps one: premultiplied RGBA, base64. */
    class Pixels(val rgbaBase64: String, val width: Int, val height: Int)

    private fun toDoc(v: Double, dpi: Double = IMPORT_DPI) = v / XoppFile.DPI * dpi
    private fun toXopp(v: Double, dpi: Double = DOCUMENT_DPI) = v / dpi * XoppFile.DPI

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * The file as a note: pages one below the other in one space, as wide as the widest,
     * on an infinite canvas without a pattern — Rnote's own import. [decodeImage] turns an
     * image's encoded bytes into pixels; an image it can't read is left out, as Rnote
     * leaves out what it can't convert.
     */
    fun toNative(root: XoppFile.Root, decodeImage: (ByteArray) -> Pixels?): RnoteNativeDocument {
        var docWidth = 0.0
        var docHeight = 0.0
        for (page in root.pages) {
            docWidth = maxOf(docWidth, page.width)
            docHeight += page.height
        }
        val pageCount = root.pages.size

        val elements = ArrayList<NativeCanvasElement>()
        var offsetY = 0.0
        for (page in root.pages) {
            for (layer in page.layers) {
                layer.strokes.mapNotNullTo(elements) { brushStroke(it, offsetY) }
                layer.images.mapNotNullTo(elements) { bitmap(it, offsetY, decodeImage) }
                layer.texts.mapNotNullTo(elements) { text(it, offsetY) }
            }
            offsetY += toDoc(page.height)
        }

        val solid = root.pages.firstOrNull()?.background is XoppFile.Background.Solid
        // The format is as wide as the widest page and one page of the average height, as
        // Rnote divides the total by the count. A file without pages, or with pages of no
        // size, keeps the default format rather than one of no size (Rnote's NaN).
        val defaults = RnoteNativeDocument()
        val width = toDoc(docWidth).toFloat().takeIf { it > 0f } ?: defaults.pageWidth
        val height = (if (pageCount > 0) toDoc(docHeight / pageCount).toFloat() else 0f).takeIf { it > 0f }
            ?: defaults.pageHeight
        return RnoteNativeDocument(
            pageWidth = width,
            pageHeight = height,
            // Xournal's patterns aren't Rnote's, so a plain page for a solid background;
            // anything else keeps Rnote's default pattern, as it does there.
            background = NativeBackgroundConfig(
                color = RnoteNativeColor.WHITE,
                pattern = if (solid) NativePatternType.BLANK else NativePatternType.DOTS,
                patternWidth = 32f,
                patternHeight = 32f,
                patternColor = RnoteNativeColor(0.8f, 0.9f, 1f, 1f)
            ),
            elements = elements,
            // Rnote's default layout, which its import leaves as it is.
            layout = "infinite",
            originX = 0f,
            originY = 0f,
            totalWidth = width,
            totalHeight = maxOf(toDoc(docHeight).toFloat(), height)
        )
    }

    /** `Stroke::from_xoppstroke`. */
    fun brushStroke(stroke: XoppFile.Stroke, offsetY: Double): NativeBrushStroke? {
        if (stroke.width.isEmpty()) return null
        val widths = stroke.width.map { toDoc(it) }
        val coords = stroke.coords.map { (x, y) -> toDoc(x) to toDoc(y) + offsetY }

        val color = when (stroke.tool) {
            XoppFile.Tool.PEN -> fromXopp(stroke.color)
            // The highlighter always has alpha 0.5.
            XoppFile.Tool.HIGHLIGHTER -> fromXopp(stroke.color).copy(a = 0.5f)
            XoppFile.Tool.ERASER -> RnoteNativeColor.WHITE
        }

        // The first width is the stroke's; any others are the absolute widths at its
        // points, which Rnote takes as pressures against the largest of them.
        val pointWidths = widths.drop(1)
        val strokeWidth: Double
        val pressures: List<Double>
        if (pointWidths.isNotEmpty()) {
            strokeWidth = pointWidths.max()
            // All of them 0: nothing to draw, but no 0/0 either.
            pressures = pointWidths.map { if (strokeWidth > 0.0) it / strokeWidth else 0.0 }
        } else {
            strokeWidth = widths[0]
            pressures = coords.map { 1.0 }
        }
        // Zipped, as Rnote zips them: Xournal++ gives one width fewer than it has points,
        // so a stroke with pressure loses its last point on the way in.
        val points = coords.zip(pressures) { (x, y), p ->
            NativeStrokePoint(x.toFloat(), y.toFloat(), p.toFloat().coerceIn(0f, 1f))
        }
        if (points.isEmpty()) return null

        return NativeBrushStroke(
            points = points,
            strokeWidth = strokeWidth.toFloat(),
            color = color,
            isHighlighter = stroke.tool == XoppFile.Tool.HIGHLIGHTER,
            minX = points.minOf { it.x }, minY = points.minOf { it.y },
            maxX = points.maxOf { it.x }, maxY = points.maxOf { it.y },
            pressureCurve = PressureCurve.DEFAULT
        )
    }

    /** `Stroke::from_xopptext`: a text box in Rnote's default style, with the font, size and colour given. */
    fun text(text: XoppFile.Text, offsetY: Double): NativeCanvasElement? {
        val color = fromXopp(text.color)
        val style = JsonObject().apply {
            addProperty("font_family", text.font)
            addProperty("font_size", toDoc(text.size))
            addProperty("font_weight", 500)
            addProperty("font_style", "regular")
            add("color", JsonObject().apply {
                addProperty("r", color.r.toDouble()); addProperty("g", color.g.toDouble())
                addProperty("b", color.b.toDouble()); addProperty("a", color.a.toDouble())
            })
            add("max_width", JsonNull.INSTANCE)
            addProperty("alignment", "start")
            add("ranged_text_attributes", JsonArray())
        }
        val obj = JsonObject().apply {
            addProperty("text", text.text)
            add("transform", affine(1.0, 1.0, toDoc(text.x), toDoc(text.y) + offsetY))
            add("text_style", style)
        }
        return RnoteNativeParser.parseElementTree(JsonObject().apply { add("textstroke", obj) })
    }

    /** `Stroke::from_xoppimage`: the picture's own pixels, stretched over the rectangle given. */
    fun bitmap(image: XoppFile.Image, offsetY: Double, decodeImage: (ByteArray) -> Pixels?): NativeBitmapElement? {
        val bytes = try {
            java.util.Base64.getMimeDecoder().decode(image.data)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val pixels = decodeImage(bytes) ?: return null
        val left = toDoc(image.left)
        val top = toDoc(image.top) + offsetY
        val right = toDoc(image.right)
        val bottom = toDoc(image.bottom) + offsetY
        // Rnote's Aabb takes the corners as given; a flipped one is its own business.
        val hx = (right - left) / 2.0
        val hy = (bottom - top) / 2.0
        val pw = pixels.width / 2.0
        val ph = pixels.height / 2.0
        val tree = JsonObject().apply {
            add("bitmapimage", JsonObject().apply {
                add("image", JsonObject().apply {
                    addProperty("data", pixels.rgbaBase64)
                    add("rectangle", rectangle(pw, ph, pw, ph))
                    addProperty("pixel_width", pixels.width)
                    addProperty("pixel_height", pixels.height)
                    addProperty("memory_format", "R8g8b8a8Premultiplied")
                })
                add("rectangle", rectangle(hx, hy, left + hx, top + hy))
            })
        }
        return RnoteNativeParser.parseElementTree(tree) as? NativeBitmapElement
    }

    private fun rectangle(hx: Double, hy: Double, cx: Double, cy: Double) = JsonObject().apply {
        add("cuboid", JsonObject().apply { add("half_extents", JsonArray().apply { add(hx); add(hy) }) })
        add("transform", affine(1.0, 1.0, cx, cy))
    }

    private fun affine(sx: Double, sy: Double, tx: Double, ty: Double) = JsonObject().apply {
        add("affine", JsonArray().apply { listOf(sx, 0.0, 0.0, 0.0, sy, 0.0, tx, ty, 1.0).forEach { add(it) } })
    }

    /** Rnote's `color_from_xopp`. */
    private fun fromXopp(c: XoppFile.Color) = RnoteNativeColor(
        (c.red / 255.0).toFloat(), (c.green / 255.0).toFloat(), (c.blue / 255.0).toFloat(), (c.alpha / 255.0).toFloat()
    )

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * The note as Rnote exports it: each page of the note that has something on it is a
     * page, with one solid background of the note's colour; the brush strokes stay strokes,
     * and whatever else is on the page — shapes, text, images — goes in as a picture of
     * itself underneath, drawn by [renderImage] over the extent it is given at
     * [IMAGE_SCALE] (a PNG, or null to leave it out). [pages] are the note's pages, at
     * least one: for a note without pages, one
     * round what is on it. With nothing on any, the first page's size at the origin, as
     * Rnote's `pages_bounds_w_content` gives.
     */
    fun fromNative(
        doc: RnoteNativeDocument,
        pages: List<PageRect>,
        renderImage: (NativeCanvasElement, PageRect) -> ByteArray?
    ): XoppFile.Root {
        // Rnote's render order: document, then images, then highlighters, then ink, each in
        // the order it was made in.
        val ordered = doc.elements.withIndex().sortedWith(compareBy({ layerRank(it.value) }, { it.index })).map { it.value }
        require(pages.isNotEmpty()) { "A note has at least one page to export" }
        val withContent = pages.filter { page -> ordered.any { intersects(bounds(it), page) } }
            .ifEmpty { listOf(PageRect(0.0, 0.0, pages[0].width, pages[0].height)) }
        val background = XoppFile.Background.Solid(toXopp(doc.background.color), "plain")

        val out = withContent.map { page ->
            val strokes = ArrayList<XoppFile.Stroke>()
            val images = ArrayList<XoppFile.Image>()
            for (el in ordered) {
                val b = bounds(el)
                if (!intersects(b, page)) continue
                when (el) {
                    is NativeBrushStroke -> strokeOf(el, page)?.let { strokes += it }
                    else -> renderImage(el, b)?.let { png ->
                        images += XoppFile.Image(
                            left = toXopp(b.minX - page.minX),
                            top = toXopp(b.minY - page.minY),
                            right = toXopp(b.maxX - page.minX),
                            bottom = toXopp(b.maxY - page.minY),
                            data = java.util.Base64.getEncoder().encodeToString(png)
                        )
                    }
                }
            }
            XoppFile.Page(
                width = toXopp(page.width),
                height = toXopp(page.height),
                background = background,
                // In Rnote images are always below strokes and text, so they get a layer underneath.
                layers = listOf(XoppFile.Layer(images = images), XoppFile.Layer(strokes = strokes))
            )
        }
        return XoppFile.Root(fileversion = "4", title = TITLE, pages = out)
    }

    /** `Stroke::into_xopp` for a brush stroke, moved to its page. Null for fewer than two points. */
    fun strokeOf(el: NativeBrushStroke, page: PageRect): XoppFile.Stroke? {
        // Xopp expects at least 4 coordinates.
        if (el.points.size < 2) return null
        val width = toXopp(el.strokeWidth.asRnoteReads())
        val pressures = el.points.map {
            val p = it.pressure.asRnoteReads()
            if (el.textured != null) width * p else el.pressureCurve.apply(width, p)
        }
        return XoppFile.Stroke(
            tool = XoppFile.Tool.PEN,
            color = toXopp(el.color),
            width = listOf(width) + pressures,
            // A curve goes as the points it runs through: Rnote's `into_elements`.
            coords = el.points.map { toXopp(it.x.asRnoteReads() - page.minX) to toXopp(it.y.asRnoteReads() - page.minY) }
        )
    }

    /**
     * Rnote's `xoppcolor_from_color`, `floor(c × 255)`, on the value it would read from
     * the file this app writes — the float as written out, read back as a double.
     */
    private fun toXopp(c: RnoteNativeColor) = XoppFile.Color(
        channel(c.r), channel(c.g), channel(c.b), channel(c.a)
    )

    private fun channel(v: Float): Int = floor(v.asRnoteReads() * 255.0).toInt().coerceIn(0, 255)

    /** A float as the `.rnote` this app writes has it, read back as Rnote reads it: a double. */
    private fun Float.asRnoteReads(): Double = toString().toDouble()

    /** Where an element sits in Rnote's rendering order (`StrokeLayer`'s `Ord`). */
    private fun layerRank(el: NativeCanvasElement): Int = when (el) {
        is NativeVectorImageElement -> if (el.layer == "document") 0 else 1
        is NativeBitmapElement -> if (el.layer == "document") 0 else 1
        is NativeBrushStroke -> if (el.isHighlighter) 2 else 3
        else -> 3
    }

    /**
     * An element's extent as Rnote's `bounds` gives it: a stroke or shape takes in half its
     * width either side (a Textured stroke its whole width, a rough shape its wobble too),
     * so a level line has a height and an outline isn't cut off at its edges.
     */
    fun bounds(el: NativeCanvasElement): PageRect {
        val pad = when (el) {
            is NativeBrushStroke -> if (el.textured != null) el.strokeWidth.toDouble() else el.strokeWidth / 2.0
            is NativeShapeElement ->
                el.strokeWidth / 2.0 + if (el.rough != null) RoughStyle.BOUNDS_MARGIN.toDouble() else 0.0
            else -> 0.0
        }
        return PageRect(el.minX - pad, el.minY - pad, el.maxX + pad, el.maxY + pad)
    }

    /** Rnote's `intersects_w_tolerance`. */
    private fun intersects(a: PageRect, b: PageRect): Boolean =
        a.minX <= b.maxX + INTERSECTION_TOLERANCE && a.maxX >= b.minX - INTERSECTION_TOLERANCE &&
            a.minY <= b.maxY + INTERSECTION_TOLERANCE && a.maxY >= b.minY - INTERSECTION_TOLERANCE

    /** A page, or any extent, in document units. */
    data class PageRect(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
        val width: Double get() = maxX - minX
        val height: Double get() = maxY - minY
    }
}
