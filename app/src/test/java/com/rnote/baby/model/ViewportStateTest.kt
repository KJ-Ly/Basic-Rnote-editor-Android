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

    @Test
    fun `returning to origin centres a page that fits and keeps the zoom`() {
        val viewport = ViewportState(
            panOffset = Offset(-4000f, 9000f), zoomScale = 0.5f, displayScale = 2f
        )
        // 800 canvas units at an effective scale of 1.0 leaves 1200 of the 2000px wide
        // viewport to split either side of the page.
        val returned = viewport.returnedToOrigin(viewportWidthPx = 2000f, pageWidthPx = 800f)
        assertOffsetEquals(Offset(600f, ViewportState.ORIGIN_MARGIN_PX), returned.panOffset)
        assertEquals(0.5f, returned.zoomScale, eps)
        assertEquals(2f, returned.displayScale, eps)
    }

    @Test
    fun `returning to origin goes to the left edge of a page too wide to fit`() {
        val viewport = ViewportState(panOffset = Offset(700f, -300f), zoomScale = 4f)
        val returned = viewport.returnedToOrigin(viewportWidthPx = 1000f, pageWidthPx = 800f)
        assertOffsetEquals(
            Offset(ViewportState.ORIGIN_MARGIN_PX, ViewportState.ORIGIN_MARGIN_PX),
            returned.panOffset
        )
    }

    @Test
    fun `a document with no pages just lands on its origin`() {
        val returned = ViewportState(panOffset = Offset(-90f, -120f))
            .returnedToOrigin(viewportWidthPx = 1000f, pageWidthPx = 0f)
        assertOffsetEquals(
            Offset(ViewportState.ORIGIN_MARGIN_PX, ViewportState.ORIGIN_MARGIN_PX),
            returned.panOffset
        )
    }

    @Test
    fun `the origin is on screen after returning to it`() {
        val returned = ViewportState(panOffset = Offset(-8000f, -8000f), zoomScale = 3f)
            .returnedToOrigin(viewportWidthPx = 1400f, pageWidthPx = 800f)
        val onScreen = returned.canvasToScreen(Offset.Zero)
        assertEquals(ViewportState.ORIGIN_MARGIN_PX, onScreen.x, eps)
        assertEquals(ViewportState.ORIGIN_MARGIN_PX, onScreen.y, eps)
    }
}
