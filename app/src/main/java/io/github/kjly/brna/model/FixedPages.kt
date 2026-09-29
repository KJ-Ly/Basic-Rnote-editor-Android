package io.github.kjly.brna.model

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil

/**
 * Desktop Rnote's Fixed Size layout (rnote-engine/src/document/mod.rs): pages of the
 * format one below the other from the origin, as many as the document is tall. Rnote
 * never grows or shrinks it on its own — writing past the last page leaves it as it is.
 * Add Page and Remove Page change it a page at a time; Resize to Fit Content, a new
 * format or layout, and an import fit it to what the document holds.
 */
object FixedPages {

    /** A height written as a float can come out a hair over a whole number of pages. */
    private const val SLACK = 1e-3f

    /** The pages in a document [height] tall: Rnote's `calc_n_pages`, which rounds up. */
    fun countFor(height: Float, pageHeight: Float): Int {
        if (pageHeight <= 0f || height <= 0f) return 1
        return ceil(height / pageHeight - SLACK).toInt().coerceAtLeast(1)
    }

    /**
     * Rnote's `resize_doc_fixed_size_layout`: the pages it takes to hold everything from
     * the top of [content] — or the origin, if that is higher — to its bottom, and never
     * fewer than one.
     */
    fun fitting(content: Rect?, pageHeight: Float): Int {
        if (pageHeight <= 0f) return 1
        val top = minOf(content?.top ?: 0f, 0f)
        val bottom = maxOf(content?.bottom ?: 0f, 0f)
        return ceil(maxOf(bottom - top, 1f) / pageHeight).toInt().coerceAtLeast(1)
    }

    /**
     * Whether what starts at [top] goes with a page that Remove Page takes off a
     * document now [bottom] tall: Rnote's `keys_below_y`, which trashes whatever lies
     * wholly below the new last page and nothing that reaches up onto it.
     */
    fun goesWithRemovedPage(top: Float, bottom: Float): Boolean = top > bottom
}
