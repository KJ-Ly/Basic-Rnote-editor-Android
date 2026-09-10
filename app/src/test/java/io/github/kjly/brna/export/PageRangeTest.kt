package io.github.kjly.brna.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRangeTest {

    @Test
    fun `blank means every page`() {
        assertEquals(listOf(0, 1, 2), PageRange.parse("", 3))
        assertEquals(listOf(0, 1, 2), PageRange.parse("   ", 3))
    }

    @Test
    fun `the field is 1-based, the result is 0-based`() {
        assertEquals(listOf(0), PageRange.parse("1", 5))
        assertEquals(listOf(4), PageRange.parse("5", 5))
    }

    @Test
    fun `ranges and lists mix, spaces and all`() {
        assertEquals(listOf(0, 1, 2, 4), PageRange.parse("1-3, 5", 6))
        assertEquals(listOf(0, 1, 2, 4), PageRange.parse(" 1 - 3 ,5 ", 6))
    }

    @Test
    fun `pages keep the order they were asked for, without repeats`() {
        assertEquals(listOf(4, 0, 1), PageRange.parse("5,1,2,1", 6))
    }

    @Test
    fun `numbers past the end are dropped rather than rejected`() {
        assertEquals(listOf(1), PageRange.parse("2, 9", 3))
        assertEquals(listOf(1, 2), PageRange.parse("2-99", 3))
        assertEquals(emptyList<Int>(), PageRange.parse("7", 3))
    }

    @Test
    fun `syntax we cannot read is an error, so the sheet can say so`() {
        assertNull(PageRange.parse("one", 3))
        assertNull(PageRange.parse("2-", 3))
        assertNull(PageRange.parse("3-1", 3))
    }
}
