package io.github.kjly.brna.storage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.kjly.brna.model.BrushStyle
import io.github.kjly.brna.model.PenFavorite
import io.github.kjly.brna.model.ToolConfig
import io.github.kjly.brna.model.ToolType
import io.github.kjly.brna.model.brushFavorite
import io.github.kjly.brna.model.withFavorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenFavoritesTest {

    private val blue = PenFavorite(BrushStyle.SOLID, Color(0xFF1C71D8).toArgb(), 2.5f)
    private val yellowMarker = PenFavorite(BrushStyle.MARKER, Color(0xFFF6D32D).copy(alpha = 0.35f).toArgb(), 12f)

    @Test
    fun `slots survive being stored, empty ones included`() {
        val slots = listOf(blue, null, yellowMarker, null)
        assertEquals(slots, PenFavorites.decode(PenFavorites.encode(slots)))
    }

    @Test
    fun `nothing stored yet is four empty slots`() {
        assertEquals(List(PenFavorites.SLOTS) { null }, PenFavorites.decode(null))
    }

    @Test
    fun `a slot that can't be read comes back empty instead of failing`() {
        val text = "SOLID,-16777216,2.0\nSPARKLY,1,2\nMARKER,notanumber,4\nSOLID,-1,-3"
        val slots = PenFavorites.decode(text)
        assertEquals(PenFavorite(BrushStyle.SOLID, -16777216, 2f), slots[0])
        assertEquals(listOf(null, null, null), slots.drop(1))
    }

    @Test
    fun `a favorite holds the brush as it is set, for its own style`() {
        val marker = ToolConfig(brushStyle = BrushStyle.MARKER, highlighterWidth = 20f)
        assertEquals(BrushStyle.MARKER, marker.brushFavorite().style)
        assertEquals(20f, marker.brushFavorite().width, 0f)
        assertEquals(marker.highlighterColor.toArgb(), marker.brushFavorite().argb)
    }

    @Test
    fun `picking a favorite switches to the brush with its style, colour and width`() {
        val shaper = ToolConfig(activeTool = ToolType.SHAPER)
        val picked = shaper.withFavorite(yellowMarker)
        assertEquals(ToolType.BRUSH, picked.activeTool)
        assertEquals(BrushStyle.MARKER, picked.brushStyle)
        assertEquals(12f, picked.currentActiveSize, 0f)
        assertEquals(yellowMarker.argb, picked.currentActiveColor.toArgb())
        // The solid pen is left as it was.
        assertEquals(shaper.penColor, picked.penColor)
        assertTrue(picked.brushFavorite().matches(yellowMarker))
        assertFalse(picked.brushFavorite().matches(blue))
    }
}
