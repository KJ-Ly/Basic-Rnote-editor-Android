package io.github.kjly.brna.export

/**
 * What the Share button sends through Android's share sheet. No counterpart in desktop
 * Rnote, which has no share sheet to hand to; each is an export with fixed choices, and
 * takes everything else — background, pattern, resolution — from the export sheet.
 */
enum class ShareTarget(val format: ExportFormat) {
    /** The page most in view; what is on screen, on a canvas without pages. */
    PAGE_PNG(ExportFormat.PNG),
    PAGE_PDF(ExportFormat.PDF),
    /** Whatever the selector holds, ink and desktop elements alike. */
    SELECTION_PNG(ExportFormat.PNG),
    /** Every page, as the export sheet's document export writes it. */
    NOTE_PDF(ExportFormat.PDF)
}
