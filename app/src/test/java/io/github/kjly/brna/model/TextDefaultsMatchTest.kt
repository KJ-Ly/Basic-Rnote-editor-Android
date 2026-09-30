package io.github.kjly.brna.model

import io.github.kjly.brna.storage.NativeEditing
import org.junit.Assert.assertEquals
import org.junit.Test

/** The saved defaults and the places that hold the same numbers must not drift apart. */
class TextDefaultsMatchTest {
    @Test fun `the defaults are the ones a new tool config and a new text box start with`() {
        assertEquals(TextDefaults.SIZE, ToolConfig().textSize, 0f)
        assertEquals(TextDefaults.FAMILY, ToolConfig().textFamily)
        assertEquals(TextDefaults.FAMILY, NativeEditing.TEXT_FONT_FAMILY)
    }

    @Test fun `the size limits are the ones the size buttons keep to`() {
        val tool = ToolConfig(activeTool = ToolType.TYPEWRITER)
        assertEquals(TextDefaults.MIN_SIZE, tool.updateActiveSize(-3f).textSize, 0f)
        assertEquals(TextDefaults.MAX_SIZE, tool.updateActiveSize(9999f).textSize, 0f)
    }
}
