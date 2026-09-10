package io.github.kjly.brna.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.LineShape
import io.github.kjly.brna.model.NativeBackgroundConfig
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativePatternType
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeStrokePoint
import io.github.kjly.brna.model.PressureCurve
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RnoteNativeDocument
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.math.sqrt

/**
 * Streaming parser for .rnote files (GZIP-compressed JSON).
 *
 * Never loads the full JSON string into memory — uses Gson's [JsonReader]
 * token-by-token streaming API over a [GZIPInputStream].
 *
 * Ported and adapted from Intranox/rnoteviewer-android (RnoteParser.kt).
 */
object RnoteNativeParser {

    // ── Entry points ──────────────────────────────────────────────────────────

    fun parse(context: Context, uri: Uri): RnoteNativeDocument =
        context.contentResolver.openInputStream(uri)!!.use { parse(it) }

    fun parse(inputStream: InputStream): RnoteNativeDocument {
        val reader = JsonReader(InputStreamReader(GZIPInputStream(inputStream), Charsets.UTF_8))
        reader.isLenient = true
        return parseRoot(reader)
    }

    // ── Internal holder types ─────────────────────────────────────────────────

    private data class FormatConfig(
        val width: Float = 793.7f,
        val height: Float = 1122.5f,
        val borderColor: RnoteNativeColor = RnoteNativeColor(0.8706f, 0.8667f, 0.851f, 1f),
        val showBorders: Boolean = true,
        val showOriginIndicator: Boolean = true
    )
    private data class BgCfg(
        val color: RnoteNativeColor = RnoteNativeColor.WHITE,
        val pattern: NativePatternType = NativePatternType.DOTS,
        val patternW: Float = 21f, val patternH: Float = 21f,
        val patternColor: RnoteNativeColor = RnoteNativeColor(0.8f, 0.9f, 1f, 1f)
    )
    private data class ParsedDocResult(
        val format: FormatConfig = FormatConfig(),
        val bg: BgCfg = BgCfg(),
        val originX: Float = 0f,
        val originY: Float = 0f,
        val totalWidth: Float = 0f,
        val totalHeight: Float = 0f,
        val layout: String = ""
    )

    // ── Root ──────────────────────────────────────────────────────────────────

    private fun parseRoot(reader: JsonReader): RnoteNativeDocument {
        var docResult = ParsedDocResult()
        val rawElements = mutableListOf<NativeCanvasElement?>()
        val chronoOrder = mutableListOf<ChronoEntry>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "engine_snapshot" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "document"          -> docResult = parseDocument(reader)
                                        "stroke_components" -> parseStrokeComponents(reader, rawElements)
                                        "chrono_components" -> parseChronoComponents(reader, chronoOrder)
                                        else                -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val elements = buildOrderedElements(rawElements, chronoOrder)
        return RnoteNativeDocument(
            pageWidth   = docResult.format.width,
            pageHeight  = docResult.format.height,
            background  = NativeBackgroundConfig(docResult.bg.color, docResult.bg.pattern, docResult.bg.patternW, docResult.bg.patternH, docResult.bg.patternColor),
            elements    = elements,
            layout      = docResult.layout,
            // x and y are meaningful at zero and routinely negative, so they pass straight
            // through; a missing width/height falls back to the page format.
            originX     = docResult.originX,
            originY     = docResult.originY,
            totalWidth  = if (docResult.totalWidth > 0f) docResult.totalWidth else docResult.format.width,
            totalHeight = if (docResult.totalHeight > 0f) docResult.totalHeight else docResult.format.height,
            borderColor = docResult.format.borderColor,
            showBorders = docResult.format.showBorders,
            showOriginIndicator = docResult.format.showOriginIndicator
        )
    }

    // ── Document block ────────────────────────────────────────────────────────

    private fun parseDocument(reader: JsonReader): ParsedDocResult {
        var format = FormatConfig(); var bg = BgCfg(); var layout = ""
        var x = 0f; var y = 0f; var w = 0f; var h = 0f
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "config" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "format"     -> format = parseFormatConfig(reader)
                            "background" -> bg     = parseBgConfig(reader)
                            "layout"     -> layout = reader.nextString()
                            else         -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "x"      -> x = reader.nextDouble().toFloat()
                "y"      -> y = reader.nextDouble().toFloat()
                "width"  -> w = reader.nextDouble().toFloat()
                "height" -> h = reader.nextDouble().toFloat()
                else     -> reader.skipValue()
            }
        }
        reader.endObject()
        return ParsedDocResult(format, bg, x, y, w, h, layout)
    }

    private fun parseFormatConfig(reader: JsonReader): FormatConfig {
        var w = 793.7f; var h = 1122.5f
        var borderColor = RnoteNativeColor(0.8706f, 0.8667f, 0.851f, 1f)
        var showBorders = true
        var showOrigin = true
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "width"  -> w = reader.nextDouble().toFloat()
                "height" -> h = reader.nextDouble().toFloat()
                "border_color" -> borderColor = parseColor(reader)
                "show_borders" -> showBorders = reader.nextBoolean()
                "show_origin_indicator" -> showOrigin = reader.nextBoolean()
                else     -> reader.skipValue()
            }
        }
        reader.endObject()
        return FormatConfig(w, h, borderColor, showBorders, showOrigin)
    }

    private fun parseBgConfig(reader: JsonReader): BgCfg {
        var color = RnoteNativeColor.WHITE
        var pattern = NativePatternType.DOTS
        var pw = 21f; var ph = 21f
        var pc = RnoteNativeColor(0.8f, 0.9f, 1f, 1f)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "color"         -> color   = parseColor(reader)
                "pattern"       -> pattern = parsePatternType(reader.nextString())
                "pattern_size"  -> {
                    reader.beginArray()
                    pw = reader.nextDouble().toFloat()
                    ph = reader.nextDouble().toFloat()
                    reader.endArray()
                }
                "pattern_color" -> pc      = parseColor(reader)
                else            -> reader.skipValue()
            }
        }
        reader.endObject()
        return BgCfg(color, pattern, pw, ph, pc)
    }

    private fun parsePatternType(s: String) = when (s.lowercase().trim()) {
        "grid"           -> NativePatternType.GRID
        "ruled", "lines" -> NativePatternType.RULED
        "dots"           -> NativePatternType.DOTS
        "isometric_grid" -> NativePatternType.ISO_GRID
        "isometric_dots" -> NativePatternType.ISO_DOTS
        else             -> NativePatternType.BLANK
    }

    // ── stroke_components ─────────────────────────────────────────────────────

    private fun parseStrokeComponents(reader: JsonReader, out: MutableList<NativeCanvasElement?>) {
        reader.beginArray()
        while (reader.hasNext()) out.add(parseOneComponent(reader))
        reader.endArray()
    }

    private fun parseOneComponent(reader: JsonReader): NativeCanvasElement? {
        var element: NativeCanvasElement? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "value" -> element = if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull(); null
                } else parseElementValue(reader)
                else    -> reader.skipValue()
            }
        }
        reader.endObject()
        return element
    }

    private fun parseElementValue(reader: JsonReader): NativeCanvasElement? {
        var element: NativeCanvasElement? = null
        reader.beginObject()
        while (reader.hasNext()) {
            element = when (reader.nextName()) {
                "brushstroke" -> parseBrushStroke(reader)
                "textstroke"  -> parseTextStroke(reader)
                "bitmapimage" -> parseBitmapImage(reader)
                "shapestroke" -> parseShapeStroke(reader)
                else          -> { reader.skipValue(); element }
            }
        }
        reader.endObject()
        return element
    }

    // ── BrushStroke ───────────────────────────────────────────────────────────

    private fun parseBrushStroke(reader: JsonReader): NativeBrushStroke? {
        val pts = mutableListOf<NativeStrokePoint>()
        var color = RnoteNativeColor.BLACK
        var width = 2f
        var isHighlighter = false
        var pressureCurve = PressureCurve.DEFAULT

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "path"   -> pts.addAll(parsePath(reader))
                "style"  -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName().lowercase()) {
                            "smooth", "textured" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "stroke_color" -> color = parseColor(reader)
                                        "stroke_width" -> width = reader.nextDouble().toFloat()
                                        // Without this the stroke's painted width is
                                        // unknowable — see [PressureCurve].
                                        "pressure_curve" ->
                                            pressureCurve = PressureCurve.fromApiName(reader.nextString())
                                        else           -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "brush"  -> {
                    // Detect highlighter by brush type name
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "BrushStyle" -> {
                                val style = reader.nextString()
                                isHighlighter = style.contains("Highlighter", ignoreCase = true)
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (pts.isEmpty()) return null
        val minX = pts.minOf { it.x }; val minY = pts.minOf { it.y }
        val maxX = pts.maxOf { it.x }; val maxY = pts.maxOf { it.y }
        return NativeBrushStroke(pts, width, color, isHighlighter, minX, minY, maxX, maxY, pressureCurve)
    }

    private fun parsePath(reader: JsonReader): List<NativeStrokePoint> {
        val pts = mutableListOf<NativeStrokePoint>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                // v0.14+ format: {"start": {pos,pressure}, "segments": [{"lineto":{"end":{pos,pressure}}}]}
                "start" -> {
                    val pt = parsePathPoint(reader)
                    pts.add(pt)
                }
                "segments" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            // Every `PenPathSegment` variant — `lineto`, `quadbezto`,
                            // `cubbezto` — is an object carrying the `end` element it
                            // draws to, so the variant name is not worth matching on:
                            // naming them one by one is how `cubbezto` came to be
                            // skipped, which reduced every curve a desktop pen drew to
                            // the straight line between its two endpoints.
                            reader.nextName()
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        // The control points are Rnote's own smoothing of
                                        // the input; `end` is the element the pen actually
                                        // reported, which is what this app draws through.
                                        "end" -> pts.add(parsePathPoint(reader))
                                        else  -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    reader.endArray()
                }
                // Old format: {"elements": [{pos, pressure}]}
                "elements" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        pts.add(parsePathPoint(reader))
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return pts
    }

    /** Parses a single path point object: {"pos": [x, y], "pressure": p} */
    private fun parsePathPoint(reader: JsonReader): NativeStrokePoint {
        var x = 0f; var y = 0f; var pressure = 1f
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "pos"      -> { reader.beginArray(); x = reader.nextDouble().toFloat(); y = reader.nextDouble().toFloat(); reader.endArray() }
                "pressure" -> pressure = reader.nextDouble().toFloat()
                else       -> reader.skipValue()
            }
        }
        reader.endObject()
        return NativeStrokePoint(x, y, pressure)
    }

    // ── TextStroke ────────────────────────────────────────────────────────────

    private fun parseTextStroke(reader: JsonReader): NativeTextElement? {
        var text = ""; var family = "sans-serif"; var size = 14f
        var color = RnoteNativeColor.BLACK
        val transform = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        var minX = 0f; var minY = 0f; var maxX = 0f; var maxY = 0f

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "text"      -> text   = reader.nextString()
                "transform" -> parseTransformInto(reader, transform)
                "text_style" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "font_family" -> family = reader.nextString()
                            "font_size"   -> size   = reader.nextDouble().toFloat()
                            "color"       -> color  = parseColor(reader)
                            else          -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "bounds" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "mins" -> { reader.beginArray(); minX = reader.nextDouble().toFloat(); minY = reader.nextDouble().toFloat(); reader.endArray() }
                            "maxs" -> { reader.beginArray(); maxX = reader.nextDouble().toFloat(); maxY = reader.nextDouble().toFloat(); reader.endArray() }
                            else   -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (text.isBlank()) return null
        // Fallback bounds from transform if no explicit bounds
        if (maxX == 0f) { minX = transform[4]; minY = transform[5]; maxX = minX + size * 10; maxY = minY + size }
        return NativeTextElement(text, family, size, color, transform, minX, minY, maxX, maxY)
    }

    // ── BitmapImage ───────────────────────────────────────────────────────────

    private fun parseBitmapImage(reader: JsonReader): NativeBitmapElement? {
        var imageBase64 = ""
        val transform   = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        var minX = 0f; var minY = 0f; var maxX = 100f; var maxY = 100f

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "image_data" -> imageBase64 = reader.nextString()
                "transform"  -> parseTransformInto(reader, transform)
                "bounds" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "mins" -> { reader.beginArray(); minX = reader.nextDouble().toFloat(); minY = reader.nextDouble().toFloat(); reader.endArray() }
                            "maxs" -> { reader.beginArray(); maxX = reader.nextDouble().toFloat(); maxY = reader.nextDouble().toFloat(); reader.endArray() }
                            else   -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (imageBase64.isBlank()) return null
        return try {
            val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
            val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            bmp.recycle()
            NativeBitmapElement(pixels, bmp.width, bmp.height, transform, minX, minY, maxX, maxY)
        } catch (e: Exception) { null }
    }

    // ── ShapeStroke ───────────────────────────────────────────────────────────

    /**
     * Rnote 0.14 names its shape variants and its style in lower case - `line`, `rect`,
     * `ellipse`, `smooth` - and gives rect and ellipse a transform rather than a corner.
     * This matched `"Line"`, `"Rectangle"`, `"Smooth"` and so on instead, which no file
     * from that version contains, so every shape fell through to a null and was dropped:
     * a desktop document's shapes vanished on the first save from here, filled or not.
     * The capitalised forms stay as a legacy branch for whatever older files still have.
     */
    private fun parseShapeStroke(reader: JsonReader): NativeCanvasElement? {
        var shape: io.github.kjly.brna.model.NativeShapeKind? = null
        // A legacy freehand "shape" is a path with a width and a colour, which is a brush
        // stroke in everything but name — and unlike a guessed-at polyline variant, a
        // brush stroke is something this app knows how to write back.
        var freehandPoints: List<NativeStrokePoint>? = null
        var color = RnoteNativeColor.BLACK
        var fill = RnoteNativeColor.TRANSPARENT
        var width = 2f

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "shape" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "line", "Line" -> shape = parseLineShape(reader)
                            "rect"         -> shape = parseRectShape(reader)
                            "ellipse"      -> shape = parseEllipseShape(reader)
                            // Legacy corner-and-size forms, re-centred into the
                            // half-extents and radii the model now carries.
                            "Rectangle"    -> shape = parseLegacyRectShape(reader)
                            "Ellipse"      -> shape = parseLegacyEllipseShape(reader)
                            "FreehandPen", "Freehand" -> freehandPoints = parsePath(reader)
                            else           -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "style" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "smooth", "rough", "Smooth", "Rough" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "stroke_color" -> color = parseColor(reader)
                                        "fill_color"   -> fill  = parseColor(reader)
                                        "stroke_width" -> width = reader.nextDouble().toFloat()
                                        else           -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        freehandPoints?.let { pts ->
            if (pts.isEmpty()) return null
            return NativeBrushStroke(
                pts, width, color, false,
                pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y }
            )
        }
        val s = shape ?: return null
        val (mnX, mnY, mxX, mxY) = boundsForShape(s)
        return NativeShapeElement(s, color, width, mnX, mnY, mxX, mxY, fill)
    }

    private fun parseLineShape(reader: JsonReader): LineShape {
        var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "start" -> { reader.beginArray(); x1 = reader.nextDouble().toFloat(); y1 = reader.nextDouble().toFloat(); reader.endArray() }
                "end"   -> { reader.beginArray(); x2 = reader.nextDouble().toFloat(); y2 = reader.nextDouble().toFloat(); reader.endArray() }
                else    -> reader.skipValue()
            }
        }
        reader.endObject()
        return LineShape(x1, y1, x2, y2)
    }

    /** `{"cuboid":{"half_extents":[hx,hy]},"transform":{"affine":[..]}}` */
    private fun parseRectShape(reader: JsonReader): RectShape {
        var hx = 0f; var hy = 0f
        val transform = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "cuboid" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "half_extents" -> { reader.beginArray(); hx = reader.nextDouble().toFloat(); hy = reader.nextDouble().toFloat(); reader.endArray() }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "transform" -> parseTransformInto(reader, transform)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return RectShape(hx, hy, transform)
    }

    /** `{"radii":[rx,ry],"transform":{"affine":[..]}}` */
    private fun parseEllipseShape(reader: JsonReader): EllipseShape {
        var rx = 0f; var ry = 0f
        val transform = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "radii"     -> { reader.beginArray(); rx = reader.nextDouble().toFloat(); ry = reader.nextDouble().toFloat(); reader.endArray() }
                "transform" -> parseTransformInto(reader, transform)
                else        -> reader.skipValue()
            }
        }
        reader.endObject()
        return EllipseShape(rx, ry, transform)
    }

    private fun parseLegacyRectShape(reader: JsonReader): RectShape {
        var x = 0f; var y = 0f; var w = 0f; var h = 0f
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "top_left" -> { reader.beginArray(); x = reader.nextDouble().toFloat(); y = reader.nextDouble().toFloat(); reader.endArray() }
                "size"     -> { reader.beginArray(); w = reader.nextDouble().toFloat(); h = reader.nextDouble().toFloat(); reader.endArray() }
                else       -> reader.skipValue()
            }
        }
        reader.endObject()
        return RectShape(w / 2f, h / 2f, floatArrayOf(1f, 0f, 0f, 1f, x + w / 2f, y + h / 2f))
    }

    private fun parseLegacyEllipseShape(reader: JsonReader): EllipseShape {
        var cx = 0f; var cy = 0f; var rx = 0f; var ry = 0f
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "center" -> { reader.beginArray(); cx = reader.nextDouble().toFloat(); cy = reader.nextDouble().toFloat(); reader.endArray() }
                "radii"  -> { reader.beginArray(); rx = reader.nextDouble().toFloat(); ry = reader.nextDouble().toFloat(); reader.endArray() }
                else     -> reader.skipValue()
            }
        }
        reader.endObject()
        return EllipseShape(rx, ry, floatArrayOf(1f, 0f, 0f, 1f, cx, cy))
    }

    /**
     * Axis-aligned bounds of a shape with its transform applied. The document extent is
     * derived from these, so a rotated rect has to report the box it actually occupies
     * rather than the one it would occupy sitting square.
     */
    private fun boundsForShape(s: io.github.kjly.brna.model.NativeShapeKind): FloatArray = when (s) {
        is LineShape -> floatArrayOf(minOf(s.x1, s.x2), minOf(s.y1, s.y2), maxOf(s.x1, s.x2), maxOf(s.y1, s.y2))
        is RectShape -> {
            val t = s.transform
            var mnX = Float.MAX_VALUE; var mnY = Float.MAX_VALUE
            var mxX = -Float.MAX_VALUE; var mxY = -Float.MAX_VALUE
            for (sx in intArrayOf(-1, 1)) {
                for (sy in intArrayOf(-1, 1)) {
                    val cx = sx * s.halfExtentX
                    val cy = sy * s.halfExtentY
                    val x = t[0] * cx + t[2] * cy + t[4]
                    val y = t[1] * cx + t[3] * cy + t[5]
                    if (x < mnX) mnX = x
                    if (x > mxX) mxX = x
                    if (y < mnY) mnY = y
                    if (y > mxY) mxY = y
                }
            }
            floatArrayOf(mnX, mnY, mxX, mxY)
        }
        is EllipseShape -> {
            // Exact half-extent of a transformed ellipse: the length of the image of the
            // radius vector, which is inside the corner of the transformed bounding box.
            val t = s.transform
            val hw = sqrt(sq(t[0] * s.radiusX) + sq(t[2] * s.radiusY))
            val hh = sqrt(sq(t[1] * s.radiusX) + sq(t[3] * s.radiusY))
            floatArrayOf(t[4] - hw, t[5] - hh, t[4] + hw, t[5] + hh)
        }
    }

    private fun sq(v: Float): Float = v * v

    // ── chrono_components ─────────────────────────────────────────────────────

    /** Chrono-order entry: which stroke_components slot it points to, and whether its layer is "highlighter". */
    private data class ChronoEntry(val strokeIndex: Int, val isHighlighter: Boolean)

    private fun parseChronoComponents(reader: JsonReader, out: MutableList<ChronoEntry>) {
        reader.beginArray()
        while (reader.hasNext()) {
            // Each item: {"value": {"t": index, "layer": "highlighter" | {"user_layer": N}} | null, "version": N}
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "value" -> {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                        } else {
                            var t = -1
                            var isHighlighter = false
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    // v0.14: {"t": index, "layer": ...}
                                    "t"     -> t = reader.nextInt()
                                    "layer" -> isHighlighter = parseLayerIsHighlighter(reader)
                                    // Old: {"stroke_key": {"index": N}}
                                    "stroke_key" -> t = parseStrokeKey(reader)
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            if (t >= 0) out.add(ChronoEntry(t, isHighlighter))
                        }
                    }
                    // Old format without value wrapper
                    "stroke_key" -> out.add(ChronoEntry(parseStrokeKey(reader), false))
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endArray()
    }

    /** `StrokeLayer` is externally tagged: unit variants (e.g. `Highlighter`) serialize as a bare
     * string "highlighter"; tuple variants (e.g. `UserLayer(0)`) serialize as `{"user_layer": 0}`. */
    private fun parseLayerIsHighlighter(reader: JsonReader): Boolean {
        return if (reader.peek() == JsonToken.STRING) {
            reader.nextString().equals("highlighter", ignoreCase = true)
        } else {
            var highlighter = false
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName().equals("highlighter", ignoreCase = true)) highlighter = true
                reader.skipValue()
            }
            reader.endObject()
            highlighter
        }
    }

    private fun parseStrokeKey(reader: JsonReader): Int {
        var idx = -1
        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "index" -> idx = reader.nextInt()
                    else    -> reader.skipValue()
                }
            }
            reader.endObject()
        } else {
            idx = reader.nextInt()
        }
        return idx
    }

    private fun buildOrderedElements(
        raw: List<NativeCanvasElement?>,
        order: List<ChronoEntry>
    ): List<NativeCanvasElement> {
        if (order.isEmpty()) return raw.filterNotNull()
        return order.mapNotNull { entry ->
            val el = raw.getOrNull(entry.strokeIndex) ?: return@mapNotNull null
            if (entry.isHighlighter && el is NativeBrushStroke && !el.isHighlighter) {
                el.copy(isHighlighter = true)
            } else el
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun parseColor(reader: JsonReader): RnoteNativeColor {
        var r = 0f; var g = 0f; var b = 0f; var a = 1f
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "r" -> r = reader.nextDouble().toFloat()
                "g" -> g = reader.nextDouble().toFloat()
                "b" -> b = reader.nextDouble().toFloat()
                "a" -> a = reader.nextDouble().toFloat()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return RnoteNativeColor(r, g, b, a)
    }

    /**
     * Reads a 2D affine transform (column-major 2×3) into [out]:
     * [a, b, c, d, tx, ty]
     */
    private fun parseTransformInto(reader: JsonReader, out: FloatArray) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                // What Rnote 0.14 actually writes: a column-major 3x3 as nine floats,
                // [a,b,0, c,d,0, tx,ty,1]. Only "matrix" was read before, so every
                // transform fell back to identity and a desktop text box or image came
                // in unrotated, unscaled, and at the origin.
                "affine" -> {
                    val m = FloatArray(9)
                    reader.beginArray()
                    for (i in 0..8) { if (reader.hasNext()) m[i] = reader.nextDouble().toFloat() }
                    reader.endArray()
                    out[0] = m[0]; out[1] = m[1]   // first column
                    out[2] = m[3]; out[3] = m[4]   // second column
                    out[4] = m[6]; out[5] = m[7]   // translation
                }
                "matrix" -> {
                    reader.beginArray()
                    for (i in 0..5) { if (reader.hasNext()) out[i] = reader.nextDouble().toFloat() }
                    reader.endArray()
                }
                // flat inline [a,b,c,d,e,f]
                "a" -> out[0] = reader.nextDouble().toFloat()
                "b" -> out[1] = reader.nextDouble().toFloat()
                "c" -> out[2] = reader.nextDouble().toFloat()
                "d" -> out[3] = reader.nextDouble().toFloat()
                "e" -> out[4] = reader.nextDouble().toFloat()
                "f" -> out[5] = reader.nextDouble().toFloat()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }
}
