package io.github.kjly.brna.model

import androidx.compose.ui.geometry.Offset

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
