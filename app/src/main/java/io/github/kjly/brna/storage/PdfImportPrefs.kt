package io.github.kjly.brna.storage

/** Rnote's `PdfImportPageSpacing`: how the imported pages follow one another. */
enum class PdfPageSpacing(val apiName: String, val displayName: String) {
    /** One below the other, 16 apart. */
    CONTINUOUS("continuous", "Continuous"),
    /** Each a page of the note's format further down. */
    ONE_PER_DOCUMENT_PAGE("one_per_document_page", "One per Document Page");

    companion object {
        fun fromApiName(name: String?): PdfPageSpacing? = entries.firstOrNull { it.apiName == name }
    }
}

/**
 * Rnote's `PdfImportPrefs`, with its defaults: half the format's width, one page below the
 * other, the document left as it is. Its "Pages Type" is left out — this app always puts a
 * page in as Rnote's vector image, holding the page drawn by Android, since Android can't
 * turn a PDF into paths — and so is the bitmap scale that only goes with Rnote's bitmaps.
 */
data class PdfImportPrefs(
    /** The pages' width as a percentage of the format's, 1 to 100. */
    val pageWidthPercent: Int = 50,
    val spacing: PdfPageSpacing = PdfPageSpacing.CONTINUOUS,
    /** Rnote's "Adjust Document": the format becomes the PDF's page, the layout Fixed Size. */
    val adjustDocument: Boolean = false
) {
    /** For the settings, which keep the last choice as Rnote's do. */
    fun encode(): String = "$pageWidthPercent;${spacing.apiName};$adjustDocument"

    companion object {
        fun decode(text: String?): PdfImportPrefs {
            val parts = text?.split(';') ?: return PdfImportPrefs()
            val defaults = PdfImportPrefs()
            return PdfImportPrefs(
                pageWidthPercent = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 100) ?: defaults.pageWidthPercent,
                spacing = PdfPageSpacing.fromApiName(parts.getOrNull(1)) ?: defaults.spacing,
                adjustDocument = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: defaults.adjustDocument
            )
        }
    }
}

/**
 * Where Rnote puts a PDF's pages (`VectorImage::from_pdf_bytes`): all at one zoom, taken
 * from the PDF's first page so that it comes out the width asked for, starting at the
 * insert position and going down.
 */
object PdfPageLayout {

    /** Rnote's `Stroke::IMPORT_OFFSET_DEFAULT`: how far into the view an import goes. */
    const val IMPORT_OFFSET = 32f

    /** A page of the PDF — [index], from 0 — and the rectangle it goes in, in document units. */
    data class Placed(val index: Int, val x: Float, val y: Float, val width: Float, val height: Float)

    /**
     * [sizes] are every page's size in points, the PDF's first page among them; [first] to
     * [last] (from 0, both in) the pages to import. With [PdfImportPrefs.adjustDocument]
     * the pages go at the origin, the width of the format, one directly below the other.
     */
    fun place(
        sizes: List<Pair<Float, Float>>,
        first: Int,
        last: Int,
        prefs: PdfImportPrefs,
        formatWidth: Float,
        formatHeight: Float,
        insertX: Float,
        insertY: Float
    ): List<Placed> {
        val firstPage = sizes.firstOrNull() ?: return emptyList()
        val pageWidth = if (prefs.adjustDocument) formatWidth else formatWidth * (prefs.pageWidthPercent / 100f)
        val zoom = pageWidth / firstPage.first.coerceAtLeast(1f)
        val x = if (prefs.adjustDocument) 0f else insertX
        var y = if (prefs.adjustDocument) 0f else insertY
        val placed = ArrayList<Placed>()
        for (i in first.coerceAtLeast(0)..last.coerceAtMost(sizes.size - 1)) {
            val (w, h) = sizes[i]
            val width = w * zoom
            val height = h * zoom
            placed += Placed(i, x, y, width, height)
            y += when {
                prefs.adjustDocument -> height
                prefs.spacing == PdfPageSpacing.CONTINUOUS -> height + IMPORT_OFFSET * 0.5f
                else -> formatHeight
            }
        }
        return placed
    }
}
