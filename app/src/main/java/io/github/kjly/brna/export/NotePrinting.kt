package io.github.kjly.brna.export

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import io.github.kjly.brna.model.NoteDocument
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Desktop Rnote's "Print" (Ctrl+P) through Android's print service: every page with its
 * background and pattern, as Rnote prints them, handed over as the same PDF the PDF
 * export writes. The printer, the paper, which pages and how many copies are all chosen
 * in Android's print dialog, which also fits each page onto the paper.
 */
class NotePrintAdapter(private val document: NoteDocument) : PrintDocumentAdapter() {

    private val prefs = ExportPrefs(
        scope = ExportScope.DOCUMENT,
        format = ExportFormat.PDF,
        withBackground = true,
        withPattern = true,
        optimizePrinterOutput = false
    )
    private val pages = DocumentExporter.printPages(document, prefs)
    private val main = Handler(Looper.getMainLooper())

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(DocumentExporter.sharedFileName(document.title, null, ExportFormat.PDF))
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages.size)
            .build()
        // The pages are the note's own, whatever the paper: only the first layout is new.
        callback.onLayoutFinished(info, oldAttributes == null)
    }

    override fun onWrite(
        pageRanges: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        // Rendering pages of imported PDFs can take a while; not on the main thread.
        thread(name = "print") {
            val written = try {
                // Not closed here: the descriptor belongs to the print service, which closes it.
                val out = FileOutputStream(destination.fileDescriptor)
                DocumentExporter.writePdf(document, pages, prefs, out).also { out.flush() }
            } catch (e: Throwable) {
                // OutOfMemoryError included: a note too big to print is a failed print, not a crash.
                e.printStackTrace()
                false
            }
            main.post {
                when {
                    cancellationSignal.isCanceled -> callback.onWriteCancelled()
                    // All pages are written; the print service picks out the ones asked for.
                    written -> callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    else -> callback.onWriteFailed("The note could not be prepared for printing")
                }
            }
        }
    }
}
