package io.github.kjly.brna.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.internal.bind.JsonTreeReader
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
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.PathOp
import io.github.kjly.brna.model.PathShape
import io.github.kjly.brna.model.RangedTextAttr
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RnoteNativeDocument
import io.github.kjly.brna.model.RoughFillStyle
import io.github.kjly.brna.model.RoughStyle
import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.model.TextAttr
import io.github.kjly.brna.model.TexturedDistribution
import io.github.kjly.brna.model.TexturedStyle
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

    /**
     * One stroke_components value, e.g. `{"shapestroke": {...}}`, read exactly as it would
     * be from a file — which is how shapes drawn in this app are built.
     */
    fun parseElementJson(json: String): NativeCanvasElement? {
        val reader = JsonReader(java.io.StringReader(json))
        reader.isLenient = true
        return parseElementValue(reader)
    }

    /**
     * As [parseElementJson], for JSON already in memory. No detour through a string, which
     * for an image would be tens of megabytes written out and read back in.
     */
    fun parseElementTree(tree: JsonElement): NativeCanvasElement? = parseElementValue(JsonTreeReader(tree))

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
                // A brush stroke keeps the text it was read from too, written back while
                // it is unchanged; see RnoteStrokeSource.
                "brushstroke" -> fromTree(reader) { r, tree -> parseBrushStroke(r)?.copy(raw = tree.toString()) }
                // Text, images and shapes keep the element exactly as read, and a save
                // writes that back; editing changes it along with the fields (NativeEditing).
                "textstroke"  -> fromTree(reader) { r, tree -> parseTextStroke(r)?.copy(raw = tree) }
                "bitmapimage" -> fromTree(reader) { r, tree -> parseBitmapImage(r)?.copy(raw = tree) }
                "shapestroke" -> fromTree(reader) { r, tree ->
                    when (val el = parseShapeStroke(r)) {
                        is NativeShapeElement -> el.copy(raw = tree)
                        // A legacy freehand "shape" becomes a brush stroke, which this
                        // app edits and writes out as one.
                        else -> el
                    }
                }
                "vectorimage" -> parseVectorImage(reader)
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
        var textured: TexturedStyle? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "path"   -> pts.addAll(parsePath(reader))
                "style"  -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (val styleName = reader.nextName().lowercase()) {
                            "smooth", "textured" -> {
                                // Rnote's TexturedOptions: its dots come from the seed, so
                                // the seed has to come through, and go back out, as it was.
                                var seed: Long? = null
                                var density = TexturedStyle.DENSITY_DEFAULT
                                var distribution = TexturedDistribution.DEFAULT
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "stroke_color" -> color = parseColor(reader)
                                        "stroke_width" -> width = reader.nextDouble().toFloat()
                                        // Without this the stroke's painted width is
                                        // unknowable — see [PressureCurve].
                                        "pressure_curve" ->
                                            pressureCurve = PressureCurve.fromApiName(reader.nextString())
                                        "seed" -> seed = if (reader.peek() == JsonToken.NULL) {
                                            reader.nextNull()
                                            null
                                        } else {
                                            // A u64: past Long's range, so read as the text it is.
                                            TexturedStyle.seedFromJson(reader.nextString())
                                        }
                                        "density" -> density = reader.nextDouble()
                                        "distribution" ->
                                            distribution = TexturedDistribution.fromApiName(reader.nextString())
                                        else           -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (styleName == "textured") textured = TexturedStyle(seed, density, distribution)
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
        return NativeBrushStroke(pts, width, color, isHighlighter, minX, minY, maxX, maxY, pressureCurve, textured)
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
                            // `cubbezto`, and their older names `line`, `quadbez`, `cubbez` —
                            // is an object carrying the `end` element it draws to; the
                            // curves carry their control points too, which a stroke from
                            // Rnote's "Curved" pen path is drawn through.
                            reader.nextName()
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                var end: NativeStrokePoint? = null
                                var cp: FloatArray? = null
                                var cp1: FloatArray? = null
                                var cp2: FloatArray? = null
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "end" -> end = parsePathPoint(reader)
                                        "cp"  -> cp = parseVector(reader)
                                        "cp1" -> cp1 = parseVector(reader)
                                        "cp2" -> cp2 = parseVector(reader)
                                        else  -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                val curve = when {
                                    cp1 != null && cp2 != null -> SegmentCurve.Cubic(cp1[0], cp1[1], cp2[0], cp2[1])
                                    cp != null -> SegmentCurve.Quad(cp[0], cp[1])
                                    else -> null
                                }
                                end?.let { pts.add(it.copy(curve = curve)) }
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

    /** A control point: `[x, y]`. */
    private fun parseVector(reader: JsonReader): FloatArray {
        reader.beginArray()
        val x = reader.nextDouble().toFloat()
        val y = reader.nextDouble().toFloat()
        while (reader.hasNext()) reader.skipValue()
        reader.endArray()
        return floatArrayOf(x, y)
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

    /**
     * Reads the next value as a tree, then parses it from that tree. The tree is what a
     * save writes back; reading it through [JsonTreeReader] rather than re-serialising it
     * means a large string (an embedded image's pixels) exists once, not three times.
     */
    private inline fun <T> fromTree(reader: JsonReader, parse: (JsonReader, JsonElement) -> T): T {
        val tree = JsonParser.parseReader(reader)
        return parse(JsonTreeReader(tree), tree)
    }

    /** Axis-aligned bounds of the w × h box at the origin with [t] applied. */
    private fun transformedBoxBounds(t: FloatArray, w: Float, h: Float): FloatArray {
        var mnX = Float.MAX_VALUE; var mnY = Float.MAX_VALUE
        var mxX = -Float.MAX_VALUE; var mxY = -Float.MAX_VALUE
        for (cx in floatArrayOf(0f, w)) {
            for (cy in floatArrayOf(0f, h)) {
                val x = t[0] * cx + t[2] * cy + t[4]
                val y = t[1] * cx + t[3] * cy + t[5]
                if (x < mnX) mnX = x
                if (x > mxX) mxX = x
                if (y < mnY) mnY = y
                if (y > mxY) mxY = y
            }
        }
        return floatArrayOf(mnX, mnY, mxX, mxY)
    }

    // ── TextStroke ────────────────────────────────────────────────────────────

    private fun parseTextStroke(reader: JsonReader): NativeTextElement? {
        var text = ""; var family = "sans-serif"; var size = 14f
        var color = RnoteNativeColor.BLACK
        var maxWidth: Float? = null
        var weight = 500
        var italic = false
        var alignment = "start"
        var ranges = emptyList<RangedTextAttr>()
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
                            "max_width"   -> maxWidth =
                                if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null }
                                else reader.nextDouble().toFloat()
                            "font_weight" -> weight = reader.nextInt()
                            "font_style"  -> italic = reader.nextString().equals("italic", ignoreCase = true)
                            "alignment"   -> alignment = reader.nextString().lowercase()
                            "ranged_text_attributes" -> ranges = parseRangedTextAttributes(reader)
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
        // Rnote 0.14 stores no bounds for text; estimate the laid-out box instead. It only
        // feeds the document extent and hit-testing, so an approximation is enough.
        if (maxX == 0f) {
            val lines = text.split('\n')
            val wrap = maxWidth
            // A wrap width is only an upper limit: short text stays as narrow as it is, so
            // tapping beside it with the typewriter starts a new box instead of editing it.
            val longest = lines.maxOf { it.length } * size * 0.6f
            val w = if (wrap != null && wrap > 0f) minOf(wrap, longest) else longest
            val lineCount = if (wrap != null && wrap > 0f) {
                lines.sumOf { maxOf(1, kotlin.math.ceil(it.length * size * 0.6f / wrap).toInt()) }
            } else lines.size
            val b = transformedBoxBounds(transform, w, lineCount * size * 1.25f)
            minX = b[0]; minY = b[1]; maxX = b[2]; maxY = b[3]
        }
        return NativeTextElement(
            text, family, size, color, transform, minX, minY, maxX, maxY,
            maxWidth = maxWidth, fontWeight = weight, italic = italic, alignment = alignment,
            ranges = ranges
        )
    }

    /**
     * Rnote's `ranged_text_attributes`: `[{"range": {"start", "end"}, "attribute": {kind: value}}]`,
     * byte offsets into the text. An attribute of a kind this app doesn't know is skipped;
     * it stays in the element's raw JSON all the same.
     */
    private fun parseRangedTextAttributes(reader: JsonReader): List<RangedTextAttr> {
        val out = mutableListOf<RangedTextAttr>()
        reader.beginArray()
        while (reader.hasNext()) {
            var start = -1
            var end = -1
            var attr: TextAttr? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "range" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "start" -> start = reader.nextInt()
                                "end" -> end = reader.nextInt()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    "attribute" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            attr = when (reader.nextName()) {
                                "font_family" -> TextAttr.Family(reader.nextString())
                                "font_size" -> TextAttr.Size(reader.nextDouble().toFloat())
                                "font_weight" -> TextAttr.Weight(reader.nextInt())
                                "text_color" -> TextAttr.Color(parseColor(reader))
                                "font_style" -> TextAttr.Italic(reader.nextString().equals("italic", ignoreCase = true))
                                "underline" -> TextAttr.Underline(reader.nextBoolean())
                                "strikethrough" -> TextAttr.Strikethrough(reader.nextBoolean())
                                else -> { reader.skipValue(); null }
                            }
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            val a = attr
            if (a != null && start >= 0 && end > start) out += RangedTextAttr(start, end, a)
        }
        reader.endArray()
        return out
    }

    // ── BitmapImage ───────────────────────────────────────────────────────────

    private fun parseBitmapImage(reader: JsonReader): NativeBitmapElement? {
        var imageBase64 = ""
        val transform   = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        var minX = 0f; var minY = 0f; var maxX = 100f; var maxY = 100f
        // Rnote 0.14: {"image": {"data", "pixel_width", "pixel_height", ...}, "rectangle"}
        var rgba: String? = null
        var pixelW = 0; var pixelH = 0
        var rect: RectShape? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "image" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "data"         -> rgba = reader.nextString()
                            "pixel_width"  -> pixelW = reader.nextInt()
                            "pixel_height" -> pixelH = reader.nextInt()
                            else           -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "rectangle"  -> rect = parseRectShape(reader)
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

        val r = rect
        if (rgba != null && r != null && pixelW > 0 && pixelH > 0) {
            val b = boundsForShape(r)
            return NativeBitmapElement(
                IntArray(0), pixelW, pixelH, r.transform, b[0], b[1], b[2], b[3],
                rgbaBase64 = rgba, rect = r
            )
        }

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

    // ── VectorImage ───────────────────────────────────────────────────────────

    /**
     * `{"svg_data": "...", "intrinsic_size": [w, h],
     *   "rectangle": {"cuboid": {"half_extents": [hx, hy]}, "transform": {"affine": [..]}}}`
     *
     * This is how desktop Rnote stores an imported PDF page. It used to be skipped
     * outright, so a PDF-based note opened empty here and a save wrote it back without
     * its pages.
     */
    private fun parseVectorImage(reader: JsonReader): NativeVectorImageElement? {
        var svg = ""
        var iw = 0f; var ih = 0f
        var rect: RectShape? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "svg_data"       -> svg = reader.nextString()
                "intrinsic_size" -> { reader.beginArray(); iw = reader.nextDouble().toFloat(); ih = reader.nextDouble().toFloat(); reader.endArray() }
                "rectangle"      -> rect = parseRectShape(reader)
                else             -> reader.skipValue()
            }
        }
        reader.endObject()
        val r = rect ?: return null
        if (svg.isEmpty()) return null
        val b = boundsForShape(r)
        return NativeVectorImageElement(
            svgData = svg,
            intrinsicWidth = if (iw > 0f) iw else 2f * r.halfExtentX,
            intrinsicHeight = if (ih > 0f) ih else 2f * r.halfExtentY,
            halfExtentX = r.halfExtentX,
            halfExtentY = r.halfExtentY,
            transform = r.transform,
            layer = "image",
            minX = b[0], minY = b[1], maxX = b[2], maxY = b[3]
        )
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
        var lineStyle = "solid"
        var roundCap = false
        var rough: RoughStyle? = null
        // An arrow's head grows with the stroke width, which is only known once "style"
        // has been read, so its outline is built after the loop.
        var arrow: FloatArray? = null

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
                            "arrow"        -> arrow = parsePointFields(reader, "start", "tip")
                            "quadbez"      -> parsePointFields(reader, "start", "cp", "end").let { p ->
                                shape = PathShape(listOf(
                                    PathOp.MoveTo(p[0], p[1]), PathOp.QuadTo(p[2], p[3], p[4], p[5])
                                ))
                            }
                            "cubbez"       -> parsePointFields(reader, "start", "cp1", "cp2", "end").let { p ->
                                shape = PathShape(listOf(
                                    PathOp.MoveTo(p[0], p[1]),
                                    PathOp.CubicTo(p[2], p[3], p[4], p[5], p[6], p[7])
                                ))
                            }
                            "polyline"     -> shape = parsePolyShape(reader, closed = false)
                            "polygon"      -> shape = parsePolyShape(reader, closed = true)
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
                        when (val styleName = reader.nextName()) {
                            "smooth", "rough", "Smooth", "Rough" -> {
                                val isRough = styleName.equals("rough", ignoreCase = true)
                                var fillStyle = RoughFillStyle.DEFAULT
                                var hachureAngle = RoughStyle.HACHURE_ANGLE_DEFAULT
                                var seed: Long? = null
                                // Rnote's defaults for a field the file leaves out: the
                                // rough style's line is 2.4 wide.
                                if (isRough) width = RoughStyle.STROKE_WIDTH_DEFAULT
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        // A rough shape without a line colour leaves it to
                                        // roughr, whose own is black.
                                        "stroke_color" -> color = if (isRough && reader.peek() == JsonToken.NULL) {
                                            reader.nextNull()
                                            RnoteNativeColor.BLACK
                                        } else {
                                            parseColor(reader)
                                        }
                                        "fill_color"   -> fill  = parseColor(reader)
                                        "stroke_width" -> width = reader.nextDouble().toFloat()
                                        "line_style"   -> lineStyle = reader.nextString().lowercase()
                                        "line_cap"     -> roundCap = reader.nextString().equals("rounded", ignoreCase = true)
                                        "fill_style"   -> fillStyle = RoughFillStyle.fromApiName(reader.nextString())
                                        "hachure_angle" -> hachureAngle = reader.nextDouble()
                                        "seed" -> seed = if (reader.peek() == JsonToken.NULL) {
                                            reader.nextNull()
                                            null
                                        } else {
                                            // A u64: past Long's range, so read as the text it is.
                                            TexturedStyle.seedFromJson(reader.nextString())
                                        }
                                        else           -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                rough = if (isRough) RoughStyle(fillStyle, hachureAngle, seed) else null
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
        arrow?.let { a -> shape = arrowShape(a[0], a[1], a[2], a[3], width) }
        val s = shape ?: return null
        val (mnX, mnY, mxX, mxY) = boundsForShape(s)
        return NativeShapeElement(
            s, color, width, mnX, mnY, mxX, mxY, fill,
            lineStyle = lineStyle, roundCap = roundCap, rough = rough
        )
    }

    /** Reads an object of named [x, y] points, returned flat in the order asked for. */
    private fun parsePointFields(reader: JsonReader, vararg names: String): FloatArray {
        val out = FloatArray(names.size * 2)
        reader.beginObject()
        while (reader.hasNext()) {
            val i = names.indexOf(reader.nextName())
            if (i < 0) { reader.skipValue(); continue }
            reader.beginArray()
            out[2 * i] = reader.nextDouble().toFloat()
            out[2 * i + 1] = reader.nextDouble().toFloat()
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
        }
        reader.endObject()
        return out
    }

    /** `{"start": [x, y], "path": [[x, y], ...]}` — Rnote's polyline and polygon. */
    private fun parsePolyShape(reader: JsonReader, closed: Boolean): PathShape {
        val ops = mutableListOf<PathOp>()
        var start: PathOp.MoveTo? = null
        val rest = mutableListOf<PathOp>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "start" -> {
                    reader.beginArray()
                    start = PathOp.MoveTo(reader.nextDouble().toFloat(), reader.nextDouble().toFloat())
                    while (reader.hasNext()) reader.skipValue()
                    reader.endArray()
                }
                "path" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginArray()
                        rest += PathOp.LineTo(reader.nextDouble().toFloat(), reader.nextDouble().toFloat())
                        while (reader.hasNext()) reader.skipValue()
                        reader.endArray()
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        ops += start ?: PathOp.MoveTo(0f, 0f)
        ops += rest
        if (closed) ops += PathOp.Close
        return PathShape(ops)
    }

    /**
     * Rnote's `Arrow::to_kurbo`: the stem, then the two head lines meeting at the tip at
     * 13/16 π from the stem, each 10 · (1 + 0.18 · stroke width) long.
     */
    private fun arrowShape(sx: Float, sy: Float, tx: Float, ty: Float, strokeWidth: Float): PathShape {
        var dx = tx - sx; var dy = ty - sy
        val len = sqrt(dx * dx + dy * dy)
        if (len == 0f) { dx = 1f; dy = 0f } else { dx /= len; dy /= len }
        val headLen = 10f * (1f + 0.18f * strokeWidth)
        val angle = (13.0 / 16.0 * Math.PI)
        fun rotated(a: Double): Pair<Float, Float> {
            val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
            return Pair((c * dx - s * dy) * headLen + tx, (s * dx + c * dy) * headLen + ty)
        }
        val (lx, ly) = rotated(angle)
        val (rx, ry) = rotated(-angle)
        return PathShape(listOf(
            PathOp.MoveTo(sx, sy), PathOp.LineTo(tx, ty),
            PathOp.MoveTo(lx, ly), PathOp.LineTo(tx, ty), PathOp.LineTo(rx, ry)
        ))
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
        // Control points included: the hull of a curve's control polygon contains the
        // curve, so this is never too small, only sometimes a little generous.
        is PathShape -> {
            var mnX = Float.MAX_VALUE; var mnY = Float.MAX_VALUE
            var mxX = -Float.MAX_VALUE; var mxY = -Float.MAX_VALUE
            fun add(x: Float, y: Float) {
                if (x < mnX) mnX = x
                if (x > mxX) mxX = x
                if (y < mnY) mnY = y
                if (y > mxY) mxY = y
            }
            for (op in s.ops) when (op) {
                is PathOp.MoveTo -> add(op.x, op.y)
                is PathOp.LineTo -> add(op.x, op.y)
                is PathOp.QuadTo -> { add(op.x1, op.y1); add(op.x, op.y) }
                is PathOp.CubicTo -> { add(op.x1, op.y1); add(op.x2, op.y2); add(op.x, op.y) }
                PathOp.Close -> Unit
            }
            if (mnX > mxX) floatArrayOf(0f, 0f, 0f, 0f) else floatArrayOf(mnX, mnY, mxX, mxY)
        }
    }

    private fun sq(v: Float): Float = v * v

    // ── chrono_components ─────────────────────────────────────────────────────

    /**
     * Chrono-order entry for one stroke_components slot.
     *
     * `chrono_components` is a slotmap `SecondaryMap` keyed like `stroke_components`, so
     * the entry at position i belongs to the stroke at position i. Its `t` is *not* an
     * index: it is a timestamp from `chrono_counter` (Rnote's `update_chrono_to_last`).
     * The two only coincide in a file nobody ever erased or reordered anything in, which
     * is why reading `t` as the slot scrambled or dropped strokes in real documents.
     * Draw order is Rnote's `sort_keys_chrono`: by layer first, then by `t`.
     */
    private data class ChronoEntry(
        val strokeIndex: Int,
        val t: Long,
        val layerRank: Long,
        val layerName: String,
        val isHighlighter: Boolean
    )

    private fun parseChronoComponents(reader: JsonReader, out: MutableList<ChronoEntry>) {
        var position = -1
        reader.beginArray()
        while (reader.hasNext()) {
            position++
            // Each item: {"value": {"t": index, "layer": "highlighter" | {"user_layer": N}} | null, "version": N}
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "value" -> {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                        } else {
                            var t = -1L
                            var layer = LayerInfo(USER_LAYER_RANK, "user_layer")
                            var legacyIndex = -1
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    // v0.14: {"t": timestamp, "layer": ...}
                                    "t"     -> t = reader.nextLong()
                                    "layer" -> layer = parseLayer(reader)
                                    // Old: {"stroke_key": {"index": N}}
                                    "stroke_key" -> legacyIndex = parseStrokeKey(reader)
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            val index = if (legacyIndex >= 0) legacyIndex else position
                            out.add(ChronoEntry(
                                strokeIndex = index,
                                t = if (t >= 0) t else index.toLong(),
                                layerRank = layer.rank,
                                layerName = layer.name,
                                isHighlighter = layer.name == "highlighter"
                            ))
                        }
                    }
                    // Old format without value wrapper
                    "stroke_key" -> {
                        val index = parseStrokeKey(reader)
                        out.add(ChronoEntry(index, index.toLong(), USER_LAYER_RANK, "user_layer", false))
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endArray()
    }

    private class LayerInfo(val rank: Long, val name: String)

    /** Rank of `UserLayer(0)`; user layer n ranks n above it. */
    private const val USER_LAYER_RANK = 3L

    /**
     * `StrokeLayer` is externally tagged: unit variants (e.g. `Highlighter`) serialize as a
     * bare string "highlighter"; tuple variants (e.g. `UserLayer(0)`) as `{"user_layer": 0}`.
     * Ranked as Rnote's `Ord for StrokeLayer`: Document < Image < Highlighter < UserLayer(n).
     */
    private fun parseLayer(reader: JsonReader): LayerInfo {
        fun unit(name: String) = when (name.lowercase()) {
            "document"    -> LayerInfo(0L, "document")
            "image"       -> LayerInfo(1L, "image")
            "highlighter" -> LayerInfo(2L, "highlighter")
            else          -> LayerInfo(USER_LAYER_RANK, "user_layer")
        }
        return if (reader.peek() == JsonToken.STRING) {
            unit(reader.nextString())
        } else {
            var info = LayerInfo(USER_LAYER_RANK, "user_layer")
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name.equals("user_layer", ignoreCase = true) || name == "UserLayer") {
                    val n = if (reader.peek() == JsonToken.NUMBER) reader.nextLong() else { reader.skipValue(); 0L }
                    info = LayerInfo(USER_LAYER_RANK + n.coerceAtLeast(0L), "user_layer")
                } else {
                    info = unit(name)
                    reader.skipValue()
                }
            }
            reader.endObject()
            info
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
        val seen = HashSet<Int>()
        val ordered = order
            .filter { seen.add(it.strokeIndex) }
            .sortedWith(compareBy<ChronoEntry>({ it.layerRank }, { it.t }))
            .mapNotNull { entry ->
                val el = raw.getOrNull(entry.strokeIndex) ?: return@mapNotNull null
                when {
                    entry.isHighlighter && el is NativeBrushStroke && !el.isHighlighter ->
                        el.copy(isHighlighter = true)
                    el is NativeBitmapElement && entry.layerName == "document" ->
                        el.copy(layer = "document")
                    el is NativeVectorImageElement && entry.layerName == "document" ->
                        NativeVectorImageElement(
                            el.svgData, el.intrinsicWidth, el.intrinsicHeight,
                            el.halfExtentX, el.halfExtentY, el.transform, "document",
                            el.minX, el.minY, el.maxX, el.maxY
                        )
                    else -> el
                }
            }
        // A stroke with no chrono entry at all is still a stroke; Rnote would draw it too.
        val missing = raw.indices.filter { it !in seen }.mapNotNull { raw[it] }
        return if (missing.isEmpty()) ordered else ordered + missing
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun parseColor(reader: JsonReader): RnoteNativeColor {
        // Rnote's shape colours are `Option<Color>`: null means "not drawn", which reads
        // as transparent. Reading it as an object threw, and took the whole file with it.
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return RnoteNativeColor.TRANSPARENT
        }
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
