package io.github.kjly.brna.model

/** Rnote's `LineStyle` for a smooth shape (rnote-compose/src/style/smooth), serde names included. */
enum class ShapeLineStyle(val apiName: String) {
    SOLID("solid"),
    DOTTED("dotted"),
    DASHED_NARROW("dashed_narrow"),
    DASHED_EQUIDISTANT("dashed_equidistant"),
    DASHED_WIDE("dashed_wide")
}

/** Rnote's `LineCap`, serde names included. */
enum class ShapeLineCap(val apiName: String) {
    STRAIGHT("straight"),
    ROUNDED("rounded")
}

/**
 * How the Shaper draws its lines: the line style and line cap of Rnote's `SmoothOptions`,
 * solid and straight to begin with. The two go together as Rnote's `update_line_style`
 * and `update_line_cap` keep them — its dots are round caps on dashes of no length, so a
 * dotted line takes a round cap, and a straight cap turns a dotted line back to solid.
 */
data class ShapeLine(
    val style: ShapeLineStyle = ShapeLineStyle.SOLID,
    val cap: ShapeLineCap = ShapeLineCap.STRAIGHT
) {
    fun withStyle(style: ShapeLineStyle): ShapeLine =
        ShapeLine(style, if (style == ShapeLineStyle.DOTTED) ShapeLineCap.ROUNDED else cap)

    fun withCap(cap: ShapeLineCap): ShapeLine =
        ShapeLine(if (style == ShapeLineStyle.DOTTED && cap != ShapeLineCap.ROUNDED) ShapeLineStyle.SOLID else style, cap)
}
