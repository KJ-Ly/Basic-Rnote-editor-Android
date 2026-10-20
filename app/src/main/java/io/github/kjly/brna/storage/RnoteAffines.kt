package io.github.kjly.brna.storage

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Rnote 0.15 changed how an element's position is stored (`maj0min15.rs`). Where 0.14
 * writes `"transform": {"affine": [a, b, 0, c, d, 0, tx, ty, 1]}` — nalgebra's
 * column-major 3×3 — 0.15 writes glam's `"affine": [a, b, c, d, tx, ty]` straight into
 * the element. Only text boxes, the two rectangles of a bitmap image, a vector image's
 * rectangle and the rect and ellipse shapes carry one.
 *
 * This app reads both and writes 0.14's, marked 0.14.2: Rnote 0.14 reads that as its
 * own, and 0.15 converts it when opening. What 0.15 cannot open is a file marked 0.14
 * that holds its own layout: its conversion finds no `transform` and gives up on the
 * whole file. So an element read in 0.15's layout is turned into 0.14's as it is read,
 * before anything keeps it to write back.
 */
object RnoteAffines {

    /** A `textstroke` as read, in 0.14's layout. */
    fun textStroke(obj: JsonElement) {
        toTransform(obj)
    }

    /** A `bitmapimage`: the rectangle of its pixel grid, and the one that places it. */
    fun bitmapImage(obj: JsonElement) {
        val o = obj.asObjectOrNull() ?: return
        toTransform(o.get("image")?.asObjectOrNull()?.get("rectangle"))
        toTransform(o.get("rectangle"))
    }

    /** A `shapestroke`, of which only a rect or an ellipse has a transform. */
    fun shapeStroke(obj: JsonElement) {
        val shape = obj.asObjectOrNull()?.get("shape")?.asObjectOrNull() ?: return
        toTransform(shape.get("rect"))
        toTransform(shape.get("ellipse"))
    }

    /**
     * [obj]'s 0.15 `affine` as 0.14's `transform`, in the place the `affine` was: the six
     * numbers as they were read, and the third row filled in. Anything already in 0.14's
     * layout, or not an object, is left as it is.
     */
    fun toTransform(obj: JsonElement?) {
        val o = obj?.asObjectOrNull() ?: return
        if (o.has("transform")) return
        val flat = o.get("affine")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        if (flat.size() != 6) return
        val matrix = JsonArray().apply {
            add(flat[0]); add(flat[1]); add(JsonPrimitive(0.0))
            add(flat[2]); add(flat[3]); add(JsonPrimitive(0.0))
            add(flat[4]); add(flat[5]); add(JsonPrimitive(1.0))
        }
        val transform = JsonObject().apply { add("affine", matrix) }
        // Rebuilt in order, as a JsonObject can only add at its end.
        val entries = o.entrySet().map { it.key to it.value }
        entries.forEach { o.remove(it.first) }
        for ((key, value) in entries) {
            if (key == "affine") o.add("transform", transform) else o.add(key, value)
        }
    }

    /**
     * An `affine` array as the model's [a, b, c, d, tx, ty]: 0.15's six as they are, or
     * 0.14's column-major nine without its third row. Null for any other length.
     */
    fun fromArray(values: List<Float>): FloatArray? = when (values.size) {
        6 -> values.toFloatArray()
        9 -> floatArrayOf(values[0], values[1], values[3], values[4], values[6], values[7])
        else -> null
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
}
