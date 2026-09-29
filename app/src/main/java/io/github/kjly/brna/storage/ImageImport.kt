package io.github.kjly.brna.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.nio.ByteBuffer

/**
 * Reads a photo or picture for insertion into a note as a Rnote 0.14 `bitmapimage`.
 *
 * Rnote keeps an image as raw RGBA, four bytes a pixel, so a tablet camera's full
 * resolution would put tens of megabytes into the note for every photo — too much to
 * sync through Drive or to hold in memory. Pictures are therefore scaled down to
 * [MAX_SIDE] on their longer side, still twice the width of an A4 page at 100%.
 */
object ImageImport {

    /** Longest side kept, in pixels. */
    const val MAX_SIDE = 1600

    /** An image's pixels as Rnote stores them. */
    class Pixels(val rgbaBase64: String, val width: Int, val height: Int)

    /**
     * The picture at [uri], upright and at most [MAX_SIDE] on its longer side, as
     * premultiplied RGBA; null if it can't be read. Blocking and memory-heavy: call from
     * a background thread, and expect an OutOfMemoryError for an absurdly large image.
     */
    fun read(context: Context, uri: Uri): Pixels? {
        val bitmap = decode(context, uri) ?: return null
        return try {
            val bytes = ByteBuffer.allocate(bitmap.width * bitmap.height * 4)
            // An ARGB_8888 bitmap holds its pixels as premultiplied R, G, B, A bytes, which is
            // exactly Rnote's R8g8b8a8Premultiplied: the bytes go across as they are, the
            // same way NativeElementRenderer.decodeBitmap brings them back.
            bitmap.copyPixelsToBuffer(bytes)
            Pixels(java.util.Base64.getEncoder().encodeToString(bytes.array()), bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * A Xournal++ image's encoded bytes as Rnote's `Image::try_from_encoded_bytes` makes
     * them: every pixel, premultiplied RGBA, not scaled down as a photo is. Null if they
     * aren't a picture Android can read.
     */
    internal fun decodeForXopp(bytes: ByteArray): XoppConvert.Pixels? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else {
            decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
        }
        return try {
            val buffer = ByteBuffer.allocate(bitmap.width * bitmap.height * 4)
            bitmap.copyPixelsToBuffer(buffer)
            XoppConvert.Pixels(java.util.Base64.getEncoder().encodeToString(buffer.array()), bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Only fills in `bounds`; the call itself always returns null in this mode.
        (resolver.openInputStream(uri) ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decoding at a power-of-two fraction of the size is nearly free and spares memory;
        // the exact size is reached by the scaling below.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        // Cameras store a photo as the sensor saw it and note the way up in EXIF, which
        // BitmapFactory ignores: without this, portrait photos arrive lying on their side.
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) {
            null
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = orientationMatrix(orientation)
        val longest = maxOf(decoded.width, decoded.height)
        if (longest > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / longest
            matrix.postScale(s, s)
        }

        val upright = if (matrix.isIdentity) decoded else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        }
        if (upright.config == Bitmap.Config.ARGB_8888) return upright
        return upright.copy(Bitmap.Config.ARGB_8888, false).also { upright.recycle() }
    }

    /** The transform that turns a photo stored with EXIF [orientation] upright. */
    private fun orientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
}
