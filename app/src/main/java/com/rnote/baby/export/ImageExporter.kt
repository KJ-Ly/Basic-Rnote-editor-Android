package com.rnote.baby.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.render.androidStrokePath
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

            // Strokes are filled outlines, not stroked paths — `stroke.strokeWidth` is a
            // nominal maximum that the pressure curve scales per point, so there is no
            // single Paint.strokeWidth that would be correct. See StrokeOutline.
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            // Render strokes
            for (stroke in document.strokes) {
                if (stroke.points.isEmpty()) continue

                // toArgb() carries the alpha; setting paint.alpha on top of it used to
                // overwrite a loaded marker's transparency with full opacity.
                paint.color = stroke.color.toArgb()

                canvas.drawPath(
                    androidStrokePath(stroke.points, stroke.strokeWidth, stroke.pressureCurve),
                    paint
                )
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
