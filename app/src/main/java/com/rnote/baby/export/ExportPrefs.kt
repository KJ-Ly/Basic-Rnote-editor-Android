package com.rnote.baby.export

/**
 * Desktop Rnote exports through three separate dialogs — Export document, Export
 * document pages, Export selection (rnote-ui/src/dialogs/export.rs) — each backed by
 * its own prefs struct in rnote-engine/src/engine/export.rs. BRNA collapses the three
 * into one sheet with a scope switch; the options and their defaults below are Rnote's.
 *
 * Two of Rnote's document formats are deliberately absent: Xopp (Xournal++), which is a
 * whole second file format to write, and nothing here can produce it.
 */
enum class ExportScope(val displayName: String) {
    /** Rnote's "Export document": the whole thing as one file. */
    DOCUMENT("Document"),
    /** Rnote's "Export document pages": one file per page, into a folder. */
    PAGES("Pages"),
    /** Rnote's "Export selection": whatever the selector tool currently holds. */
    SELECTION("Selection")
}

enum class ExportFormat(
    val displayName: String,
    val mimeType: String,
    val extension: String
) {
    SVG("SVG", "image/svg+xml", "svg"),
    PNG("PNG", "image/png", "png"),
    JPEG("JPEG", "image/jpeg", "jpg"),
    PDF("PDF", "application/pdf", "pdf");

    val isBitmap: Boolean get() = this == PNG || this == JPEG
}

/**
 * Rnote's `SplitOrder` — the order pages are walked when a document is cut into pages.
 * Only meaningful when the layout can be more than one column wide.
 */
enum class SplitOrder(val displayName: String, val isReversed: Boolean) {
    ROW_MAJOR("Rows", false),
    COLUMN_MAJOR("Columns", false),
    ROW_MAJOR_REVERSE("Rows ↩", true),
    COLUMN_MAJOR_REVERSE("Columns ↩", true);

    val isRowMajor: Boolean get() = this == ROW_MAJOR || this == ROW_MAJOR_REVERSE
}

/**
 * The union of Rnote's `DocExportPrefs`, `DocPagesExportPrefs` and `SelectionExportPrefs`.
 * Defaults are Rnote's own (bitmap scale 1.8, JPEG quality 85, selection margin 12.0);
 * [pageRange] has no counterpart there — Rnote's page export always writes every page.
 */
data class ExportPrefs(
    val scope: ExportScope = ExportScope.DOCUMENT,
    val format: ExportFormat = ExportFormat.SVG,
    val withBackground: Boolean = true,
    val withPattern: Boolean = true,
    val optimizePrinterOutput: Boolean = false,
    val pageOrder: SplitOrder = SplitOrder.ROW_MAJOR,
    val bitmapScaleFactor: Float = 1.8f,
    val jpegQuality: Int = 85,
    val marginPx: Float = 12f,
    /** Blank means every page. Otherwise a 1-based list like "1-3, 5". */
    val pageRange: String = ""
) {
    /** Keeps [format] legal after a scope change, since the format lists differ. */
    fun withScope(newScope: ExportScope): ExportPrefs {
        val allowed = formatsFor(newScope)
        return copy(scope = newScope, format = if (format in allowed) format else allowed.first())
    }

    companion object {
        /** Mirrors Rnote's per-dialog format enums, minus Xopp. */
        fun formatsFor(scope: ExportScope): List<ExportFormat> = when (scope) {
            // DocExportFormat: Svg, Pdf (, Xopp)
            ExportScope.DOCUMENT -> listOf(ExportFormat.SVG, ExportFormat.PDF)
            // DocPagesExportFormat / SelectionExportFormat: Svg, Png, Jpeg
            ExportScope.PAGES,
            ExportScope.SELECTION -> listOf(ExportFormat.SVG, ExportFormat.PNG, ExportFormat.JPEG)
        }
    }
}

/**
 * Parses the page-range field: 1-based, comma separated, ranges with a dash, blank for
 * "everything". Out-of-document numbers are dropped rather than rejected, but syntax we
 * can't read returns null so the sheet can say so instead of silently exporting the lot.
 */
object PageRange {

    fun parse(spec: String, pageCount: Int): List<Int>? {
        val trimmed = spec.trim()
        if (trimmed.isEmpty()) return (0 until pageCount).toList()

        val picked = LinkedHashSet<Int>()
        for (part in trimmed.split(',')) {
            val token = part.trim()
            if (token.isEmpty()) continue
            val dash = token.indexOf('-')
            if (dash < 0) {
                val n = token.toIntOrNull() ?: return null
                if (n in 1..pageCount) picked.add(n - 1)
            } else {
                val from = token.substring(0, dash).trim().toIntOrNull() ?: return null
                val to = token.substring(dash + 1).trim().toIntOrNull() ?: return null
                if (from > to) return null
                for (n in from..to) if (n in 1..pageCount) picked.add(n - 1)
            }
        }
        return picked.toList()
    }
}
