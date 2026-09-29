package io.github.kjly.brna.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.ShapeKind
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.storage.NativeEditing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rnote's selector styles other than the lasso: rectangle, single, intersecting path. */
class SelectionModesTest {

    private val black = RnoteNativeColor(0f, 0f, 0f, 1f)
    private val blue = RnoteNativeColor(0.2f, 0.5f, 0.9f, 1f)

    private fun stroke(vararg points: Pair<Float, Float>, width: Float = 2f) = Stroke(
        points = points.map { (x, y) -> StrokePoint(x, y) },
        color = Color.Black,
        strokeWidth = width
    )

    private fun path(vararg points: Pair<Float, Float>) = points.map { (x, y) -> Offset(x, y) }

    // ── Rectangle ─────────────────────────────────────────────────────────────

    @Test
    fun `a rectangle takes the ink wholly inside it, dragged either way`() {
        val inside = stroke(20f to 20f, 40f to 30f)
        val across = stroke(80f to 50f, 150f to 50f)
        assertEquals(listOf(inside), SelectionManager.strokesInRect(Offset(10f, 10f), Offset(100f, 100f), listOf(inside, across)))
        assertEquals(listOf(inside), SelectionManager.strokesInRect(Offset(100f, 100f), Offset(10f, 10f), listOf(inside, across)))
    }

    @Test
    fun `a rectangle takes shapes and text only when they are wholly inside`() {
        val small = NativeEditing.createShape(ShapeKind.RECTANGLE, 20f, 20f, 40f, 40f, black, 2f)!!
        val large = NativeEditing.createShape(ShapeKind.RECTANGLE, 20f, 20f, 400f, 40f, black, 2f)!!
        assertTrue(NativeEditing.insideRect(small, 10f, 10f, 100f, 100f))
        assertFalse(NativeEditing.insideRect(large, 10f, 10f, 100f, 100f))
    }

    // ── Intersecting path ─────────────────────────────────────────────────────

    @Test
    fun `a line drawn through ink takes what it crosses and nothing else`() {
        val crossed = stroke(50f to 0f, 50f to 100f)
        val beside = stroke(200f to 0f, 200f to 100f)
        val line = path(0f to 50f, 50f to 50f, 100f to 50f)
        assertEquals(listOf(crossed), SelectionManager.strokesCrossedByPath(line, listOf(crossed, beside)))
    }

    @Test
    fun `touching the ink's width is enough, as with Rnote's hitboxes`() {
        val thick = stroke(0f to 50f, 100f to 50f, width = 20f)
        // Passes 8 above the line: inside its half width of 10.
        val line = path(0f to 42f, 50f to 42f, 100f to 42f)
        assertEquals(listOf(thick), SelectionManager.strokesCrossedByPath(line, listOf(thick)))
    }

    @Test
    fun `a path too short to be a line takes nothing, as in Rnote`() {
        val crossed = stroke(50f to 0f, 50f to 100f)
        assertTrue(SelectionManager.strokesCrossedByPath(path(0f to 50f, 100f to 50f), listOf(crossed)).isEmpty())
    }

    @Test
    fun `a line through a shape's empty middle misses it, unless the shape is filled`() {
        val empty = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 100f, 100f, black, 2f)!!
        val filled = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 100f, 100f, black, 2f, blue)!!
        val inside = listOf(30f to 50f, 50f to 50f, 70f to 50f)
        assertFalse(NativeEditing.crossedByPath(empty, inside))
        assertTrue(NativeEditing.crossedByPath(filled, inside))
        assertTrue(NativeEditing.crossedByPath(empty, listOf(-20f to 50f, 20f to 50f, 40f to 50f)))
    }

    // ── Single ────────────────────────────────────────────────────────────────

    @Test
    fun `a tap near a line takes it, the one on top when two cross`() {
        val below = stroke(0f to 50f, 100f to 50f)
        val above = stroke(50f to 0f, 50f to 100f)
        assertEquals(above, SelectionManager.strokeAt(Offset(51f, 50f), listOf(below, above), tolerance = 2f))
        assertEquals(below, SelectionManager.strokeAt(Offset(20f, 52f), listOf(below, above), tolerance = 2f))
        assertNull(SelectionManager.strokeAt(Offset(20f, 70f), listOf(below, above), tolerance = 2f))
    }

    @Test
    fun `a tap takes shapes and text over ink, and ink over pictures`() {
        val ink = stroke(0f to 50f, 100f to 50f)
        val filled = NativeEditing.createShape(ShapeKind.RECTANGLE, 40f, 40f, 60f, 60f, black, 2f, blue)!!
        val empty = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 100f, 100f, black, 2f)!!
        val picture = NativeEditing.createImage(twoPixels, 2, 1, NativeEditing.ImagePlacement(0f, 0f, 100f))!!

        val onFill = SelectionManager.pickAt(Offset(50f, 50f), listOf(ink), listOf(picture, filled), 2f)
        assertEquals(SelectionManager.Pick.Element(filled), onFill)
        // Inside an unfilled shape is not on it: the ink under the tap is.
        val onInk = SelectionManager.pickAt(Offset(20f, 50f), listOf(ink), listOf(picture, empty), 2f)
        assertEquals(SelectionManager.Pick.Ink(ink), onInk)
        val onPicture = SelectionManager.pickAt(Offset(20f, 20f), listOf(ink), listOf(picture), 2f)
        assertTrue(onPicture is SelectionManager.Pick.Element && onPicture.element === picture)
        assertNull(SelectionManager.pickAt(Offset(500f, 500f), listOf(ink), listOf(picture, empty), 2f))
    }

    // ── Colours ───────────────────────────────────────────────────────────────

    @Test
    fun `recolouring ink keeps a marker translucent`() {
        val pen = stroke(0f to 0f, 10f to 0f)
        val marker = pen.copy(id = "m", color = Color(1f, 1f, 0f, 0.35f), isHighlighter = true)
        val (newPen, newMarker) = SelectionManager.recolored(listOf(pen, marker), Color.Red)
        assertEquals(Color.Red, newPen.color)
        assertEquals(0.35f, newMarker.color.alpha, 1e-3f)
        assertEquals(1f, newMarker.color.red, 1e-3f)
        assertEquals(0f, newMarker.color.green, 1e-3f)
    }

    /** 2 × 1 pixels of premultiplied RGBA. */
    private val twoPixels = java.util.Base64.getEncoder().encodeToString(
        byteArrayOf(-1, 0, 0, -1, -128, -128, -128, -128)
    )
}
