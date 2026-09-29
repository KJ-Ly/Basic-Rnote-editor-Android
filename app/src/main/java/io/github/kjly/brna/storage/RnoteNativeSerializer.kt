package io.github.kjly.brna.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.LayoutMode
import io.github.kjly.brna.model.LineShape
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativePatternType
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.PathOp
import io.github.kjly.brna.model.PathShape
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RnoteNativeDocument
import io.github.kjly.brna.model.RoughStyle
import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.model.NoteDocument
import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.TexturedStyle
import io.github.kjly.brna.model.PaperPattern
import io.github.kjly.brna.render.RoughShapes
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.GZIPOutputStream

/**
 * Serialises a [RnoteNativeDocument] (or a [NoteDocument] bridged to native)
 * back to the .rnote wire format: GZIP-compressed JSON matching the desktop
 * Rnote engine_snapshot schema.
 */
object RnoteNativeSerializer {

    // ── Entry points ──────────────────────────────────────────────────────────

    fun serialize(context: Context, uri: Uri, doc: RnoteNativeDocument): Boolean = try {
        // "wt" truncates; the default mode leaves a shorter save sitting inside the
        // remains of a longer one, which for GZIP means a file that no longer parses.
        context.contentResolver.openOutputStream(uri, "wt")!!.use { serialize(it, doc) }
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    /** Bridge: convert our editable [NoteDocument] → native format and write. */
    fun serializeFromNoteDocument(context: Context, uri: Uri, doc: NoteDocument): Boolean {
        val native = bridgeToNative(doc)
        return serialize(context, uri, native)
    }

    fun serialize(outputStream: OutputStream, doc: RnoteNativeDocument) {
        GZIPOutputStream(outputStream).use { gzip ->
            OutputStreamWriter(gzip, Charsets.UTF_8).buffered(WRITE_BUFFER_CHARS).use { writer ->
                writeJson(doc, writer)
            }
        }
    }

    // ── Bridge NoteDocument → RnoteNativeDocument ─────────────────────────────

    internal fun bridgeToNative(doc: NoteDocument): RnoteNativeDocument {
        val nativePattern = when (doc.paperStyle.pattern) {
            PaperPattern.DOTS     -> NativePatternType.DOTS
            PaperPattern.GRID     -> NativePatternType.GRID
            PaperPattern.LINES    -> NativePatternType.RULED
            PaperPattern.ISO_GRID -> NativePatternType.ISO_GRID
            PaperPattern.ISO_DOTS -> NativePatternType.ISO_DOTS
            PaperPattern.BLANK    -> NativePatternType.BLANK
        }
        val bgColor = doc.paperStyle.currentBackgroundColor.let {
            RnoteNativeColor(it.red, it.green, it.blue, it.alpha)
        }
        val gridColor = doc.paperStyle.currentGridColor.let {
            RnoteNativeColor(it.red, it.green, it.blue, it.alpha)
        }

        val pageW = doc.paperStyle.effectivePageWidthPx
        val pageH = doc.paperStyle.effectivePageHeightPx

        val nativeStrokes: List<NativeCanvasElement> = doc.strokes.map { stroke ->
            // Rnote's `Element::new` clamps pressure to [0, 1] and its serde reader
            // assumes that range; Android reports stylus pressure that can exceed 1.0.
            val pts = stroke.points.map {
                io.github.kjly.brna.model.NativeStrokePoint(it.x, it.y, it.pressure.coerceIn(0f, 1f), it.curve)
            }
            val color = RnoteNativeColor(
                stroke.color.red, stroke.color.green, stroke.color.blue, stroke.color.alpha
            )
            val minX = pts.minOfOrNull { it.x } ?: 0f
            val minY = pts.minOfOrNull { it.y } ?: 0f
            val maxX = pts.maxOfOrNull { it.x } ?: 0f
            val maxY = pts.maxOfOrNull { it.y } ?: 0f
            NativeBrushStroke(
                pts, stroke.strokeWidth, color, stroke.isHighlighter,
                minX, minY, maxX, maxY, stroke.pressureCurve, stroke.textured,
                // A desktop stroke nothing has changed goes back exactly as it came.
                raw = stroke.source?.takeIf { it.describes(stroke) }?.json
            )
        }

        // Include preserved native elements (text, shapes, images) in save-back
        val allElements: List<NativeCanvasElement> = nativeStrokes + doc.nativeElements.filter { it !is NativeBrushStroke }

        // Rnote recomputes a document's extent from what it holds and how it is laid out
        // (`Document::resize_autoexpand`), so derive the same rect it would rather than
        // writing a page-sized one: until the file is edited on the desktop it is this
        // rect that is shown, and a continuous-vertical note written as exactly one page
        // opens looking like a fixed-size one.
        val ink = inkBounds(allElements)
        var minX = 0f; var minY = 0f; var maxX = pageW; var maxY = pageH
        when (doc.paperStyle.layoutMode) {
            // A fixed-size document is its pages of the format, one below the other.
            // Content drawn outside them is still kept (Rnote keeps it too) but does not
            // add a page: only Add Page and Resize to Fit Content do, in Rnote as here.
            LayoutMode.FIXED_SIZE -> maxY = pageH * doc.paperStyle.fixedPages

            // Width is pinned to the format; height is the content plus one page of room
            // to keep writing — the +height is Rnote's, not padding of our own.
            LayoutMode.CONTINUOUS_VERTICAL ->
                maxY = maxOf(pageH, (ink?.maxY ?: 0f).coerceAtLeast(0f) + pageH)

            // Anchored at the origin like Rnote's `resize_doc_semi_infinite_layout`: the
            // extent only ever reaches out to the right and down.
            LayoutMode.SEMI_INFINITE -> if (ink != null) {
                if (ink.maxX > maxX) maxX = ink.maxX
                if (ink.maxY > maxY) maxY = ink.maxY
            }

            // No bounds to respect, so it is the page widened to cover everything.
            // Infinite-layout content sits at negative coordinates routinely.
            LayoutMode.INFINITE -> if (ink != null) {
                if (ink.minX < minX) minX = ink.minX
                if (ink.minY < minY) minY = ink.minY
                if (ink.maxX > maxX) maxX = ink.maxX
                if (ink.maxY > maxY) maxY = ink.maxY
            }
        }

        return RnoteNativeDocument(
            pageWidth   = pageW,
            pageHeight  = pageH,
            background  = io.github.kjly.brna.model.NativeBackgroundConfig(
                color        = bgColor,
                pattern      = nativePattern,
                patternWidth = doc.paperStyle.gridSpacingPx,
                // Rnote's `pattern_size` is two numbers; writing the width twice lost a
                // height set on either side, and ruled paper is where they differ.
                patternHeight = doc.paperStyle.patternHeightPx,
                patternColor = gridColor
            ),
            elements = allElements,
            layout = doc.paperStyle.layoutMode.apiName,
            originX = minX,
            originY = minY,
            totalWidth = maxX - minX,
            totalHeight = maxY - minY,
            // The stored field, not `currentBorderColor` -- that one derives a colour from
            // dark mode when the stored one is untouched, and it is the stored one an import
            // populates, so round-tripping it is what keeps native -> app -> native exact.
            borderColor = doc.paperStyle.formatBorderColor.let {
                RnoteNativeColor(it.red, it.green, it.blue, it.alpha)
            },
            showBorders = doc.paperStyle.showFormatBorders,
            showOriginIndicator = doc.paperStyle.showOriginIndicator
        )
    }

    /** Element bounds in canvas coordinates; null when the document is empty. */
    private class InkBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

    /**
     * The bounds of the ink, not of the input points: a stroke is painted half its width
     * either side of its centreline, and Rnote's own stroke bounds cover that envelope —
     * which is why a 2px stroke ending at y=57.947 leaves a desktop-saved document exactly
     * 58.947 tall past its page. Non-stroke elements already carry painted bounds.
     */
    private fun inkBounds(elements: List<NativeCanvasElement>): InkBounds? {
        if (elements.isEmpty()) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (el in elements) {
            val pad = when (el) {
                is NativeBrushStroke   -> el.strokeWidth / 2f
                // A rough shape's bounds leave room for its wobble, as Rnote's do.
                is NativeShapeElement  -> RoughShapes.margin(el)
                else                   -> 0f
            }
            if (el.minX - pad < minX) minX = el.minX - pad
            if (el.minY - pad < minY) minY = el.minY - pad
            if (el.maxX + pad > maxX) maxX = el.maxX + pad
            if (el.maxY + pad > maxY) maxY = el.maxY + pad
        }
        return InkBounds(minX, minY, maxX, maxY)
    }

    // ── JSON builder ──────────────────────────────────────────────────────────

    /**
     * Writes the whole file to [out]. Built piecewise rather than as one string: an image
     * is megabytes of base64, and a note holding a few photos, built up as a single
     * string and then copied out of it, ran autosave out of memory.
     */
    private fun writeJson(doc: RnoteNativeDocument, out: java.io.Writer) {
        val sb = StringBuilder()
        sb.append("""{"version":"0.14.2","data":{"engine_snapshot":{""")
        sb.append(""""document":""")
        sb.appendDocument(doc)
        sb.append(""","camera":{"offset":[0.0,0.0],"size":[${doc.pageWidth},${doc.pageHeight}],"zoom":1.0}""")
        // Rnote's stroke_components/chrono_components are backed by a slotmap whose index 0 is
        // a reserved sentinel slot (never a real element) — real files always carry a leading
        // {"value":null,"version":0} placeholder and start real elements at index 1. Omitting it
        // breaks slot-key reconstruction and desktop Rnote refuses to open the file.
        sb.append(""","stroke_components":[{"value":null,"version":0}""")
        doc.elements.forEach { el ->
            sb.append(",{\"value\":")
            val rawImage = (el as? NativeBitmapElement)?.raw
            if (rawImage != null) {
                // Straight into the stream, the pixels never copied into the builder.
                out.append(sb)
                sb.setLength(0)
                out.write("{\"bitmapimage\":")
                writeTree(rawImage, out)
                out.write("}")
            } else {
                sb.appendElement(el)
            }
            sb.append(""","version":1}""")
            if (sb.length >= WRITE_BUFFER_CHARS) {
                out.append(sb)
                sb.setLength(0)
            }
        }

        sb.append("""],"chrono_components":[{"value":null,"version":0}""")
        doc.elements.forEachIndexed { i, el ->
            val layer = when {
                el is NativeBrushStroke && el.isHighlighter -> "\"highlighter\""
                // PDF pages sit on Rnote's document layer, underneath everything else.
                el is NativeVectorImageElement ->
                    if (el.layer == "document") "\"document\"" else "\"image\""
                // Rnote's default layer for images; a bitmap-imported PDF page is "document".
                el is NativeBitmapElement ->
                    if (el.layer == "document") "\"document\"" else "\"image\""
                else -> """{"user_layer":0}"""
            }
            sb.append(""",{"value":{"t":${i + 1},"layer":$layer},"version":1}""")
        }
        sb.append("""],"chrono_counter":${doc.elements.size}}}}""")
        out.append(sb)
    }

    /** How much is gathered before it goes to the stream, in chars. */
    private const val WRITE_BUFFER_CHARS = 64 * 1024

    private val treeAdapter = com.google.gson.Gson().getAdapter(com.google.gson.JsonElement::class.java)

    /** [tree] as [com.google.gson.JsonElement.toString] writes it, byte for byte, but streamed. */
    private fun writeTree(tree: com.google.gson.JsonElement, out: java.io.Writer) {
        val writer = com.google.gson.stream.JsonWriter(out)
        writer.isLenient = true
        treeAdapter.write(writer, tree)
    }

    // ── Document block ────────────────────────────────────────────────────────

    private fun StringBuilder.appendDocument(doc: RnoteNativeDocument) {
        val bg = doc.background
        // Rnote's orientation is a label on the format, not a second source of truth for
        // it: the width and height are already swapped by the time they reach here. It
        // used to be written as "portrait" whatever the page was, which left a landscape
        // file contradicting itself — desktop believes the field, so its format panel
        // showed Portrait for a page half again as wide as it was tall, and the next
        // orientation toggle there started from the wrong state.
        val orientation = if (doc.pageWidth > doc.pageHeight) "landscape" else "portrait"
        append("""{
            |"config":{
            |  "format":{
            |    "width":${doc.pageWidth},
            |    "height":${doc.pageHeight},
            |    "dpi":96,
            |    "orientation":"$orientation",
            |    "border_color":${doc.borderColor.toJson()},
            |    "show_borders":${doc.showBorders},
            |    "show_origin_indicator":${doc.showOriginIndicator}
            |  },
            |  "background":{
            |    "color":${bg.color.toJson()},
            |    "pattern":"${bg.pattern.toApiString()}",
            |    "pattern_size":[${bg.patternWidth},${bg.patternHeight}],
            |    "pattern_color":${bg.patternColor.toJson()}
            |  },
            |  "layout":"${doc.layout}"
            |},
            |"x":${doc.originX},
            |"y":${doc.originY},
            |"width":${doc.totalWidth},
            |"height":${doc.totalHeight}
            |}""".trimMargin().replace("\n", ""))
    }

    // ── Element dispatch ──────────────────────────────────────────────────────

    private fun StringBuilder.appendElement(el: NativeCanvasElement) {
        when (el) {
            is NativeBrushStroke -> appendBrushStroke(el)
            is NativeTextElement  -> appendTextElement(el)
            is NativeBitmapElement -> appendBitmapElement(el)
            is NativeShapeElement  -> appendShapeElement(el)
            is NativeVectorImageElement -> appendVectorImage(el)
        }
    }

    // ── BrushStroke ───────────────────────────────────────────────────────────

    private fun StringBuilder.appendBrushStroke(el: NativeBrushStroke) {
        el.raw?.let { append("""{"brushstroke":""").append(it).append('}'); return }
        append("""{"brushstroke":{""")
        append(""""path":""")
        appendPenPath(el.points)
        append(""","style":""")
        val textured = el.textured
        if (textured != null) {
            appendTexturedStyle(el.color, el.strokeWidth, el.pressureCurve, textured)
        } else {
            appendSmoothStyle(el.color, el.strokeWidth, el.pressureCurve)
        }
        append("""}}""")
    }

    /** Rnote's `TexturedOptions`, with the names and in the order it writes them. */
    private fun StringBuilder.appendTexturedStyle(
        color: RnoteNativeColor,
        strokeWidth: Float,
        pressureCurve: PressureCurve,
        textured: TexturedStyle
    ) {
        append("""{"textured":{""")
        append(""""seed":${TexturedStyle.seedJson(textured.seed)},""")
        append(""""stroke_width":$strokeWidth,""")
        append(""""stroke_color":${color.toJson()},""")
        append(""""density":${textured.density},""")
        append(""""distribution":"${textured.distribution.apiName}",""")
        append(""""pressure_curve":"${pressureCurve.apiName}"""")
        append("}}")
    }

    /** Rnote's `PenPath`: a required `start` element plus a list of `segments` (no legacy alias). */
    private fun StringBuilder.appendPenPath(points: List<io.github.kjly.brna.model.NativeStrokePoint>) {
        append("""{"start":""")
        appendPathPoint(points.firstOrNull() ?: io.github.kjly.brna.model.NativeStrokePoint(0f, 0f, 0f))
        append(""","segments":[""")
        for (i in 1 until points.size) {
            if (i > 1) append(',')
            val pt = points[i]
            // Rnote's `Segment` variants, their fields in its order.
            when (val curve = pt.curve) {
                null -> append("""{"lineto":{"end":""")
                is SegmentCurve.Quad -> append("""{"quadbezto":{"cp":[${curve.cx},${curve.cy}],"end":""")
                is SegmentCurve.Cubic ->
                    append("""{"cubbezto":{"cp1":[${curve.c1x},${curve.c1y}],"cp2":[${curve.c2x},${curve.c2y}],"end":""")
            }
            appendPathPoint(pt)
            append("""}}""")
        }
        append("]}")
    }

    private fun StringBuilder.appendPathPoint(pt: io.github.kjly.brna.model.NativeStrokePoint) {
        append("""{"pos":[${pt.x},${pt.y}],"pressure":${pt.pressure}}""")
    }

    /** Rnote's `Style` enum is externally tagged with lowercase variant names (e.g. "smooth"). */
    private fun StringBuilder.appendSmoothStyle(
        color: RnoteNativeColor,
        strokeWidth: Float,
        pressureCurve: PressureCurve = PressureCurve.DEFAULT,
        // Transparent for a brush stroke, which has nothing to fill; a shape passes its
        // own, which used to be written away and left desktop's filled shapes hollow.
        fillColor: RnoteNativeColor = RnoteNativeColor.TRANSPARENT
    ) {
        append("""{"smooth":{""")
        append(""""stroke_color":${color.toJson()},""")
        append(""""stroke_width":$strokeWidth,""")
        append(""""fill_color":${fillColor.toJson()},""")
        // Hard-coding "linear" here made every Marker stroke taper with pressure in
        // desktop Rnote, which defines its Marker brush as PressureCurve::Const.
        append(""""pressure_curve":"${pressureCurve.apiName}",""")
        append(""""line_style":"solid",""")
        append(""""line_cap":"straight"""")
        append("}}")
    }

    // ── TextElement ───────────────────────────────────────────────────────────

    /** `{"<variant>": <the element as read>}` — see the `raw` fields on the model. */
    private fun StringBuilder.appendRaw(variant: String, raw: com.google.gson.JsonElement) {
        append("{\"").append(variant).append("\":")
        append(raw.toString())
        append("}")
    }

    private fun StringBuilder.appendTextElement(el: NativeTextElement) {
        el.raw?.let { appendRaw("textstroke", it); return }
        val tf = el.transform
        append("""{"textstroke":{""")
        append(""""text":${jsonString(el.text)},""")
        append(""""transform":""")
        appendAffine(tf)
        append(",")
        append(""""text_style":{""")
        append(""""font_family":${jsonString(el.fontFamily)},""")
        append(""""font_size":${el.fontSize},""")
        append(""""color":${el.color.toJson()}""")
        // Three: text_style, textstroke, and the element object. The fourth that used to
        // be here made every file holding a text box invalid JSON, which nothing caught
        // because nothing round-tripped a text element until now.
        append("""}}}""")
    }

    // ── BitmapElement ─────────────────────────────────────────────────────────

    private fun StringBuilder.appendBitmapElement(el: NativeBitmapElement) {
        el.raw?.let { appendRaw("bitmapimage", it); return }
        // Re-encode pixels to PNG Base64
        val bmp = Bitmap.createBitmap(el.bmpWidth, el.bmpHeight, Bitmap.Config.ARGB_8888)
        bmp.setPixels(el.pixels, 0, el.bmpWidth, 0, 0, el.bmpWidth, el.bmpHeight)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val tf = el.transform
        append("""{"bitmapimage":{""")
        append(""""image_data":${jsonString(b64)},""")
        append(""""transform":""")
        appendAffine(tf)
        append(",")
        append(""""bounds":{""")
        append(""""mins":[${el.minX},${el.minY}],"maxs":[${el.maxX},${el.maxY}]""")
        append("}}}")
    }

    // ── VectorImage ───────────────────────────────────────────────────────────

    /** The mirror of `parseVectorImage`; the SVG goes back exactly as it was read. */
    private fun StringBuilder.appendVectorImage(el: NativeVectorImageElement) {
        append("""{"vectorimage":{"svg_data":""")
        append(jsonString(el.svgData))
        append(""","intrinsic_size":[${el.intrinsicWidth},${el.intrinsicHeight}]""")
        append(""","rectangle":{"cuboid":{"half_extents":[${el.halfExtentX},${el.halfExtentY}]},"transform":""")
        appendAffine(el.transform)
        append("}}}")
    }

    // ── ShapeElement ──────────────────────────────────────────────────────────

    /**
     * The mirror of `parseShapeStroke`: lower-case variant names, rect and ellipse
     * carrying their transform. A shape only ever gets here because it was read from a
     * file under the same name, so the two stay in step by construction.
     */
    private fun StringBuilder.appendShapeElement(el: NativeShapeElement) {
        el.raw?.let { appendRaw("shapestroke", it); return }
        append("""{"shapestroke":{"shape":{""")
        when (val s = el.shape) {
            is LineShape    -> append(""""line":{"start":[${s.x1},${s.y1}],"end":[${s.x2},${s.y2}]}""")
            is RectShape    -> {
                append(""""rect":{"cuboid":{"half_extents":[${s.halfExtentX},${s.halfExtentY}]},"transform":""")
                appendAffine(s.transform)
                append("}")
            }
            is EllipseShape -> {
                append(""""ellipse":{"radii":[${s.radiusX},${s.radiusY}],"transform":""")
                appendAffine(s.transform)
                append("}")
            }
            // Only ever read from a file, so it always has `raw` and never gets here; a
            // polyline through its on-curve points is the closest fallback there is.
            is PathShape -> {
                val pts = s.ops.mapNotNull {
                    when (it) {
                        is PathOp.MoveTo -> it.x to it.y
                        is PathOp.LineTo -> it.x to it.y
                        is PathOp.QuadTo -> it.x to it.y
                        is PathOp.CubicTo -> it.x to it.y
                        PathOp.Close -> null
                    }
                }
                val first = pts.firstOrNull() ?: (0f to 0f)
                append(""""polyline":{"start":[${first.first},${first.second}],"path":[""")
                append(pts.drop(1).joinToString(",") { "[${it.first},${it.second}]" })
                append("]}")
            }
        }
        append("""},"style":""")
        val rough = el.rough
        if (rough != null) appendRoughStyle(el.color, el.strokeWidth, el.fillColor, rough)
        else appendSmoothStyle(el.color, el.strokeWidth, fillColor = el.fillColor)
        append("}}")
    }

    /** Rnote's `RoughOptions`, with the names and in the order it writes them. */
    private fun StringBuilder.appendRoughStyle(
        color: RnoteNativeColor,
        strokeWidth: Float,
        fillColor: RnoteNativeColor,
        rough: RoughStyle
    ) {
        append("""{"rough":{""")
        append(""""stroke_color":${color.toJson()},""")
        append(""""stroke_width":$strokeWidth,""")
        append(""""fill_color":${fillColor.toJson()},""")
        append(""""fill_style":"${rough.fillStyle.apiName}",""")
        append(""""hachure_angle":${rough.hachureAngle},""")
        append(""""seed":${TexturedStyle.seedJson(rough.seed)}""")
        append("}}")
    }

    /**
     * Rnote's transform: a column-major 3x3 as nine floats. Ours is the six that can
     * differ, so the third column and the bottom row are filled back in here.
     */
    private fun StringBuilder.appendAffine(t: FloatArray) {
        append("""{"affine":[${t[0]},${t[1]},0.0,${t[2]},${t[3]},0.0,${t[4]},${t[5]},1.0]}""")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun RnoteNativeColor.toJson() =
        """{"r":$r,"g":$g,"b":$b,"a":$a}"""

    private fun NativePatternType.toApiString() = when (this) {
        NativePatternType.GRID     -> "grid"
        NativePatternType.RULED    -> "ruled"
        NativePatternType.DOTS     -> "dots"
        NativePatternType.ISO_GRID -> "isometric_grid"
        NativePatternType.ISO_DOTS -> "isometric_dots"
        NativePatternType.BLANK    -> "blank"
    }

    /** Escapes a string for safe JSON embedding. */
    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"'  -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u${c.code.toString(16).padStart(4,'0')}")
                        else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
