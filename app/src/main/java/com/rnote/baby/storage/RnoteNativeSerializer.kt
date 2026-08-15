package com.rnote.baby.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.rnote.baby.model.EllipseShape
import com.rnote.baby.model.FreehandShape
import com.rnote.baby.model.LineShape
import com.rnote.baby.model.NativeBitmapElement
import com.rnote.baby.model.NativeBrushStroke
import com.rnote.baby.model.NativeCanvasElement
import com.rnote.baby.model.NativePatternType
import com.rnote.baby.model.NativeShapeElement
import com.rnote.baby.model.NativeTextElement
import com.rnote.baby.model.RectShape
import com.rnote.baby.model.RnoteNativeColor
import com.rnote.baby.model.RnoteNativeDocument
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.model.PaperPattern
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
        context.contentResolver.openOutputStream(uri)!!.use { serialize(it, doc) }
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    /** Bridge: convert our editable [NoteDocument] → native format and write. */
    fun serializeFromNoteDocument(context: Context, uri: Uri, doc: NoteDocument): Boolean {
        val native = bridgeToNative(doc)
        return serialize(context, uri, native)
    }

    fun serialize(outputStream: OutputStream, doc: RnoteNativeDocument) {
        GZIPOutputStream(outputStream).use { gzip ->
            OutputStreamWriter(gzip, Charsets.UTF_8).use { writer ->
                writer.write(buildJson(doc))
            }
        }
    }

    // ── Bridge NoteDocument → RnoteNativeDocument ─────────────────────────────

    private fun bridgeToNative(doc: NoteDocument): RnoteNativeDocument {
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
            val pts = stroke.points.map {
                com.rnote.baby.model.NativeStrokePoint(it.x, it.y, it.pressure)
            }
            val color = RnoteNativeColor(
                stroke.color.red, stroke.color.green, stroke.color.blue, stroke.color.alpha
            )
            val minX = pts.minOfOrNull { it.x } ?: 0f
            val minY = pts.minOfOrNull { it.y } ?: 0f
            val maxX = pts.maxOfOrNull { it.x } ?: 0f
            val maxY = pts.maxOfOrNull { it.y } ?: 0f
            NativeBrushStroke(pts, stroke.strokeWidth, color, stroke.isHighlighter, minX, minY, maxX, maxY)
        }

        // Include preserved native elements (text, shapes, images) in save-back
        val allElements: List<NativeCanvasElement> = nativeStrokes + doc.nativeElements.filter { it !is NativeBrushStroke }

        return RnoteNativeDocument(
            pageWidth   = pageW,
            pageHeight  = pageH,
            totalHeight = pageH,
            background  = com.rnote.baby.model.NativeBackgroundConfig(
                color        = bgColor,
                pattern      = nativePattern,
                patternWidth = doc.paperStyle.gridSpacingPx,
                patternHeight = doc.paperStyle.gridSpacingPx,
                patternColor = gridColor
            ),
            elements = allElements
        )
    }

    // ── JSON builder ──────────────────────────────────────────────────────────

    private fun buildJson(doc: RnoteNativeDocument): String {
        val sb = StringBuilder()
        sb.append("""{"data":{"engine_snapshot":{""")
        sb.append(""""document":""")
        sb.appendDocument(doc)
        sb.append(""","stroke_components":[""")

        doc.elements.forEachIndexed { i, el ->
            if (i > 0) sb.append(',')
            sb.append("""{"value":""")
            sb.appendElement(el)
            sb.append('}')
        }

        sb.append("""],"chrono_components":[""")
        doc.elements.indices.forEachIndexed { i, idx ->
            if (i > 0) sb.append(',')
            sb.append("""{"stroke_key":{"index":$idx,"generation":1}}""")
        }
        sb.append("]}}}}")
        return sb.toString()
    }

    // ── Document block ────────────────────────────────────────────────────────

    private fun StringBuilder.appendDocument(doc: RnoteNativeDocument) {
        val bg = doc.background
        append("""{
            |"config":{
            |  "format":{"width":${doc.pageWidth},"height":${doc.pageHeight},"dpi":96,"orientation":"portrait"},
            |  "background":{
            |    "color":${bg.color.toJson()},
            |    "pattern":"${bg.pattern.toApiString()}",
            |    "pattern_size":[${bg.patternWidth},${bg.patternHeight}],
            |    "pattern_color":${bg.patternColor.toJson()}
            |  }
            |},
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
        }
    }

    // ── BrushStroke ───────────────────────────────────────────────────────────

    private fun StringBuilder.appendBrushStroke(el: NativeBrushStroke) {
        append("""{"brushstroke":{""")
        append(""""path":{"elements":[""")
        el.points.forEachIndexed { i, pt ->
            if (i > 0) append(',')
            append("""{"pos":[${pt.x},${pt.y}],"pressure":${pt.pressure}}""")
        }
        append("""]},"style":{"Smooth":{""")
        append(""""stroke_color":${el.color.toJson()},""")
        append(""""stroke_width":${el.strokeWidth}""")
        append("""}},"brush":{"BrushStyle":"${if (el.isHighlighter) "Highlighter" else "Marker"}"}}}""")
    }

    // ── TextElement ───────────────────────────────────────────────────────────

    private fun StringBuilder.appendTextElement(el: NativeTextElement) {
        val tf = el.transform
        append("""{"textstroke":{""")
        append(""""text":${jsonString(el.text)},""")
        append(""""transform":{"matrix":[${tf.joinToString(",")}]},""")
        append(""""text_style":{""")
        append(""""font_family":${jsonString(el.fontFamily)},""")
        append(""""font_size":${el.fontSize},""")
        append(""""color":${el.color.toJson()}""")
        append("""}}}}""")
    }

    // ── BitmapElement ─────────────────────────────────────────────────────────

    private fun StringBuilder.appendBitmapElement(el: NativeBitmapElement) {
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
        append(""""transform":{"matrix":[${tf.joinToString(",")}]},""")
        append(""""bounds":{""")
        append(""""mins":[${el.minX},${el.minY}],"maxs":[${el.maxX},${el.maxY}]""")
        append("}}}")
    }

    // ── ShapeElement ──────────────────────────────────────────────────────────

    private fun StringBuilder.appendShapeElement(el: NativeShapeElement) {
        append("""{"shapestroke":{"shape":{""")
        when (val s = el.shape) {
            is LineShape    -> append(""""Line":{"start":[${s.x1},${s.y1}],"end":[${s.x2},${s.y2}]}""")
            is RectShape    -> append(""""Rectangle":{"top_left":[${s.x},${s.y}],"size":[${s.w},${s.h}]}""")
            is EllipseShape -> append(""""Ellipse":{"center":[${s.cx},${s.cy}],"radii":[${s.rx},${s.ry}]}""")
            is FreehandShape -> {
                append(""""FreehandPen":{"elements":[""")
                s.points.forEachIndexed { i, pt ->
                    if (i > 0) append(',')
                    append("""{"pos":[${pt.x},${pt.y}],"pressure":${pt.pressure}}""")
                }
                append("]}")
            }
        }
        append("""},"style":{"Smooth":{""")
        append(""""stroke_color":${el.color.toJson()},""")
        append(""""stroke_width":${el.strokeWidth}""")
        append("}}}}}")
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
