package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoHistoryTest {

    @Test
    fun `undo reaches back as far as Rnote's, dropping the oldest steps`() {
        val stack = (1..105).toMutableList()
        UndoHistory.trim(stack)
        assertEquals(UndoHistory.LIMIT, stack.size)
        assertEquals(6, stack.first())
        assertEquals(105, stack.last())
    }

    @Test
    fun `a history within the limit is left alone`() {
        val stack = mutableListOf("a", "b", "c")
        UndoHistory.trim(stack, limit = 3)
        assertEquals(listOf("a", "b", "c"), stack)
        stack += "d"
        UndoHistory.trim(stack, limit = 3)
        assertEquals(listOf("b", "c", "d"), stack)
    }
}
