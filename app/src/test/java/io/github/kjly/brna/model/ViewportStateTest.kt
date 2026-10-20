package io.github.kjly.brna.model

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
    fun `zooming about a point keeps that point where it is on screen`() {
        val view = ViewportState(panOffset = Offset(30f, -40f), zoomScale = 1.5f, displayScale = 2f)
        val middle = Offset(400f, 300f)
        val under = view.screenToCanvas(middle)
        val zoomed = view.zoomedAround(middle, 1.5f * 1.1f)
        assertEquals(1.65f, zoomed.zoomScale, eps)
        assertOffsetEquals(middle, zoomed.canvasToScreen(under))
        // Clamped to Rnote's limits like any other zoom.
        assertEquals(ViewportState.ZOOM_MAX, view.zoomedAround(middle, 100f).zoomScale, eps)
    }

    @Test
    fun `the Offset Camera tool keeps the point taken hold of under the pen`() {
        val v = ViewportState(panOffset = Offset(10f, 20f), zoomScale = 2f, displayScale = 1.5f)
        val grab = v.screenToCanvas(Offset(100f, 100f))
        val moved = v.offsetTo(grab, Offset(160f, 40f))
        assertEquals(160f, moved.canvasToScreen(grab).x, 1e-3f)
        assertEquals(40f, moved.canvasToScreen(grab).y, 1e-3f)
        assertEquals(2f, moved.zoomScale, 0f)
    }

    @Test
    fun `the Zoom tool zooms in dragging up, about where it began, as Rnote's does`() {
        val v = ViewportState(panOffset = Offset(10f, 20f), zoomScale = 1f, displayScale = 2f)
        val anchor = Offset(200f, 300f)
        val point = v.screenToCanvas(anchor)
        // 40 device px up at 2 px per desktop pixel: 20 desktop pixels, 1 + 20 × 0.005.
        val zoomed = v.dragZoomed(anchor, -40f)
        assertEquals(1.1f, zoomed.zoomScale, 1e-5f)
        assertEquals(anchor.x, zoomed.canvasToScreen(point).x, 1e-3f)
        assertEquals(anchor.y, zoomed.canvasToScreen(point).y, 1e-3f)
        assertEquals(0.9f, v.dragZoomed(anchor, 40f).zoomScale, 1e-5f)
    }

    @Test
    fun `a Zoom tool step past Rnote's limits leaves the zoom where it is`() {
        val v = ViewportState(zoomScale = ViewportState.ZOOM_MAX - 0.01f)
        assertEquals(v, v.dragZoomed(Offset(5f, 5f), -100f))
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

    @Test
    fun `jumping to a page centres it at the top, at the same zoom`() {
        val viewport = ViewportState(panOffset = Offset(-5000f, 300f), zoomScale = 0.5f, displayScale = 2f)
        // Page 3 of an A4 column: top at 2 * 1122.5, 800 wide at an effective scale of 1.
        val shown = viewport.showingPage(0f, 2245f, 800f, viewportWidthPx = 2000f)
        assertOffsetEquals(Offset(600f, ViewportState.ORIGIN_MARGIN_PX - 2245f), shown.panOffset)
        assertEquals(0.5f, shown.zoomScale, eps)
        assertOffsetEquals(Offset(600f, ViewportState.ORIGIN_MARGIN_PX), shown.canvasToScreen(Offset(0f, 2245f)))
    }

    @Test
    fun `jumping to a page to the right of the origin brings its left edge in`() {
        val viewport = ViewportState(zoomScale = 2f)
        val shown = viewport.showingPage(800f, 0f, 800f, viewportWidthPx = 1000f)
        assertOffsetEquals(
            Offset(ViewportState.ORIGIN_MARGIN_PX, ViewportState.ORIGIN_MARGIN_PX),
            shown.canvasToScreen(Offset(800f, 0f))
        )
    }

    @Test
    fun `zoom to page width fits the page and Rnote's overshoot across the view, centred`() {
        // The middle of the view is over the first page, at x = 400.
        val viewport = ViewportState(panOffset = Offset(400f, -700f), zoomScale = 0.5f, displayScale = 2f)
        val fitted = viewport.fittedToWidth(viewportWidthPx = 1600f, viewportHeightPx = 2400f, pageWidthPx = 800f)
        // 800 units of page and 96 either side across 1600 px.
        assertEquals(1600f / (800f + 2f * ViewportState.FIT_WIDTH_OVERSHOOT), fitted.effectiveScale, eps)
        val left = fitted.canvasToScreen(Offset(0f, 0f)).x
        val right = fitted.canvasToScreen(Offset(800f, 0f)).x
        assertEquals(1600f - right, left, eps)
    }

    @Test
    fun `zoom to page width keeps the middle of the view where it is, up and down`() {
        val viewport = ViewportState(panOffset = Offset(40f, -900f), zoomScale = 1f, displayScale = 2f)
        val middle = Offset(800f, 1200f)
        val before = viewport.screenToCanvas(middle).y
        val fitted = viewport.fittedToWidth(1600f, 2400f, 800f)
        assertEquals(before, fitted.screenToCanvas(middle).y, eps)
    }

    @Test
    fun `zoom to page width does nothing without a page`() {
        val viewport = ViewportState(panOffset = Offset(12f, 34f), zoomScale = 1.3f)
        assertEquals(viewport, viewport.fittedToWidth(1600f, 2400f, 0f))
    }

    @Test
    fun `zoom to page width centres the page under the middle of the view, not the first`() {
        // The middle is at x = 1100, over the second page of an infinite layout's row.
        val viewport = ViewportState(panOffset = Offset(-300f, 0f), zoomScale = 0.5f, displayScale = 2f)
        val fitted = viewport.fittedToWidth(1600f, 2400f, 800f)
        val left = fitted.canvasToScreen(Offset(800f, 0f)).x
        val right = fitted.canvasToScreen(Offset(1600f, 0f)).x
        assertEquals(1600f - right, left, eps)
    }

    @Test
    fun `zoom to real size makes an inch of the page an inch of the panel`() {
        // A panel of 264 px per inch; the page a 96 dpi A4, as Rnote makes it.
        val viewport = ViewportState(panOffset = Offset(-500f, 80f), zoomScale = 2.4f, displayScale = 264f / 96f)
        val real = viewport.zoomedToRealSize(1600f, 2400f, formatDpi = 96f)
        assertEquals(1f, real.zoomScale, eps)
        assertEquals(264f, real.effectiveScale * 96f, eps)
        // At 300 dpi an inch is 300 units, and still an inch across.
        val dense = viewport.zoomedToRealSize(1600f, 2400f, formatDpi = 300f)
        assertEquals(264f, dense.effectiveScale * 300f, eps)
    }

    @Test
    fun `zoom to real size keeps what is in the middle of the view there`() {
        val viewport = ViewportState(panOffset = Offset(-500f, 80f), zoomScale = 2.4f, displayScale = 2.75f)
        val middle = Offset(800f, 1200f)
        val before = viewport.screenToCanvas(middle)
        assertOffsetEquals(before, viewport.zoomedToRealSize(1600f, 2400f, 96f).screenToCanvas(middle))
        // A format of no dpi is none to zoom to.
        assertEquals(viewport, viewport.zoomedToRealSize(1600f, 2400f, 0f))
    }

    // A4 at 96 dpi, as Rnote makes it.
    private val a4w = 793.7f
    private val a4h = 1122.5f

    @Test
    fun `a Fixed Size document keeps the view to its pages and an inch around them`() {
        val bounds = ViewportState.boundsFor(LayoutMode.FIXED_SIZE, a4w, a4h, fixedPages = 2, contentHeight = 0f)
        assertEquals(ViewBounds(0f, 0f, a4w, a4h * 2), bounds)
        // 2 device px per desktop px: the overshoot is 192 px of the panel.
        val view = ViewportState(zoomScale = 1f, displayScale = 2f)
        // Dragged far up and left: stops with the overshoot above and before the page.
        assertOffsetEquals(Offset(192f, 192f), view.copy(panOffset = Offset(5000f, 5000f)).clampedTo(bounds, 1000f, 800f).panOffset)
        // Far down and right: the last of the pages and the overshoot after them at the view's far edge.
        val end = view.copy(panOffset = Offset(-99999f, -99999f)).clampedTo(bounds, 1000f, 800f)
        assertEquals(1000f, end.canvasToScreen(Offset(a4w, 0f)).x + 192f, eps)
        assertEquals(800f, end.canvasToScreen(Offset(0f, a4h * 2)).y + 192f, eps)
        // Anywhere in between is left as it is.
        val inside = view.copy(panOffset = Offset(-300f, -900f))
        assertEquals(inside, inside.clampedTo(bounds, 1000f, 800f))
    }

    @Test
    fun `a page narrower than the view sits at its left, the overshoot before it, as in Rnote`() {
        val bounds = ViewportState.boundsFor(LayoutMode.FIXED_SIZE, a4w, a4h, fixedPages = 1, contentHeight = 0f)
        val view = ViewportState(panOffset = Offset(700f, 0f), zoomScale = 0.2f, displayScale = 2f)
        assertEquals(192f, view.clampedTo(bounds, 3000f, 2000f).panOffset.x, eps)
    }

    @Test
    fun `Semi Infinite bounds only the top and the left`() {
        val bounds = ViewportState.boundsFor(LayoutMode.SEMI_INFINITE, a4w, a4h, fixedPages = 1, contentHeight = 0f)
        val view = ViewportState(zoomScale = 1.5f, displayScale = 2.75f)
        val over = 96f * 2.75f
        assertOffsetEquals(Offset(over, over), view.copy(panOffset = Offset(4000f, 4000f)).clampedTo(bounds, 1600f, 2400f).panOffset)
        val far = view.copy(panOffset = Offset(-1e6f, -1e6f))
        assertEquals(far, far.clampedTo(bounds, 1600f, 2400f))
    }

    @Test
    fun `Continuous Vertical reaches a page past what is on it, Infinite anywhere`() {
        val bounds = ViewportState.boundsFor(LayoutMode.CONTINUOUS_VERTICAL, a4w, a4h, fixedPages = 1, contentHeight = 3000f)
        assertEquals(ViewBounds(0f, 0f, a4w, 3000f + a4h), bounds)
        assertEquals(null, ViewportState.boundsFor(LayoutMode.INFINITE, a4w, a4h, 1, 0f))
        val far = ViewportState(panOffset = Offset(1e6f, -1e6f))
        assertEquals(far, far.clampedTo(null, 1600f, 2400f))
        // No page to go by, no bounds.
        assertEquals(null, ViewportState.boundsFor(LayoutMode.FIXED_SIZE, 0f, 0f, 1, 0f))
    }
}
