package com.rnote.baby.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.rnote.baby.model.NoteDocument
import java.io.OutputStream

object ImageExporter {

    /**
     * Renders a NoteDocument onto a high-resolution Bitmap and saves as PNG to output stream.
     */
    fun exportToPng(
        document: NoteDocument,
        outputStream: OutputStream,
        width: Int = 0,
        height: Int = 0
    ): Boolean {
        val w = if (width > 0) width else document.paperStyle.effectivePageWidthPx.toInt().coerceAtLeast(1920)
        val h = if (height > 0) height else document.paperStyle.effectivePageHeightPx.toInt().coerceAtLeast(1080)
        return try {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Fill background
            canvas.drawColor(document.paperStyle.currentBackgroundColor.toArgb())

            // Paint setup
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
            }

            // Render strokes
            for (stroke in document.strokes) {
                if (stroke.points.isEmpty()) continue

                paint.color = stroke.color.toArgb()
                paint.strokeWidth = stroke.width
                paint.alpha = (stroke.alpha * 255).toInt()
                paint.strokeCap = if (stroke.isHighlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND

                val path = Path()
                path.moveTo(stroke.points[0].x, stroke.points[0].y)

                if (stroke.points.size == 1) {
                    path.addCircle(stroke.points[0].x, stroke.points[0].y, stroke.width / 2f, Path.Direction.CW)
                } else {
                    for (i in 1 until stroke.points.size - 1) {
                        val curr = stroke.points[i]
                        val next = stroke.points[i + 1]
                        val midX = (curr.x + next.x) / 2f
                        val midY = (curr.y + next.y) / 2f
                        path.quadTo(curr.x, curr.y, midX, midY)
                    }
                    val last = stroke.points.last()
                    path.lineTo(last.x, last.y)
                }

                canvas.drawPath(path, paint)
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            bitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
