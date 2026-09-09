package com.rnote.baby.model

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportStateTest {

    private val eps = 1e-3f

    private fun assertOffsetEquals(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }

    @Test
    fun `screen and canvas conversions are inverses`() {
        val viewport = ViewportState(
            panOffset = Offset(120f, -45f),
            zoomScale = 1.75f,
            displayScale = 2.86f
        )
        val canvas = Offset(310f, 92.5f)
        assertOffsetEquals(canvas, viewport.screenToCanvas(viewport.canvasToScreen(canvas)))
    }

    @Test
    fun `conversions use display scale, not the percentage shown to the user`() {
        // Converting through zoomScale alone is what made a document nearly three times
        // physically smaller on a tablet panel than on a 96 dpi desktop at the same "100%".
        val viewport = ViewportState(zoomScale = 1f, displayScale = 2.5f)
        assertEquals(2.5f, viewport.effectiveScale, eps)
        assertOffsetEquals(Offset(250f, 500f), viewport.canvasToScreen(Offset(100f, 200f)))
        assertOffsetEquals(Offset(100f, 200f), viewport.screenToCanvas(Offset(250f, 500f)))
    }

    @Test
    fun `pan offset is applied in screen pixels, unscaled`() {
        val viewport = ViewportState(panOffset = Offset(50f, 10f), zoomScale = 2f)
        assertOffsetEquals(Offset(250f, 210f), viewport.canvasToScreen(Offset(100f, 100f)))
    }

    @Test
    fun `update clamps zoom to Rnote's camera limits and leaves pan alone`() {
        val viewport = ViewportState()
        assertEquals(ViewportState.ZOOM_MAX, viewport.update(Offset.Zero, 99f).zoomScale, eps)
        assertEquals(ViewportState.ZOOM_MIN, viewport.update(Offset.Zero, 0.001f).zoomScale, eps)
        val panned = viewport.update(Offset(7f, 8f), 2f)
        assertOffsetEquals(Offset(7f, 8f), panned.panOffset)
        assertEquals(2f, panned.zoomScale, eps)
    }

    @Test
    fun `display scale uses reported panel dpi when it is plausible`() {
        assertEquals(275f / CANVAS_DPI, ViewportState.displayScaleFor(276f, 274f, 280), eps)
    }

    @Test
    fun `display scale falls back to the density bucket when panel dpi is nonsense`() {
        // Emulators and some OEMs report xdpi/ydpi wildly out of step with the bucket.
        assertEquals(280f / CANVAS_DPI, ViewportState.displayScaleFor(1000f, 1000f, 280), eps)
        assertEquals(280f / CANVAS_DPI, ViewportState.displayScaleFor(20f, 20f, 280), eps)
        assertEquals(280f / CANVAS_DPI, ViewportState.displayScaleFor(0f, 0f, 280), eps)
    }

    @Test
    fun `a 96 dpi display draws one canvas unit per device pixel`() {
        assertEquals(1f, ViewportState.displayScaleFor(96f, 96f, 96), eps)
    }
}
