package io.github.kjly.brna.storage

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.kjly.brna.model.Affine
import io.github.kjly.brna.model.EllipseShape
import io.github.kjly.brna.model.InvertedBrightness
import io.github.kjly.brna.model.LineShape
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeBrushStroke
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.PathOp
import io.github.kjly.brna.model.PathShape
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RoughStyle
import io.github.kjly.brna.model.ShapeKind
import io.github.kjly.brna.model.ShapeLine
import io.github.kjly.brna.model.TextFormatting
import io.github.kjly.brna.model.TexturedStyle
import io.github.kjly.brna.model.TextToggle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Editing for the desktop elements this app used to only display — text, shapes, images,
 * PDF pages — and creating new shapes and text. Every element that carries the JSON it was read
 * from ([NativeShapeElement.raw] etc.) gets that JSON changed along with it, so a moved
 * text box is written back moved and still has every attribute the model doesn't know.
 */
object NativeEditing {

    // ── Moving ────────────────────────────────────────────────────────────────

    /** [el] moved by ([dx], [dy]). Brush strokes are returned unchanged: they live in the stroke list. */
    fun translate(el: NativeCanvasElement, dx: Float, dy: Float): NativeCanvasElement = when (el) {
        is NativeTextElement -> el.copy(
            transform = shifted(el.transform, dx, dy),
            minX = el.minX + dx, minY = el.minY + dy, maxX = el.maxX + dx, maxY = el.maxY + dy,
            raw = el.raw?.let { shiftTree(it, dx, dy) }
        )
        is NativeShapeElement -> el.copy(
            shape = when (val s = el.shape) {
                is LineShape -> LineShape(s.x1 + dx, s.y1 + dy, s.x2 + dx, s.y2 + dy)
                is RectShape -> s.copy(transform = shifted(s.transform, dx, dy))
                is EllipseShape -> s.copy(transform = shifted(s.transform, dx, dy))
                is PathShape -> PathShape(s.ops.map { shiftOp(it, dx, dy) })
            },
            minX = el.minX + dx, minY = el.minY + dy, maxX = el.maxX + dx, maxY = el.maxY + dy,
            raw = el.raw?.let { shiftTree(it, dx, dy) }
        )
        is NativeBitmapElement -> el.copy(
            transform = shifted(el.transform, dx, dy),
            rect = el.rect?.let { it.copy(transform = shifted(it.transform, dx, dy)) },
            minX = el.minX + dx, minY = el.minY + dy, maxX = el.maxX + dx, maxY = el.maxY + dy,
            raw = el.raw?.let { shiftTree(it, dx, dy) }
        )
        is NativeVectorImageElement -> NativeVectorImageElement(
            el.svgData, el.intrinsicWidth, el.intrinsicHeight, el.halfExtentX, el.halfExtentY,
            shifted(el.transform, dx, dy), el.layer,
            el.minX + dx, el.minY + dy, el.maxX + dx, el.maxY + dy
        )
        is NativeBrushStroke -> el
    }

    private fun shifted(t: FloatArray, dx: Float, dy: Float) =
        floatArrayOf(t[0], t[1], t[2], t[3], t[4] + dx, t[5] + dy)

    private fun shiftOp(op: PathOp, dx: Float, dy: Float): PathOp = when (op) {
        is PathOp.MoveTo -> PathOp.MoveTo(op.x + dx, op.y + dy)
        is PathOp.LineTo -> PathOp.LineTo(op.x + dx, op.y + dy)
        is PathOp.QuadTo -> PathOp.QuadTo(op.x1 + dx, op.y1 + dy, op.x + dx, op.y + dy)
        is PathOp.CubicTo -> PathOp.CubicTo(op.x1 + dx, op.y1 + dy, op.x2 + dx, op.y2 + dy, op.x + dx, op.y + dy)
        PathOp.Close -> op
    }

    /** Point fields of Rnote's shapes (and the legacy corner/centre forms). */
    private val POINT_KEYS = setOf("start", "end", "tip", "cp", "cp1", "cp2", "top_left", "center")

    /**
     * A copy of an element's JSON with everything positional moved: every `affine`
     * transform's translation, every named point, and the vertices of a polyline's or
     * polygon's `path`. Colours, sizes and radii are left alone — nothing else in Rnote's
     * element JSON is a position. Styles are skipped outright, so a stroke width is never
     * mistaken for a coordinate.
     */
    private fun shiftTree(tree: JsonElement, dx: Float, dy: Float): JsonElement {
        val copy = tree.deepCopy()
        shiftIn(copy, dx, dy)
        return copy
    }

    private fun shiftIn(node: JsonElement, dx: Float, dy: Float) {
        if (!node.isJsonObject) return
        val obj = node.asJsonObject
        for ((key, value) in obj.entrySet()) {
            when {
                key == "style" || key == "text_style" -> Unit
                key == "affine" && value.isJsonArray && value.asJsonArray.size() == 9 -> {
                    val a = value.asJsonArray
                    a.set(6, JsonPrimitive(round3(a[6].asDouble + dx)))
                    a.set(7, JsonPrimitive(round3(a[7].asDouble + dy)))
                }
                key in POINT_KEYS && isPoint(value) -> shiftPoint(value.asJsonArray, dx, dy)
                key == "path" && value.isJsonArray -> value.asJsonArray.forEach {
                    if (isPoint(it)) shiftPoint(it.asJsonArray, dx, dy)
                }
                value.isJsonObject -> shiftIn(value, dx, dy)
            }
        }
    }

    private fun isPoint(e: JsonElement) =
        e.isJsonArray && e.asJsonArray.size() == 2 &&
            e.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }

    private fun shiftPoint(p: JsonArray, dx: Float, dy: Float) {
        p.set(0, JsonPrimitive(round3(p[0].asDouble + dx)))
        p.set(1, JsonPrimitive(round3(p[1].asDouble + dy)))
    }

    /** Rnote writes coordinates to three decimals; so do we. */
    private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0

    // ── Scaling and rotating ──────────────────────────────────────────────────

    /**
     * [el] with the affine [m] applied — how the selector scales and rotates. Mirrors
     * Rnote's `Transformable` impls: points are mapped, every placement transform (a
     * rect's, an ellipse's, a text box's, an image's) has [m] composed onto it, and a
     * shape's line width is scaled by [Affine.widthFactor], as Rnote's `ShapeStroke::scale`
     * does. Text keeps its font size and is scaled through its transform instead, as in
     * Rnote. Brush strokes are returned unchanged: they live in the stroke list.
     */
    fun transform(el: NativeCanvasElement, m: FloatArray): NativeCanvasElement {
        val widthFactor = Affine.widthFactor(m)
        return when (el) {
            is NativeTextElement -> {
                val tree = (el.raw?.takeIf { it.isJsonObject } ?: textJson(el)).deepCopy()
                transformIn(tree, m, 1.0)
                RnoteNativeParser.parseElementTree(JsonObject().apply { add("textstroke", tree) })
                    as? NativeTextElement ?: el
            }
            is NativeShapeElement -> {
                val raw = el.raw
                if (raw != null) {
                    val tree = raw.deepCopy()
                    transformIn(tree, m, widthFactor.toDouble())
                    RnoteNativeParser.parseElementTree(JsonObject().apply { add("shapestroke", tree) })
                        as? NativeShapeElement ?: el
                } else {
                    val shape = mapShape(el.shape, m)
                    val b = outlineBounds(shape) ?: floatArrayOf(el.minX, el.minY, el.maxX, el.maxY)
                    el.copy(shape = shape, strokeWidth = el.strokeWidth * widthFactor,
                        minX = b[0], minY = b[1], maxX = b[2], maxY = b[3])
                }
            }
            is NativeBitmapElement -> {
                // Not re-read from its JSON: an older file's image would be decoded all
                // over again on every frame of the drag.
                val rect = el.rect?.let { it.copy(transform = Affine.compose(m, it.transform)) }
                val b = if (rect != null) boxBounds(rect.transform, rect.halfExtentX, rect.halfExtentY)
                else mappedBounds(m, el.minX, el.minY, el.maxX, el.maxY)
                el.copy(
                    transform = Affine.compose(m, el.transform), rect = rect,
                    minX = b[0], minY = b[1], maxX = b[2], maxY = b[3],
                    raw = el.raw?.deepCopy()?.also { transformIn(it, m, 1.0) }
                )
            }
            is NativeVectorImageElement -> {
                val t = Affine.compose(m, el.transform)
                val b = boxBounds(t, el.halfExtentX, el.halfExtentY)
                NativeVectorImageElement(
                    el.svgData, el.intrinsicWidth, el.intrinsicHeight, el.halfExtentX, el.halfExtentY,
                    t, el.layer, b[0], b[1], b[2], b[3]
                )
            }
            is NativeBrushStroke -> el
        }
    }

    /** Applies [m] to every position in an element's JSON; see [shiftTree] for what counts. */
    private fun transformIn(node: JsonElement, m: FloatArray, widthFactor: Double) {
        if (!node.isJsonObject) return
        for ((key, value) in node.asJsonObject.entrySet()) {
            when {
                key == "text_style" -> Unit
                key == "style" -> if (widthFactor != 1.0) scaleWidths(value, widthFactor)
                key == "affine" && value.isJsonArray && value.asJsonArray.size() == 9 -> {
                    val a = value.asJsonArray
                    // Composed in double precision and written unrounded: a rotation's
                    // cosines rounded to three places would visibly shrink a large page.
                    val t = doubleArrayOf(a[0].asDouble, a[1].asDouble, a[3].asDouble, a[4].asDouble, a[6].asDouble, a[7].asDouble)
                    val r = composeD(m, t)
                    a.set(0, JsonPrimitive(r[0])); a.set(1, JsonPrimitive(r[1]))
                    a.set(3, JsonPrimitive(r[2])); a.set(4, JsonPrimitive(r[3]))
                    a.set(6, JsonPrimitive(r[4])); a.set(7, JsonPrimitive(r[5]))
                }
                key in POINT_KEYS && isPoint(value) -> mapPoint(value.asJsonArray, m)
                key == "path" && value.isJsonArray -> value.asJsonArray.forEach {
                    if (isPoint(it)) mapPoint(it.asJsonArray, m)
                }
                value.isJsonObject -> transformIn(value, m, widthFactor)
            }
        }
    }

    /** Multiplies every `stroke_width` in a style (Rnote's smooth, rough and textured all have one). */
    private fun scaleWidths(node: JsonElement, factor: Double) {
        if (!node.isJsonObject) return
        val obj = node.asJsonObject
        for ((key, value) in obj.entrySet()) {
            if (key == "stroke_width" && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                obj.add(key, JsonPrimitive(round3(value.asDouble * factor)))
            } else if (value.isJsonObject) {
                scaleWidths(value, factor)
            }
        }
    }

    private fun composeD(m: FloatArray, t: DoubleArray): DoubleArray {
        val m0 = m[0].toDouble(); val m1 = m[1].toDouble(); val m2 = m[2].toDouble()
        val m3 = m[3].toDouble(); val m4 = m[4].toDouble(); val m5 = m[5].toDouble()
        return doubleArrayOf(
            m0 * t[0] + m2 * t[1], m1 * t[0] + m3 * t[1],
            m0 * t[2] + m2 * t[3], m1 * t[2] + m3 * t[3],
            m0 * t[4] + m2 * t[5] + m4, m1 * t[4] + m3 * t[5] + m5
        )
    }

    private fun mapPoint(p: JsonArray, m: FloatArray) {
        val x = p[0].asDouble; val y = p[1].asDouble
        p.set(0, JsonPrimitive(round3(m[0] * x + m[2] * y + m[4])))
        p.set(1, JsonPrimitive(round3(m[1] * x + m[3] * y + m[5])))
    }

    private fun mapShape(shape: io.github.kjly.brna.model.NativeShapeKind, m: FloatArray) = when (shape) {
        is LineShape -> LineShape(
            Affine.mapX(m, shape.x1, shape.y1), Affine.mapY(m, shape.x1, shape.y1),
            Affine.mapX(m, shape.x2, shape.y2), Affine.mapY(m, shape.x2, shape.y2)
        )
        is RectShape -> shape.copy(transform = Affine.compose(m, shape.transform))
        is EllipseShape -> shape.copy(transform = Affine.compose(m, shape.transform))
        is PathShape -> PathShape(shape.ops.map { op ->
            fun x(px: Float, py: Float) = Affine.mapX(m, px, py)
            fun y(px: Float, py: Float) = Affine.mapY(m, px, py)
            when (op) {
                is PathOp.MoveTo -> PathOp.MoveTo(x(op.x, op.y), y(op.x, op.y))
                is PathOp.LineTo -> PathOp.LineTo(x(op.x, op.y), y(op.x, op.y))
                is PathOp.QuadTo -> PathOp.QuadTo(x(op.x1, op.y1), y(op.x1, op.y1), x(op.x, op.y), y(op.x, op.y))
                is PathOp.CubicTo -> PathOp.CubicTo(
                    x(op.x1, op.y1), y(op.x1, op.y1), x(op.x2, op.y2), y(op.x2, op.y2), x(op.x, op.y), y(op.x, op.y)
                )
                PathOp.Close -> op
            }
        })
    }

    private fun outlineBounds(shape: io.github.kjly.brna.model.NativeShapeKind): FloatArray? {
        val pts = outline(shape).flatten()
        if (pts.isEmpty()) return null
        return floatArrayOf(pts.minOf { it.first }, pts.minOf { it.second }, pts.maxOf { it.first }, pts.maxOf { it.second })
    }

    /** Axis-aligned bounds of the box ±([hx], [hy]) placed by [t]. */
    private fun boxBounds(t: FloatArray, hx: Float, hy: Float): FloatArray =
        mappedBounds(t, -hx, -hy, hx, hy)

    private fun mappedBounds(m: FloatArray, l: Float, t: Float, r: Float, b: Float): FloatArray {
        val xs = floatArrayOf(Affine.mapX(m, l, t), Affine.mapX(m, r, t), Affine.mapX(m, r, b), Affine.mapX(m, l, b))
        val ys = floatArrayOf(Affine.mapY(m, l, t), Affine.mapY(m, r, t), Affine.mapY(m, r, b), Affine.mapY(m, l, b))
        return floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }

    // ── Hit testing ───────────────────────────────────────────────────────────

    /**
     * Whether the eraser square hits [el]'s outline — Rnote's eraser removes shapes the
     * way it removes ink, and leaves text and images alone (`trash_colliding_strokes`).
     */
    fun eraserHits(el: NativeShapeElement, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val pad = el.strokeWidth / 2f
        if (el.maxX + pad < left || el.minX - pad > right || el.maxY + pad < top || el.minY - pad > bottom) {
            return false
        }
        val l = left - pad; val t = top - pad; val r = right + pad; val b = bottom + pad
        // A filled shape is hit anywhere inside it, as it looks.
        if (el.fillColor.a > 0f && l <= el.maxX && r >= el.minX && t <= el.maxY && b >= el.minY) {
            val cx = (l + r) / 2f; val cy = (t + b) / 2f
            if (cx in el.minX..el.maxX && cy in el.minY..el.maxY) return true
        }
        for (polyline in outline(el.shape)) {
            for (i in 1 until polyline.size) {
                val (x1, y1) = polyline[i - 1]
                val (x2, y2) = polyline[i]
                if (segmentHitsBox(x1, y1, x2, y2, l, t, r, b)) return true
            }
            if (polyline.size == 1 && polyline[0].first in l..r && polyline[0].second in t..b) return true
        }
        return false
    }

    /** The shape's outline as polylines in document coordinates, curves flattened. */
    fun outline(shape: io.github.kjly.brna.model.NativeShapeKind): List<List<Pair<Float, Float>>> = when (shape) {
        is LineShape -> listOf(listOf(shape.x1 to shape.y1, shape.x2 to shape.y2))
        is RectShape -> {
            val t = shape.transform
            val hx = shape.halfExtentX; val hy = shape.halfExtentY
            listOf(listOf(-hx to -hy, hx to -hy, hx to hy, -hx to hy, -hx to -hy).map { (x, y) ->
                (t[0] * x + t[2] * y + t[4]) to (t[1] * x + t[3] * y + t[5])
            })
        }
        is EllipseShape -> {
            val t = shape.transform
            listOf((0..48).map { i ->
                val a = i * 2.0 * Math.PI / 48
                val x = shape.radiusX * cos(a).toFloat(); val y = shape.radiusY * sin(a).toFloat()
                (t[0] * x + t[2] * y + t[4]) to (t[1] * x + t[3] * y + t[5])
            })
        }
        is PathShape -> {
            val lines = mutableListOf<MutableList<Pair<Float, Float>>>()
            var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f
            for (op in shape.ops) {
                when (op) {
                    is PathOp.MoveTo -> { lines += mutableListOf(op.x to op.y); cx = op.x; cy = op.y; sx = cx; sy = cy }
                    is PathOp.LineTo -> { lines.lastOrNull()?.add(op.x to op.y); cx = op.x; cy = op.y }
                    is PathOp.QuadTo -> {
                        val line = lines.lastOrNull() ?: continue
                        for (i in 1..16) {
                            val u = i / 16f; val v = 1 - u
                            line += (v * v * cx + 2 * v * u * op.x1 + u * u * op.x) to
                                (v * v * cy + 2 * v * u * op.y1 + u * u * op.y)
                        }
                        cx = op.x; cy = op.y
                    }
                    is PathOp.CubicTo -> {
                        val line = lines.lastOrNull() ?: continue
                        for (i in 1..16) {
                            val u = i / 16f; val v = 1 - u
                            line += (v * v * v * cx + 3 * v * v * u * op.x1 + 3 * v * u * u * op.x2 + u * u * u * op.x) to
                                (v * v * v * cy + 3 * v * v * u * op.y1 + 3 * v * u * u * op.y2 + u * u * u * op.y)
                        }
                        cx = op.x; cy = op.y
                    }
                    PathOp.Close -> { lines.lastOrNull()?.add(sx to sy); cx = sx; cy = sy }
                }
            }
            lines
        }
    }

    /** Liang–Barsky: does the segment cross the box? */
    internal fun segmentHitsBox(
        x1: Float, y1: Float, x2: Float, y2: Float,
        l: Float, t: Float, r: Float, b: Float
    ): Boolean {
        var t0 = 0f; var t1 = 1f
        val dx = x2 - x1; val dy = y2 - y1
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(x1 - l, r - x1, y1 - t, b - y1)
        for (i in 0..3) {
            if (p[i] == 0f) {
                if (q[i] < 0f) return false
            } else {
                val u = q[i] / p[i]
                if (p[i] < 0f) { if (u > t1) return false; if (u > t0) t0 = u }
                else { if (u < t0) return false; if (u < t1) t1 = u }
            }
        }
        return true
    }

    /**
     * Whether [el] lies entirely inside the lasso. Entirely, not partly: a lasso drawn
     * across an imported PDF page to catch the notes on it must not pick up the page.
     */
    fun insideLasso(el: NativeCanvasElement, polygon: List<Pair<Float, Float>>): Boolean {
        if (el is NativeBrushStroke || polygon.size < 3) return false
        return listOf(el.minX to el.minY, el.maxX to el.minY, el.maxX to el.maxY, el.minX to el.maxY)
            .all { (x, y) -> pointInPolygon(x, y, polygon) }
    }

    /** Whether [el] lies wholly inside the box ([l], [t])–([r], [b]): Rnote's rectangle selection. */
    fun insideRect(el: NativeCanvasElement, l: Float, t: Float, r: Float, b: Float): Boolean =
        el !is NativeBrushStroke && el.minX >= l && el.maxX <= r && el.minY >= t && el.maxY <= b

    /**
     * Whether the line [path] crosses [el]: Rnote's intersecting-path selection, which
     * tests the path against the element's hitboxes — a shape's outline, piece by piece
     * (and, filled, its whole area); a text box's or a picture's whole box.
     */
    fun crossedByPath(el: NativeCanvasElement, path: List<Pair<Float, Float>>): Boolean {
        if (el is NativeBrushStroke || path.size < 3) return false
        var pl = Float.MAX_VALUE; var pt = Float.MAX_VALUE; var pr = -Float.MAX_VALUE; var pb = -Float.MAX_VALUE
        for ((x, y) in path) { pl = minOf(pl, x); pt = minOf(pt, y); pr = maxOf(pr, x); pb = maxOf(pb, y) }
        return hitboxes(el).any { box ->
            box[0] <= pr && box[2] >= pl && box[1] <= pb && box[3] >= pt &&
                (1 until path.size).any { i ->
                    val (x1, y1) = path[i - 1]
                    val (x2, y2) = path[i]
                    segmentHitsBox(x1, y1, x2, y2, box[0], box[1], box[2], box[3])
                }
        }
    }

    /**
     * Whether a tap at ([x], [y]) lands on [el], [tolerance] around it: Rnote's single
     * selection. A shape is hit on its outline — anywhere inside when it is filled — a
     * text box or picture anywhere in its box.
     */
    fun hitAt(el: NativeCanvasElement, x: Float, y: Float, tolerance: Float): Boolean = when (el) {
        is NativeBrushStroke -> false
        is NativeShapeElement -> eraserHits(el, x - tolerance, y - tolerance, x + tolerance, y + tolerance)
        else -> x >= el.minX - tolerance && x <= el.maxX + tolerance && y >= el.minY - tolerance && y <= el.maxY + tolerance
    }

    /** Rnote's hitboxes for [el], as [left, top, right, bottom] boxes. */
    private fun hitboxes(el: NativeCanvasElement): List<FloatArray> {
        if (el !is NativeShapeElement) return listOf(floatArrayOf(el.minX, el.minY, el.maxX, el.maxY))
        val pad = el.strokeWidth / 2f
        val boxes = mutableListOf<FloatArray>()
        if (el.fillColor.a > 0f) boxes += floatArrayOf(el.minX, el.minY, el.maxX, el.maxY)
        for (polyline in outline(el.shape)) {
            if (polyline.size == 1) {
                val (x, y) = polyline[0]
                boxes += floatArrayOf(x - pad, y - pad, x + pad, y + pad)
            }
            for (i in 1 until polyline.size) {
                val (x1, y1) = polyline[i - 1]
                val (x2, y2) = polyline[i]
                boxes += floatArrayOf(minOf(x1, x2) - pad, minOf(y1, y2) - pad, maxOf(x1, x2) + pad, maxOf(y1, y2) + pad)
            }
        }
        return boxes
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    // ── Creating shapes ───────────────────────────────────────────────────────

    /**
     * A new shape dragged from ([x1], [y1]) to ([x2], [y2]), built as the JSON desktop
     * Rnote writes for its own shaper and read back through the normal parser, so a shape
     * drawn here is exactly what the file will hold. Rectangles and ellipses fill the
     * dragged box; null for a drag too short to be a shape. [fillColor] goes on every
     * shape, as Rnote's shaper puts its fill colour on every shape it draws.
     */
    fun createShape(
        kind: ShapeKind,
        x1: Float, y1: Float, x2: Float, y2: Float,
        color: RnoteNativeColor,
        strokeWidth: Float,
        fillColor: RnoteNativeColor = RnoteNativeColor.TRANSPARENT,
        line: ShapeLine = ShapeLine(),
        rough: RoughStyle? = null
    ): NativeShapeElement? {
        if (abs(x2 - x1) < 1f && abs(y2 - y1) < 1f) return null
        val cx = (x1 + x2) / 2f; val cy = (y1 + y2) / 2f
        val hx = abs(x2 - x1) / 2f; val hy = abs(y2 - y1) / 2f
        val shape = when (kind) {
            ShapeKind.LINE -> """{"line":{"start":${pointJson(x1, y1)},"end":${pointJson(x2, y2)}}}"""
            ShapeKind.ARROW -> """{"arrow":{"start":${pointJson(x1, y1)},"tip":${pointJson(x2, y2)}}}"""
            ShapeKind.RECTANGLE ->
                """{"rect":{"cuboid":{"half_extents":${pointJson(hx, hy)}},"transform":${affineText(1f, 0f, cx, cy)}}}"""
            ShapeKind.ELLIPSE ->
                """{"ellipse":{"radii":${pointJson(hx, hy)},"transform":${affineText(1f, 0f, cx, cy)}}}"""
            // Several lines each; see ShapeBuilders, whose lines come back through here.
            ShapeKind.COORD_SYSTEM_2D, ShapeKind.COORD_SYSTEM_3D, ShapeKind.QUADRANT, ShapeKind.GRID -> return null
            // Several strokes of the pen each; see ShapeDraft, which ends in the ones below.
            ShapeKind.POLYLINE, ShapeKind.POLYGON, ShapeKind.QUADBEZ, ShapeKind.CUBBEZ, ShapeKind.FOCI_ELLIPSE -> return null
        }
        return shapeElement(shape, color, strokeWidth, fillColor, line, rough)
    }

    /**
     * Rnote's `Polyline` — or, [closed], its `Polygon` — through [points], the first one
     * its start. Null for fewer corners than the shape needs: two for a polyline, three
     * for a polygon.
     */
    fun createPolyShape(
        closed: Boolean,
        points: List<Pair<Float, Float>>,
        color: RnoteNativeColor,
        strokeWidth: Float,
        fillColor: RnoteNativeColor = RnoteNativeColor.TRANSPARENT,
        line: ShapeLine = ShapeLine(),
        rough: RoughStyle? = null
    ): NativeShapeElement? {
        if (points.size < (if (closed) 3 else 2)) return null
        val (sx, sy) = points.first()
        val path = points.drop(1).joinToString(",") { (x, y) -> pointJson(x, y) }
        val name = if (closed) "polygon" else "polyline"
        return shapeElement("""{"$name":{"start":${pointJson(sx, sy)},"path":[$path]}}""", color, strokeWidth, fillColor, line, rough)
    }

    /**
     * Rnote's quadratic Bézier from three points — start, control point, end — or its
     * cubic one from four; null for any other number.
     */
    fun createCurve(
        points: List<Pair<Float, Float>>,
        color: RnoteNativeColor,
        strokeWidth: Float,
        fillColor: RnoteNativeColor = RnoteNativeColor.TRANSPARENT,
        line: ShapeLine = ShapeLine(),
        rough: RoughStyle? = null
    ): NativeShapeElement? {
        val p = points.map { (x, y) -> pointJson(x, y) }
        val shape = when (p.size) {
            3 -> """{"quadbez":{"start":${p[0]},"cp":${p[1]},"end":${p[2]}}}"""
            4 -> """{"cubbez":{"start":${p[0]},"cp1":${p[1]},"cp2":${p[2]},"end":${p[3]}}}"""
            else -> return null
        }
        return shapeElement(shape, color, strokeWidth, fillColor, line, rough)
    }

    /**
     * Rnote's `Ellipse::from_foci_and_point`: the ellipse through ([px], [py]) whose foci
     * are ([f1x], [f1y]) and ([f2x], [f2y]), turned to lie along them. It is an ordinary
     * ellipse in the file, radii about a transformed centre, as Rnote writes it.
     */
    fun createFociEllipse(
        f1x: Float, f1y: Float, f2x: Float, f2y: Float, px: Float, py: Float,
        color: RnoteNativeColor,
        strokeWidth: Float,
        fillColor: RnoteNativeColor = RnoteNativeColor.TRANSPARENT,
        line: ShapeLine = ShapeLine(),
        rough: RoughStyle? = null
    ): NativeShapeElement? {
        val sum = hypot(px - f1x, py - f1y) + hypot(px - f2x, py - f2y)
        val d = hypot(f1x - f2x, f1y - f2y) * 0.5f
        var semimajor = sum * 0.5f
        // The pen can't be closer to both foci than they are to each other; rounding can.
        var semiminor = sqrt((semimajor * semimajor - d * d).coerceAtLeast(0f))
        if (semimajor == 0f) semimajor = 1f
        if (semiminor == 0f) semiminor = 1f
        val angle = atan2(f2y - f1y, f2x - f1x)
        val shape = """{"ellipse":{"radii":${pointJson(semimajor, semiminor)},""" +
            """"transform":${affineText(cos(angle), sin(angle), (f1x + f2x) / 2f, (f1y + f2y) / 2f)}}}"""
        return shapeElement(shape, color, strokeWidth, fillColor, line, rough)
    }

    private fun num(v: Float) = String.format(Locale.ROOT, "%.3f", v)
    private fun pointJson(x: Float, y: Float) = "[${num(x)},${num(y)}]"

    /** A turn by the angle of cosine [c] and sine [s], then a move to ([tx], [ty]): Rnote's column-major affine. */
    private fun affineText(c: Float, s: Float, tx: Float, ty: Float) =
        """{"affine":[${num(c)},${num(s)},0.0,${num(0f - s)},${num(c)},0.0,${num(tx)},${num(ty)},1.0]}"""

    /**
     * [shape] as a shape stroke with the style Rnote's shaper gives one — its line style
     * and cap [line] — built as the JSON desktop Rnote writes and read back through the
     * normal parser, so a shape drawn here is exactly what the file will hold.
     */
    private fun shapeElement(
        shape: String,
        color: RnoteNativeColor,
        strokeWidth: Float,
        fillColor: RnoteNativeColor,
        line: ShapeLine,
        rough: RoughStyle?
    ): NativeShapeElement? {
        fun color(c: RnoteNativeColor) = """{"r":${num(c.r)},"g":${num(c.g)},"b":${num(c.b)},"a":${num(c.a)}}"""
        val style = if (rough != null) {
            // Rnote's `RoughOptions`, in its order: no line style, but a fill style, the
            // hachure angle to three places as Rnote rounds it, and the seed.
            """{"rough":{"stroke_color":${color(color)},"stroke_width":${num(strokeWidth)},""" +
                """"fill_color":${color(fillColor)},"fill_style":"${rough.fillStyle.apiName}",""" +
                """"hachure_angle":${String.format(Locale.ROOT, "%.3f", rough.hachureAngle)},""" +
                """"seed":${TexturedStyle.seedJson(rough.seed)}}}"""
        } else {
            """{"smooth":{"stroke_width":${num(strokeWidth)},"stroke_color":${color(color)},""" +
                """"fill_color":${color(fillColor)},"pressure_curve":"const",""" +
                """"line_style":"${line.style.apiName}","line_cap":"${line.cap.apiName}"}}"""
        }
        return RnoteNativeParser.parseElementJson("""{"shapestroke":{"shape":$shape,"style":$style}}""")
            as? NativeShapeElement
    }

    // ── Colours ───────────────────────────────────────────────────────────────

    /**
     * [el] in [color]: Rnote's `change_stroke_colors` for the selection — a shape's line,
     * a text box's text. Pictures and PDF pages have no colour of their own and come
     * back as they are; so does anything whose JSON can't be rewritten.
     */
    fun withStrokeColor(el: NativeCanvasElement, color: RnoteNativeColor): NativeCanvasElement = when (el) {
        is NativeShapeElement -> withStyleColor(el, "stroke_color", color) ?: el.copy(color = color)
        is NativeTextElement -> {
            val obj = el.raw?.takeIf { it.isJsonObject }?.deepCopy()?.asJsonObject ?: textJson(el)
            val style = obj.get("text_style")?.takeIf { it.isJsonObject }?.asJsonObject
            if (style != null) {
                style.add("color", colorJson(color))
                parseText(obj) ?: el
            } else {
                el.copy(color = color)
            }
        }
        else -> el
    }

    /**
     * [el] filled with [color] — transparent for no fill: Rnote's `change_fill_colors`.
     * Only shapes have a fill; everything else comes back as it is.
     */
    fun withFillColor(el: NativeCanvasElement, color: RnoteNativeColor): NativeCanvasElement =
        if (el is NativeShapeElement) withStyleColor(el, "fill_color", color) ?: el.copy(fillColor = color) else el

    /**
     * The shape with [key] of its style set to [color], in whichever of Rnote's styles it
     * has (smooth or rough); null when it has no JSON of its own to change.
     */
    private fun withStyleColor(el: NativeShapeElement, key: String, color: RnoteNativeColor): NativeShapeElement? {
        val tree = el.raw?.takeIf { it.isJsonObject }?.deepCopy()?.asJsonObject ?: return null
        val style = tree.get("style")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val options = style.entrySet().firstOrNull { it.value.isJsonObject }?.value?.asJsonObject ?: return null
        options.add(key, colorJson(color))
        return RnoteNativeParser.parseElementTree(JsonObject().apply { add("shapestroke", tree) }) as? NativeShapeElement
    }

    /**
     * [el] with every colour it has put through [InvertedBrightness]: Rnote's
     * `set_to_inverted_brightness_color` for the selector's "Invert Color Brightness" —
     * a shape's line and fill, a text box's text. Pictures and PDF pages keep theirs; a
     * shape with no fill keeps none.
     */
    fun withInvertedColors(el: NativeCanvasElement): NativeCanvasElement = when (el) {
        is NativeShapeElement -> {
            val lined = withStrokeColor(el, inverted(el.color))
            if (el.fillColor.a > 0f && lined is NativeShapeElement) withFillColor(lined, inverted(el.fillColor)) else lined
        }
        is NativeTextElement -> withStrokeColor(el, inverted(el.color))
        else -> el
    }

    private fun inverted(c: RnoteNativeColor): RnoteNativeColor {
        val rgb = InvertedBrightness.of(c.r, c.g, c.b)
        return RnoteNativeColor(rgb[0], rgb[1], rgb[2], c.a)
    }

    // ── Images ────────────────────────────────────────────────────────────────

    /** Where a new image goes: its top-left corner, and document units per image pixel. */
    data class ImagePlacement(val x: Float, val y: Float, val scale: Float)

    /**
     * Desktop Rnote's placement of an imported image (`determine_stroke_import_pos` and
     * `calculate_resize_ratio`): the top-left corner [offset] in from the top-left of the
     * view, never above or left of the origin on a layout that has one; one document unit
     * per pixel, shrunk — never grown — until the image fits in the view and, on a layout
     * of fixed width, on the page. The view is in document units; [offset] is too, and is
     * also kept free on the right and at the bottom, where Rnote lets the image touch the
     * edge but the pen picker would sit on top of it here. With [borders] — Rnote's
     * "Respect Borders When Pasting" — it is shrunk until it also stays clear of the next
     * page border to the right and below, on every layout.
     */
    fun placeImage(
        pixelWidth: Int, pixelHeight: Int,
        viewLeft: Float, viewTop: Float, viewRight: Float, viewBottom: Float,
        offset: Float,
        fixedPageWidth: Float?,
        clampToOrigin: Boolean,
        borders: PageBorders? = null
    ): ImagePlacement {
        var x = viewLeft + offset
        var y = viewTop + offset
        if (clampToOrigin) {
            x = x.coerceAtLeast(0f)
            y = y.coerceAtLeast(0f)
        }
        val w = pixelWidth.coerceAtLeast(1).toFloat()
        val h = pixelHeight.coerceAtLeast(1).toFloat()
        var scale = 1f
        val roomX = viewRight - offset - x
        val roomY = viewBottom - offset - y
        if (roomX > 0f) scale = minOf(scale, roomX / w)
        if (roomY > 0f) scale = minOf(scale, roomY / h)
        if (fixedPageWidth != null && fixedPageWidth > x) scale = minOf(scale, (fixedPageWidth - x) / w)
        if (borders != null && borders.width > 0f && borders.height > 0f) {
            // Rnote's `helper_calculate_page_next_limit`: the page border after the corner.
            val nextX = (floor(x / borders.width) + 1f) * borders.width
            val nextY = (floor(y / borders.height) + 1f) * borders.height
            scale = minOf(scale, (nextX - x) / w, (nextY - y) / h)
        }
        return ImagePlacement(x, y, scale.coerceAtLeast(IMAGE_SCALE_MIN))
    }

    /** The page size the borders repeat at, for [placeImage]. */
    data class PageBorders(val width: Float, val height: Float)

    /**
     * A new image, built as desktop Rnote's `BitmapImage::from_image_bytes` builds one and
     * read back through the normal parser, so the element holds exactly what the file will:
     * a rectangle of the image's own pixel size, with [ImagePlacement.scale] and the
     * position in its transform. [rgbaBase64] is standard base64 of [pixelWidth] ×
     * [pixelHeight] pixels of premultiplied RGBA, Rnote's only memory format. Null for an
     * image without pixels.
     */
    fun createImage(
        rgbaBase64: String,
        pixelWidth: Int,
        pixelHeight: Int,
        placement: ImagePlacement
    ): NativeBitmapElement? {
        if (pixelWidth <= 0 || pixelHeight <= 0 || placement.scale <= 0f) return null
        val hx = pixelWidth / 2.0
        val hy = pixelHeight / 2.0
        fun rectangle(scale: Double, tx: Double, ty: Double) = JsonObject().apply {
            add("cuboid", JsonObject().apply {
                add("half_extents", JsonArray().apply { add(hx); add(hy) })
            })
            add("transform", JsonObject().apply {
                add("affine", JsonArray().apply {
                    listOf(scale, 0.0, 0.0, 0.0, scale, 0.0, tx, ty, 1.0).forEach { add(it) }
                })
            })
        }
        val image = JsonObject().apply {
            addProperty("data", rgbaBase64)
            // Rnote's `Image::from(DynamicImage)`: the pixel grid itself, at the origin.
            add("rectangle", rectangle(1.0, hx, hy))
            addProperty("pixel_width", pixelWidth)
            addProperty("pixel_height", pixelHeight)
            addProperty("memory_format", "R8g8b8a8Premultiplied")
        }
        val s = placement.scale.toDouble()
        val placed = rectangle(s, round3(placement.x + hx * s), round3(placement.y + hy * s))
        val tree = JsonObject().apply {
            add("bitmapimage", JsonObject().apply {
                add("image", image)
                add("rectangle", placed)
            })
        }
        return RnoteNativeParser.parseElementTree(tree) as? NativeBitmapElement
    }

    // ── Text (the Typewriter) ─────────────────────────────────────────────────

    /**
     * How wide a new text box may grow before it wraps: Rnote's typewriter default of 600,
     * narrowed so text typed on a page wraps at that page's right edge rather than running
     * off it — unless that would leave a column too narrow to write in.
     */
    fun typewriterWrapWidth(x: Float, pageWidth: Float): Float {
        if (pageWidth <= 0f) return TEXT_WIDTH_DEFAULT
        val pageRight = (kotlin.math.floor(x / pageWidth) + 1f) * pageWidth
        val room = pageRight - x - TEXT_PAGE_MARGIN
        return if (room >= TEXT_WIDTH_MIN) minOf(TEXT_WIDTH_DEFAULT, room) else TEXT_WIDTH_DEFAULT
    }

    /**
     * A new text box with its top-left corner at ([x], [y]), written the way desktop
     * Rnote's typewriter writes one and read back through the normal parser. Null for
     * text that is only whitespace, which Rnote doesn't keep either.
     */
    fun createText(
        text: String,
        x: Float, y: Float,
        fontSize: Float,
        color: RnoteNativeColor,
        maxWidth: Float?,
        /** Rnote's `TextAlignment` name: "start", "center", "end" or "fill". */
        alignment: String = "start"
    ): NativeTextElement? {
        if (text.isBlank()) return null
        val style = JsonObject().apply {
            addProperty("font_family", TEXT_FONT_FAMILY)
            addProperty("font_size", round3(fontSize.toDouble()))
            addProperty("font_weight", 500)
            addProperty("font_style", "regular")
            add("color", colorJson(color))
            if (maxWidth != null) addProperty("max_width", round3(maxWidth.toDouble()))
            else add("max_width", JsonNull.INSTANCE)
            addProperty("alignment", alignment)
            add("ranged_text_attributes", JsonArray())
        }
        val obj = JsonObject().apply {
            addProperty("text", text)
            add("transform", affineJson(floatArrayOf(1f, 0f, 0f, 1f, x, y)))
            add("text_style", style)
        }
        return parseText(obj)
    }

    /**
     * [el] aligned [alignment] — "start", "center", "end" or "fill" — everything else kept:
     * Rnote's typewriter alignment buttons, which set the box's `text_style.alignment`.
     */
    fun withAlignment(el: NativeTextElement, alignment: String): NativeTextElement {
        val obj = el.raw?.takeIf { it.isJsonObject }?.deepCopy()?.asJsonObject ?: textJson(el)
        val style = obj.get("text_style")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return el.copy(alignment = alignment)
        style.addProperty("alignment", alignment)
        return parseText(obj) ?: el.copy(alignment = alignment)
    }

    /**
     * [el] with its text replaced, everything else kept — font, colour, position, and
     * the bold/italic/underline ranges desktop Rnote put on parts of it, which are moved
     * along with the text around the edit. Null when the new text is blank: the caller
     * deletes the box, as Rnote does with an emptied one.
     */
    fun withText(el: NativeTextElement, newText: String): NativeTextElement? {
        if (newText.isBlank()) return null
        val obj = el.raw?.takeIf { it.isJsonObject }?.deepCopy()?.asJsonObject ?: textJson(el)
        obj.addProperty("text", newText)
        val style = obj.get("text_style")
        if (style != null && style.isJsonObject) {
            val ranges = style.asJsonObject.get("ranged_text_attributes")
            if (ranges != null && ranges.isJsonArray) {
                style.asJsonObject.add("ranged_text_attributes", shiftRanges(ranges.asJsonArray, el.text, newText))
            }
        }
        return parseText(obj)
    }

    // ── Text formatting ───────────────────────────────────────────────────────

    /**
     * Rnote's `TextStroke::toggle_attrs_for_range`, for the chars [startChar, endChar) of
     * [el]: the smallest range of the same kind that meets the selection decides — bold is
     * taken off, italic, underline and strikethrough are flipped — and with none there the
     * attribute is put on. Whatever of that kind lay inside the selection is cut out first.
     * An empty selection changes nothing, as in Rnote.
     */
    fun toggleFormat(el: NativeTextElement, startChar: Int, endChar: Int, toggle: TextToggle): NativeTextElement =
        editRanges(el, startChar, endChar, toggle) { intersecting ->
            val smallest = intersecting.minByOrNull { rangeEnd(it) - rangeStart(it) }
                ?: return@editRanges onValue(toggle)
            val value = smallest.asJsonObject.getAsJsonObject("attribute").get(toggle.key)
            when (toggle) {
                TextToggle.BOLD -> null
                TextToggle.ITALIC ->
                    JsonPrimitive(if (value?.isJsonPrimitive == true && value.asString == "italic") "regular" else "italic")
                TextToggle.UNDERLINE, TextToggle.STRIKETHROUGH ->
                    JsonPrimitive(!(value?.isJsonPrimitive == true && value.asBoolean))
            }
        }

    /**
     * [toggle] switched on or off for the chars [startChar, endChar) of [el], whatever they
     * had: what the switches set for text typed next. Setting rather than flipping keeps a
     * keyboard's autocorrect, which replaces a word already typed, from undoing it again.
     * Rnote's `replace_attr_for_range` to switch on, `remove_attrs_for_range` of just that
     * kind to switch off.
     */
    fun setFormat(el: NativeTextElement, startChar: Int, endChar: Int, toggle: TextToggle, on: Boolean): NativeTextElement =
        editRanges(el, startChar, endChar, toggle) { if (on) onValue(toggle) else null }

    /** The value Rnote's typewriter buttons set. */
    private fun onValue(toggle: TextToggle): JsonElement = when (toggle) {
        TextToggle.BOLD -> JsonPrimitive(TextFormatting.BOLD_WEIGHT)
        TextToggle.ITALIC -> JsonPrimitive("italic")
        TextToggle.UNDERLINE, TextToggle.STRIKETHROUGH -> JsonPrimitive(true)
    }

    /**
     * The shared part of [toggleFormat] and [setFormat]: the ranges of [toggle]'s kind that
     * meet the selection are cut back to outside it (split around it if they reach past
     * both ends), then [newValue] — given those ranges as they were — goes over the whole
     * selection, or nothing does when it returns null.
     */
    private inline fun editRanges(
        el: NativeTextElement,
        startChar: Int,
        endChar: Int,
        toggle: TextToggle,
        newValue: (intersecting: List<JsonElement>) -> JsonElement?
    ): NativeTextElement {
        val s = TextFormatting.byteIndex(el.text, minOf(startChar, endChar))
        val e = TextFormatting.byteIndex(el.text, maxOf(startChar, endChar))
        if (s >= e) return el
        val obj = el.raw?.takeIf { it.isJsonObject }?.deepCopy()?.asJsonObject ?: textJson(el)
        val style = obj.get("text_style")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: JsonObject().also { obj.add("text_style", it) }
        val existing = style.get("ranged_text_attributes")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

        val others = JsonArray()
        val matching = mutableListOf<JsonElement>()
        for (item in existing) {
            if (isRange(item) && attributeKey(item) == toggle.key) matching += item else others.add(item)
        }
        val intersecting = matching.filter { rangeEnd(it) > s && rangeStart(it) < e }
        val retained = matching.filter { !(rangeEnd(it) > s && rangeStart(it) < e) }
        val value = newValue(intersecting)

        val result = others
        retained.forEach { result.add(it) }
        for (item in intersecting) {
            val a = rangeStart(item)
            val b = rangeEnd(item)
            // Rnote's remove_intersecting_attrs_in_range: keep what lies outside [s, e).
            if (a < s) result.add(withRange(item, a, s))
            if (b > e) result.add(withRange(item, e, b))
        }
        if (value != null) {
            result.add(JsonObject().apply {
                add("range", JsonObject().apply { addProperty("start", s); addProperty("end", e) })
                add("attribute", JsonObject().apply { add(toggle.key, value) })
            })
        }
        style.add("ranged_text_attributes", mergeTouching(result))
        return parseText(obj) ?: el
    }

    private fun isRange(e: JsonElement): Boolean {
        if (!e.isJsonObject) return false
        val o = e.asJsonObject
        return o.get("range")?.isJsonObject == true && o.get("attribute")?.isJsonObject == true
    }

    private fun attributeKey(e: JsonElement): String? =
        e.asJsonObject.getAsJsonObject("attribute").keySet().singleOrNull()

    private fun rangeStart(e: JsonElement) = e.asJsonObject.getAsJsonObject("range").get("start").asInt
    private fun rangeEnd(e: JsonElement) = e.asJsonObject.getAsJsonObject("range").get("end").asInt

    private fun withRange(e: JsonElement, start: Int, end: Int): JsonElement = e.deepCopy().also {
        it.asJsonObject.add("range", JsonObject().apply { addProperty("start", start); addProperty("end", end) })
    }

    /**
     * Ranges of one attribute and value that touch or overlap made into one: typing with
     * bold switched on adds a character at a time, and a hundred one-letter ranges would
     * mean the same as one. Everything else keeps its place in the list.
     */
    private fun mergeTouching(ranges: JsonArray): JsonArray {
        val out = mutableListOf<JsonElement>()
        for (item in ranges) {
            if (!isRange(item)) { out += item; continue }
            val attribute = item.asJsonObject.get("attribute")
            val i = out.indexOfFirst {
                isRange(it) && it.asJsonObject.get("attribute") == attribute &&
                    rangeStart(it) <= rangeEnd(item) && rangeStart(item) <= rangeEnd(it)
            }
            if (i < 0) {
                out += item
            } else {
                val other = out[i]
                out[i] = withRange(other, minOf(rangeStart(other), rangeStart(item)), maxOf(rangeEnd(other), rangeEnd(item)))
            }
        }
        return JsonArray().apply { out.forEach { add(it) } }
    }

    /** The topmost text box at ([x], [y]), if any; [slop] widens each box for a fingertip. */
    fun textAt(elements: List<NativeCanvasElement>, x: Float, y: Float, slop: Float = 0f): NativeTextElement? =
        elements.lastOrNull {
            it is NativeTextElement &&
                x >= it.minX - slop && x <= it.maxX + slop && y >= it.minY - slop && y <= it.maxY + slop
        } as NativeTextElement?

    /**
     * Moves Rnote's `ranged_text_attributes` from [old] to [new]. Ranges are UTF-8 byte
     * offsets (Rust string indices). The edit is taken to be the part between the two
     * texts' common prefix and suffix: ranges before it stay, ranges after it shift,
     * a range around it grows or shrinks with it, and whatever of a range lay inside the
     * replaced part is dropped.
     */
    internal fun shiftRanges(ranges: JsonArray, old: String, new: String): JsonArray {
        var p = 0
        val maxP = minOf(old.length, new.length)
        while (p < maxP && old[p] == new[p]) p++
        // Never split a surrogate pair: back off to the start of the character.
        if (p in 1 until old.length && Character.isLowSurrogate(old[p])) p--
        var s = 0
        val maxS = minOf(old.length, new.length) - p
        while (s < maxS && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        if (s > 0 && Character.isLowSurrogate(old[old.length - s])) s--

        val prefixEnd = utf8Length(old, 0, p)
        val oldChangeEnd = utf8Length(old, 0, old.length - s)
        val newLength = utf8Length(new, 0, new.length)
        val delta = newLength - utf8Length(old, 0, old.length)

        val out = JsonArray()
        for (item in ranges) {
            if (!item.isJsonObject) continue
            val range = item.asJsonObject.get("range")
            if (range == null || !range.isJsonObject) { out.add(item); continue }
            val a = range.asJsonObject.get("start")?.asInt ?: continue
            val b = range.asJsonObject.get("end")?.asInt ?: continue
            val na = when {
                a <= prefixEnd -> a
                a >= oldChangeEnd -> a + delta
                else -> oldChangeEnd + delta
            }.coerceIn(0, newLength)
            val nb = when {
                b <= prefixEnd -> b
                b >= oldChangeEnd -> b + delta
                else -> prefixEnd
            }.coerceIn(0, newLength)
            if (nb <= na) continue
            val moved = item.asJsonObject.deepCopy()
            moved.add("range", JsonObject().apply {
                addProperty("start", na)
                addProperty("end", nb)
            })
            out.add(moved)
        }
        return out
    }

    private fun utf8Length(s: String, from: Int, to: Int): Int =
        s.substring(from, to).toByteArray(Charsets.UTF_8).size

    /** JSON for a text element that came without the JSON it was read from. */
    private fun textJson(el: NativeTextElement): JsonObject = JsonObject().apply {
        addProperty("text", el.text)
        add("transform", affineJson(el.transform))
        add("text_style", JsonObject().apply {
            addProperty("font_family", el.fontFamily)
            addProperty("font_size", round3(el.fontSize.toDouble()))
            addProperty("font_weight", el.fontWeight)
            addProperty("font_style", if (el.italic) "italic" else "regular")
            add("color", colorJson(el.color))
            val w = el.maxWidth
            if (w != null) addProperty("max_width", round3(w.toDouble())) else add("max_width", JsonNull.INSTANCE)
            addProperty("alignment", el.alignment)
            add("ranged_text_attributes", JsonArray())
        })
    }

    private fun parseText(inner: JsonObject): NativeTextElement? {
        val wrapper = JsonObject().apply { add("textstroke", inner) }
        return RnoteNativeParser.parseElementJson(wrapper.toString()) as? NativeTextElement
    }

    /** Rnote's column-major 3×3 `affine` for our [a, b, c, d, tx, ty]. */
    private fun affineJson(t: FloatArray) = JsonObject().apply {
        add("affine", JsonArray().apply {
            listOf(t[0], t[1], 0f, t[2], t[3], 0f, t[4], t[5], 1f).forEach { add(round3(it.toDouble())) }
        })
    }

    private fun colorJson(c: RnoteNativeColor) = JsonObject().apply {
        addProperty("r", round3(c.r.toDouble()))
        addProperty("g", round3(c.g.toDouble()))
        addProperty("b", round3(c.b.toDouble()))
        addProperty("a", round3(c.a.toDouble()))
    }

    /** Rnote's `TextStyle::FONT_FAMILY_DEFAULT`. */
    const val TEXT_FONT_FAMILY = "serif"
    /** Rnote's typewriter `text_width` default. */
    const val TEXT_WIDTH_DEFAULT = 600f
    private const val TEXT_WIDTH_MIN = 150f
    private const val TEXT_PAGE_MARGIN = 20f
    /** Keeps an image placed in a sliver of a view from collapsing to nothing. */
    private const val IMAGE_SCALE_MIN = 0.01f
}
