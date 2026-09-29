package io.github.kjly.brna.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import io.github.kjly.brna.model.NativeVectorImageElement
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Imports a PDF's pages into a note, the way desktop Rnote's PDF import does: one image
 * per page on the document layer, underneath everything drawn.
 *
 * Android can render a PDF page but not turn it into vector paths, so each page becomes
 * a Rnote vector image whose SVG holds the rendered page as an embedded PNG. That keeps
 * the file readable by desktop Rnote (it draws SVG images with librsvg), and a mostly
 * white worksheet compresses to a few hundred kB as PNG — where Rnote's own bitmap form,
 * raw pixels, would be tens of megabytes per page.
 */
object PdfImporter {

    /** Rendered pixels per document unit: sharp at the zoom a tablet is read at. */
    private const val RENDER_SCALE = 2f

    /** Largest rendered side, so one oversized page can't exhaust memory. */
    private const val MAX_RENDER_SIDE = 4096

    /**
     * The pages of the PDF at [uri], each [pageWidth] document units wide and starting
     * on a fresh page of the note's format from [startY] down, one after the other.
     * Blocking; call off the main thread. Throws when the file isn't a readable PDF.
     */
    fun import(
        context: Context,
        uri: Uri,
        pageWidth: Float,
        formatHeight: Float,
        startY: Float
    ): List<NativeVectorImageElement> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open $uri")
        return descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                var y = startY
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val element = importPage(page, pageWidth, y)
                        // Each page starts on a page of its own, so the note's page grid
                        // and a page-by-page export line up with the PDF's pages.
                        val height = element.maxY - element.minY
                        y += ceil(height / formatHeight).coerceAtLeast(1f) * formatHeight
                        element
                    }
                }
            }
        }
    }

    private fun importPage(page: PdfRenderer.Page, docWidth: Float, top: Float): NativeVectorImageElement {
        // PDF units are points; the page keeps its proportions at the note's width.
        val ptW = page.width.toFloat().coerceAtLeast(1f)
        val ptH = page.height.toFloat().coerceAtLeast(1f)
        val docHeight = ptH * docWidth / ptW

        var pxW = docWidth * RENDER_SCALE
        var pxH = docHeight * RENDER_SCALE
        val longest = maxOf(pxW, pxH)
        if (longest > MAX_RENDER_SIDE) {
            pxW *= MAX_RENDER_SIDE / longest
            pxH *= MAX_RENDER_SIDE / longest
        }
        val w = pxW.roundToInt().coerceAtLeast(1)
        val h = pxH.roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // PdfRenderer leaves the page transparent where the PDF paints nothing; Rnote's
        // import renders onto white, and a page should look like paper.
        bitmap.eraseColor(Color.WHITE)
        page.render(
            bitmap, null,
            Matrix().apply { setScale(w / ptW, h / ptH) },
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )
        val png = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
        bitmap.recycle()
        val data = Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP)

        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
            "width=\"$ptW\" height=\"$ptH\">" +
            "<image x=\"0\" y=\"0\" width=\"$ptW\" height=\"$ptH\" preserveAspectRatio=\"none\" " +
            "xlink:href=\"data:image/png;base64,$data\"/></svg>"

        val hx = docWidth / 2f
        val hy = docHeight / 2f
        return NativeVectorImageElement(
            svgData = svg,
            intrinsicWidth = ptW,
            intrinsicHeight = ptH,
            halfExtentX = hx,
            halfExtentY = hy,
            transform = floatArrayOf(1f, 0f, 0f, 1f, hx, top + hy),
            // Where Rnote puts imported PDF pages: beneath images and all ink.
            layer = "document",
            minX = 0f, minY = top, maxX = docWidth, maxY = top + docHeight
        )
    }
}
