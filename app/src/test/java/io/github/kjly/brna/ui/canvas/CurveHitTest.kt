package io.github.kjly.brna.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.SegmentCurve
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stroke from Rnote's "Curved" pen path is hit where its curve runs, as Rnote's
 * hitboxes have it, not along the straight line between its ends: a curve from (0, 0) to
 * (100, 0) bulging up to y = 37.5.
 */
class CurveHitTest {

    private val curved = Stroke(
        points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(100f, 0f, curve = SegmentCurve.Cubic(0f, 50f, 100f, 50f))
        ),
        color = Color.Black,
        strokeWidth = 2f
    )

    @Test
    fun `the eraser hits the curve, not the line between its ends`() {
        val onCurve = EraserHitTest.eraserBounds(Offset(50f, 37f), 4f)
        val onChord = EraserHitTest.eraserBounds(Offset(50f, 0f), 4f)
        assertEquals(listOf(curved), EraserHitTest.collidingStrokes(onCurve, listOf(curved)))
        assertTrue(EraserHitTest.collidingStrokes(onChord, listOf(curved)).isEmpty())
        // Splitting cuts its one segment out; nothing on the chord cuts anything.
        assertNotNull(EraserHitTest.splitStroke(onCurve, curved))
        assertNull(EraserHitTest.splitStroke(onChord, curved))
    }

    @Test
    fun `a tap on the curve picks the stroke, one on the chord doesn't`() {
        assertEquals(curved, SelectionManager.strokeAt(Offset(50f, 36f), listOf(curved), 2f))
        assertNull(SelectionManager.strokeAt(Offset(50f, 0f), listOf(curved), 2f))
    }

    @Test
    fun `a box round the ends alone does not hold the curve`() {
        assertTrue(SelectionManager.strokesInRect(Offset(-5f, -5f), Offset(105f, 10f), listOf(curved)).isEmpty())
        assertEquals(listOf(curved), SelectionManager.strokesInRect(Offset(-5f, -5f), Offset(105f, 45f), listOf(curved)))
        // And the selection box reaches up to the curve.
        val box = SelectionManager.calculateBoundingBox(listOf(curved))!!
        assertTrue(box.bottom > 30f)
    }

    @Test
    fun `a line drawn through the curve crosses it, one through the chord does not`() {
        val throughCurve = listOf(Offset(50f, 20f), Offset(50f, 50f), Offset(51f, 60f))
        val throughChord = listOf(Offset(50f, -10f), Offset(50f, 10f), Offset(51f, 12f))
        assertEquals(listOf(curved), SelectionManager.strokesCrossedByPath(throughCurve, listOf(curved)))
        assertFalse(SelectionManager.strokesCrossedByPath(throughChord, listOf(curved)).isNotEmpty())
    }
}
