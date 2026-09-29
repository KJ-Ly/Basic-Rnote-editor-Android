package io.github.kjly.brna.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import io.github.kjly.brna.model.NativeVectorImageElement
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rasterises Rnote vector images (imported PDF pages) once, the way desktop Rnote does
 * (`VectorImage::gen_images` renders them to a full image because they are too expensive
 * to redraw per frame).
 */
object VectorImageRenderer {

    /** Longest bitmap side. A PDF page at roughly one pixel per canvas unit fits under it. */
    private const val MAX_SIDE_PX = 2048

    /** Returns null when the SVG can't be parsed or there isn't memory for the bitmap. */
    fun rasterize(el: NativeVectorImageElement): Bitmap? = try {
        val t = el.transform
        val scaleX = sqrt(t[0] * t[0] + t[1] * t[1]).coerceAtLeast(1e-3f)
        val scaleY = sqrt(t[2] * t[2] + t[3] * t[3]).coerceAtLeast(1e-3f)
        var w = 2f * el.halfExtentX * scaleX
        var h = 2f * el.halfExtentY * scaleY
        val longest = max(w, h)
        if (longest > MAX_SIDE_PX) {
            w *= MAX_SIDE_PX / longest
            h *= MAX_SIDE_PX / longest
        }
        val pw = w.roundToInt().coerceAtLeast(1)
        val ph = h.roundToInt().coerceAtLeast(1)

        val svg = SVG.getFromString(el.svgData)
        val bitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Rnote stretches the SVG's intrinsic box over the image rectangle
        // (preserveAspectRatio="none"), so the two axes scale independently.
        canvas.scale(pw / el.intrinsicWidth, ph / el.intrinsicHeight)
        svg.renderToCanvas(
            canvas,
            RenderOptions().viewPort(0f, 0f, el.intrinsicWidth, el.intrinsicHeight)
        )
        bitmap
    } catch (e: Throwable) {
        // OutOfMemoryError included: one page failing to render must not take the app down.
        e.printStackTrace()
        null
    }

    /**
     * A sharper rendering of part of a vector image, for when the view is zoomed in past
     * what the one-off [rasterize] bitmap can resolve. Covers [left]..[bottom] in document
     * coordinates at [pxPerUnit] device pixels per document unit.
     */
    class DetailTile(
        val bitmap: Bitmap,
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val pxPerUnit: Float
    ) {
        fun covers(l: Float, t: Float, r: Float, b: Float): Boolean =
            l >= left && t >= top && r <= right && b <= bottom
    }

    /**
     * Parsed SVGs for [renderRegion], kept because parsing a PDF page's SVG takes far
     * longer than rendering a screenful of it. Two is enough for a page boundary on screen.
     * Only touched from one thread at a time (see DrawingCanvas).
     */
    private val parsedCache = LinkedHashMap<NativeVectorImageElement, SVG>(4, 0.75f, true)
    private const val PARSED_CACHE_SIZE = 2

    private fun parsed(el: NativeVectorImageElement): SVG = synchronized(parsedCache) {
        parsedCache[el] ?: SVG.getFromString(el.svgData).also {
            parsedCache[el] = it
            while (parsedCache.size > PARSED_CACHE_SIZE) {
                parsedCache.remove(parsedCache.keys.first())
            }
        }
    }

    /** Forgets parsed SVGs, e.g. when another document is opened. */
    fun clearCache() = synchronized(parsedCache) { parsedCache.clear() }

    /** Renders the part of [el] inside the given document rect; null on failure. */
    fun renderRegion(
        el: NativeVectorImageElement,
        left: Float, top: Float, right: Float, bottom: Float,
        pxPerUnit: Float
    ): DetailTile? = try {
        val w = ((right - left) * pxPerUnit).roundToInt()
        val h = ((bottom - top) * pxPerUnit).roundToInt()
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_DETAIL_PIXELS) {
            null
        } else {
            val svg = parsed(el)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(pxPerUnit, pxPerUnit)
            canvas.translate(-left, -top)
            applyImageSpace(canvas, el)
            svg.renderToCanvas(
                canvas,
                RenderOptions().viewPort(0f, 0f, el.intrinsicWidth, el.intrinsicHeight)
            )
            DetailTile(bitmap, left, top, right, bottom, pxPerUnit)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }

    /** A screenful and a bit: a detail tile never needs to be bigger than the view. */
    private const val MAX_DETAIL_PIXELS = 16_000_000L

    /**
     * Draws [el] straight onto [canvas] as vectors, for export: a PDF keeps its imported
     * pages as paths and text outlines rather than a bitmap of them.
     */
    fun renderVector(canvas: Canvas, el: NativeVectorImageElement) {
        try {
            val svg = SVG.getFromString(el.svgData)
            canvas.save()
            applyImageSpace(canvas, el)
            canvas.clipRect(0f, 0f, el.intrinsicWidth, el.intrinsicHeight)
            svg.renderToCanvas(
                canvas,
                RenderOptions().viewPort(0f, 0f, el.intrinsicWidth, el.intrinsicHeight)
            )
            canvas.restore()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Document coordinates -> the SVG's intrinsic coordinates: the image transform, then
     * the intrinsic box stretched over the rectangle [-hx, hx] x [-hy, hy].
     */
    private fun applyImageSpace(canvas: Canvas, el: NativeVectorImageElement) {
        canvas.concat(matrixFor(el))
        canvas.translate(-el.halfExtentX, -el.halfExtentY)
        canvas.scale(2f * el.halfExtentX / el.intrinsicWidth, 2f * el.halfExtentY / el.intrinsicHeight)
    }

    /** Maps the image's local rectangle [-hx, hx] x [-hy, hy] into canvas coordinates. */
    fun matrixFor(el: NativeVectorImageElement): Matrix {
        val t = el.transform
        return Matrix().apply {
            setValues(floatArrayOf(t[0], t[2], t[4], t[1], t[3], t[5], 0f, 0f, 1f))
        }
    }
}
