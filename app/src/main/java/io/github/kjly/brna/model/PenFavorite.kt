package io.github.kjly.brna.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

/**
 * A saved brush: style, colour and width, picked up again with one tap. Desktop Rnote
 * has nothing like it — its colour slots hold a colour, its width presets a width — so it
 * lives only in this app's settings and never in a file.
 */
data class PenFavorite(
    /** Solid, Marker or Textured. */
    val style: BrushStyle,
    /** ARGB, alpha included, as the brush draws it: a marker's translucency is part of it. */
    val argb: Int,
    val width: Float
) {
    fun matches(other: PenFavorite) =
        style == other.style && argb == other.argb && abs(width - other.width) < WIDTH_TOLERANCE

    private companion object {
        const val WIDTH_TOLERANCE = 0.05f
    }
}

/** The brush as it is set now, to keep as a favorite. */
fun ToolConfig.brushFavorite(): PenFavorite = when (brushStyle) {
    BrushStyle.MARKER -> PenFavorite(BrushStyle.MARKER, highlighterColor.toArgb(), highlighterWidth)
    BrushStyle.TEXTURED -> PenFavorite(BrushStyle.TEXTURED, penColor.toArgb(), texturedWidth)
    BrushStyle.SOLID -> PenFavorite(BrushStyle.SOLID, penColor.toArgb(), strokeWidth)
}

/** The brush switched to [favorite]: its style, and its colour and width for that style. */
fun ToolConfig.withFavorite(favorite: PenFavorite): ToolConfig = when (favorite.style) {
    BrushStyle.MARKER -> copy(
        activeTool = ToolType.BRUSH,
        brushStyle = BrushStyle.MARKER,
        highlighterColor = Color(favorite.argb),
        highlighterWidth = favorite.width
    )
    BrushStyle.TEXTURED -> copy(
        activeTool = ToolType.BRUSH,
        brushStyle = BrushStyle.TEXTURED,
        penColor = Color(favorite.argb),
        texturedWidth = favorite.width
    )
    BrushStyle.SOLID -> copy(
        activeTool = ToolType.BRUSH,
        brushStyle = BrushStyle.SOLID,
        penColor = Color(favorite.argb),
        strokeWidth = favorite.width
    )
}
