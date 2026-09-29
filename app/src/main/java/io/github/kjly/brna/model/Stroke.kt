package io.github.kjly.brna.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * The curve a stroke's segment takes to its end point, where Rnote drew one: its "Curved"
 * pen path writes cubic segments, older files quadratic ones. A straight line — what the
 * app draws, and by far the most common — has none.
 */
sealed class SegmentCurve {
    /** Rnote's `quadbezto`, with its one control point. */
    data class Quad(val cx: Float, val cy: Float) : SegmentCurve()

    /** Rnote's `cubbezto`, with its two control points. */
    data class Cubic(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float) : SegmentCurve()

    /** The same curve with its control points put through [x] and [y], as a stroke's points are moved. */
    inline fun mapped(x: (Float, Float) -> Float, y: (Float, Float) -> Float): SegmentCurve = when (this) {
        is Quad -> Quad(x(cx, cy), y(cx, cy))
        is Cubic -> Cubic(x(c1x, c1y), y(c1x, c1y), x(c2x, c2y), y(c2x, c2y))
    }
}

/**
 * One of a stroke's elements: where the pen was and how hard it pressed. [curve] is the
 * curve the segment from the previous point takes to this one, if it is not straight; the
 * first point of a stroke has no segment, and its curve means nothing.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = PRESSURE_DEFAULT,
    val curve: SegmentCurve? = null
) {
    companion object {
        /**
         * Rnote's `Element::PRESSURE_DEFAULT` — what it records when the input device
         * reports no pressure of its own. Desktop mouse strokes are written entirely at
         * this value, so matching it is what makes a finger-drawn stroke here the same
         * size as a mouse-drawn one there.
         */
        const val PRESSURE_DEFAULT = 0.5f
    }
}

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    /**
     * Ink colour, alpha included — the sole place a stroke's opacity lives, matching
     * .rnote, which carries it in `stroke_color`. There was a separate `alpha` field
     * alongside this; because a Marker's colour already carries its 0.35, every renderer
     * that multiplied the two drew markers at 0.12.
     */
    val color: Color,
    /**
     * The stroke's nominal width, i.e. Rnote's `SmoothOptions::stroke_width`. This is a
     * maximum, not the painted thickness: [pressureCurve] and each point's pressure
     * decide that. Never bake pressure into this value — the .rnote file carries the
     * per-point pressures too, so desktop Rnote would apply it a second time.
     */
    val strokeWidth: Float = 3f,
    val toolType: ToolType = ToolType.BRUSH,
    val isHighlighter: Boolean = false,
    /** Rnote's default for a Solid brush; its Marker brush uses [PressureCurve.CONST]. */
    val pressureCurve: PressureCurve = PressureCurve.DEFAULT,
    /** Set for a stroke in Rnote's Textured style, drawn as dots rather than an outline. */
    val textured: TexturedStyle? = null,
    /** The stroke as a desktop file had it, for as long as it is unchanged; see [RnoteStrokeSource]. */
    val source: RnoteStrokeSource? = null
) {
    // Legacy alias so existing DrawingCanvas references to stroke.width still compile
    val width: Float get() = strokeWidth
}

/**
 * A stroke exactly as a desktop file had it: [json] is the value of its `brushstroke`, the
 * very text the file held, and [stroke] the stroke it was read as. While a stroke is still
 * that stroke — nothing moved, recoloured, cut or reshaped — a save writes [json] back
 * instead of writing the stroke anew, so a note opened and saved here leaves the desktop's
 * strokes untouched to the last digit. Whatever changes a stroke changes one of the
 * fields compared, so no edit can be written over by a stale original.
 */
class RnoteStrokeSource(val json: String, stroke: Stroke) {
    /** The stroke as read, without an id or a source of its own to compare. */
    private val read: Stroke = stroke.copy(id = "", source = null)

    /** Whether [current] is still the stroke [json] holds. */
    fun describes(current: Stroke): Boolean = current.copy(id = "", source = null) == read
}
