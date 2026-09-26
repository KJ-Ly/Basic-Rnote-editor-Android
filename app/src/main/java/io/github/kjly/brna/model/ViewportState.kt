package io.github.kjly.brna.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor

data class ViewportState(
    val panOffset: Offset = Offset.Zero,
    /** User-facing zoom, the number shown as a percentage in the top bar. */
    val zoomScale: Float = 1.0f,
    /**
     * Device px per canvas unit at 100% zoom.
     *
     * Canvas units are defined at [CANVAS_DPI] (96/inch), the same basis desktop Rnote
     * uses, so on a ~96 dpi desktop display this is 1.0 and one canvas px is one device
     * px. An Android panel is far denser (~275 dpi on an 11" tablet), and drawing 1:1
     * there made "100%" nearly three times physically smaller than the same document on
     * the desktop — dots 0.70" apart against desktop's 1.98" at the same percentage.
     * Folding the display's physical scale in here keeps a canvas unit a constant
     * *physical* size, so a percentage means the same thing on every device and Rnote's
     * own [ZOOM_MIN]/[ZOOM_MAX] carry over verbatim instead of needing a per-device
     * ceiling to compensate.
     */
    val displayScale: Float = 1.0f
) {
    /**
     * Canvas unit -> device px, user zoom and display scale combined. Every screen<->canvas
     * conversion and every screen-space size derived from a canvas-space one must go
     * through this rather than [zoomScale], which is only the number shown to the user.
     */
    val effectiveScale: Float get() = zoomScale * displayScale

    /**
     * Converts a screen pixel offset into document canvas space coordinates.
     */
    fun screenToCanvas(screenOffset: Offset): Offset {
        return (screenOffset - panOffset) / effectiveScale
    }

    /**
     * Converts document canvas space coordinates into screen pixel offset.
     */
    fun canvasToScreen(canvasOffset: Offset): Offset {
        return (canvasOffset * effectiveScale) + panOffset
    }

    /**
     * Desktop Rnote's `return_to_origin_page` (crates/rnote-ui/src/canvas/mod.rs): puts
     * the origin page back in view at the zoom you are already working at, rather than
     * throwing that zoom away the way a full reset does. Rnote centres the page across
     * the viewport when it fits and goes to its left edge when it doesn't, which is what
     * the two branches here are.
     *
     * [pageWidthPx] is the page width in canvas units, or zero for a document with no
     * pages to centre — an unbounded canvas simply lands on its origin.
     */
    fun returnedToOrigin(viewportWidthPx: Float, pageWidthPx: Float): ViewportState {
        val pageScreenWidth = pageWidthPx * effectiveScale
        val x = if (pageWidthPx > 0f && pageScreenWidth + 2f * ORIGIN_MARGIN_PX <= viewportWidthPx) {
            (viewportWidthPx - pageScreenWidth) / 2f
        } else {
            ORIGIN_MARGIN_PX
        }
        return copy(panOffset = Offset(x, ORIGIN_MARGIN_PX))
    }

    /**
     * [returnedToOrigin] for any page: the page whose top-left corner is at ([left], [top])
     * comes into view the same way — centred when it fits, else from its left edge — at
     * the zoom already in use. The page overview jumps with this.
     */
    fun showingPage(left: Float, top: Float, pageWidthPx: Float, viewportWidthPx: Float): ViewportState {
        val atOrigin = returnedToOrigin(viewportWidthPx, pageWidthPx)
        return atOrigin.copy(panOffset = atOrigin.panOffset - Offset(left, top) * effectiveScale)
    }

    /**
     * Zoomed to [newZoom] (clamped) with the document point under [anchor] — a screen
     * position, the middle of the view for Rnote's zoom keys — kept where it is.
     */
    fun zoomedAround(anchor: Offset, newZoom: Float): ViewportState {
        val zoom = newZoom.coerceIn(ZOOM_MIN, ZOOM_MAX)
        val point = screenToCanvas(anchor)
        return copy(zoomScale = zoom, panOffset = anchor - point * (zoom * displayScale))
    }

    /**
     * Desktop Rnote's Zoom to Page Width (`zoom-fit-width` in rnote-ui's actions.rs): the
     * zoom at which a page of [pageWidthPx] and Rnote's [FIT_WIDTH_OVERSHOOT] on either
     * side fill the view's width, about its middle. Rnote's camera then keeps a page
     * layout's column in view, which centres it; here the page under the middle of the
     * view is centred across it, so it does the same on every layout. Unchanged when
     * there is no page, or no view yet, to fit.
     */
    fun fittedToWidth(viewportWidthPx: Float, viewportHeightPx: Float, pageWidthPx: Float): ViewportState {
        if (pageWidthPx <= 0f || viewportWidthPx <= 0f) return this
        val middle = Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
        val zoomed = zoomedAround(middle, viewportWidthPx / (pageWidthPx + 2f * FIT_WIDTH_OVERSHOOT) / displayScale)
        val pageLeft = floor(zoomed.screenToCanvas(middle).x / pageWidthPx) * pageWidthPx
        val x = middle.x - (pageLeft + pageWidthPx / 2f) * zoomed.effectiveScale
        return zoomed.copy(panOffset = Offset(x, zoomed.panOffset.y))
    }

    /**
     * Rnote's Offset Camera tool: the view moved so the document point [grab] is under the
     * pen at [screen] — the page taken hold of and dragged.
     */
    fun offsetTo(grab: Offset, screen: Offset): ViewportState = copy(panOffset = screen - grab * effectiveScale)

    /**
     * Rnote's Zoom tool, one step of a drag: [dy] screen px down zooms out, up zooms in, by
     * `DRAG_ZOOM_MAGN_ZOOM_FACTOR` per desktop pixel, about [anchor] (a screen position,
     * where the drag began), whose document point stays put. A step past [ZOOM_MIN] or
     * [ZOOM_MAX] leaves the zoom as it is, as Rnote's does.
     */
    fun dragZoomed(anchor: Offset, dy: Float): ViewportState {
        // A desktop pixel is a 96th of an inch, which is what a display-scale unit is here.
        val newZoom = zoomScale * (1f - dy / displayScale * DRAG_ZOOM_FACTOR)
        if (newZoom < ZOOM_MIN || newZoom > ZOOM_MAX) return this
        return zoomedAround(anchor, newZoom)
    }

    /**
     * Clamps and returns a new ViewportState with updated zoom and pan.
     */
    fun update(newPan: Offset, newZoom: Float): ViewportState {
        return copy(panOffset = newPan, zoomScale = newZoom.coerceIn(ZOOM_MIN, ZOOM_MAX))
    }

    companion object {
        /**
         * Desktop Rnote's `Camera::ZOOM_MIN` / `ZOOM_MAX`
         * (crates/rnote-engine/src/camera.rs), used verbatim — which is only meaningful
         * because [displayScale] makes a zoom figure device-independent.
         */
        const val ZOOM_MIN = 0.2f
        const val ZOOM_MAX = 6.0f

        /** Rnote's `Camera::DRAG_ZOOM_MAGN_ZOOM_FACTOR`: the Zoom tool's change per pixel dragged. */
        const val DRAG_ZOOM_FACTOR = 0.005f

        /** Rnote's `RnCanvas::ZOOM_SCROLL_STEP`: one press of a zoom key is 10 %, in or out. */
        const val ZOOM_STEP = 0.1f

        /** Rnote's `Camera::OVERSHOOT_HORIZONTAL`, in document units: the room beside a page fitted to the width. */
        const val FIT_WIDTH_OVERSHOOT = 96f

        /**
         * Gap left between the origin and the corner of the screen by
         * [returnedToOrigin]. Rnote's own overshoot is in document units and so grows
         * with zoom; this one is screen-space, because a margin whose job is to keep the
         * page off the edge of a phone should not become half the screen at 600%.
         */
        const val ORIGIN_MARGIN_PX = 24f

        /**
         * Physical scale of a display in device px per canvas unit.
         *
         * `xdpi`/`ydpi` are the real panel dimensions where the vendor reports them
         * honestly, but they are occasionally nonsense (emulators, some OEMs), so they
         * are sanity-checked against the density bucket and discarded if wildly out of
         * step with it. The bucket is a UI-sizing figure rather than a physical one, so
         * it's the fallback, not the first choice.
         */
        fun displayScaleFor(xdpi: Float, ydpi: Float, densityDpi: Int): Float {
            val reported = (xdpi + ydpi) / 2f
            val bucket = densityDpi.toFloat()
            val dpi = if (reported > 0f && reported >= bucket * 0.5f && reported <= bucket * 1.5f) {
                reported
            } else {
                bucket
            }
            return dpi / CANVAS_DPI
        }
    }
}
