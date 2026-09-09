package com.rnote.baby.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.rnote.baby.model.Stroke

/**
 * The drawing surface an export paints onto, in document coordinates. It exists so the
 * page/pattern/ink painting is written once ([DocumentPainter]) and can land on either an
 * `android.graphics.Canvas` (PNG, JPEG, PDF) or an SVG string — the same split the ink
 * outlines already make through `StrokeOutline.Sink`.
 */
interface ExportCanvas {

    fun fillRect(rect: Rect, color: Color)

    fun fillRoundRect(rect: Rect, cornerRadius: Float, color: Color)

    fun fillPolygon(points: List<Offset>, color: Color)

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color)

    /** Fills one ink stroke's outline. */
    fun fillStroke(stroke: Stroke)

    /** Runs [block] with drawing clipped to [rect]. */
    fun clipped(rect: Rect, block: () -> Unit)
}
