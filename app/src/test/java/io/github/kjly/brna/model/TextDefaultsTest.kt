package io.github.kjly.brna.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDefaultsTest {
    @Test fun `a saved size within Rnote's limits is kept`() {
        assertEquals(48f, TextDefaults.size(48f), 0f)
        assertEquals(20.5f, TextDefaults.size(20.5f), 0f)
        assertEquals(1f, TextDefaults.size(1f), 0f)
        assertEquals(512f, TextDefaults.size(512f), 0f)
    }

    @Test fun `a saved size outside the limits is brought inside them`() {
        assertEquals(1f, TextDefaults.size(0.25f), 0f)
        assertEquals(512f, TextDefaults.size(9000f), 0f)
    }

    @Test fun `nothing usable gives Rnote's default size`() {
        assertEquals(32f, TextDefaults.size(0f), 0f)
        assertEquals(32f, TextDefaults.size(-5f), 0f)
        assertEquals(32f, TextDefaults.size(Float.NaN), 0f)
        assertEquals(32f, TextDefaults.size(Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun `a family with a font loaded for it is kept`() {
        assertEquals("Cantarell", TextDefaults.family("Cantarell") { it == "Cantarell" })
        assertEquals("Cantarell", TextDefaults.family("  Cantarell ") { it == "Cantarell" })
    }

    @Test fun `a family whose font is gone falls back to the default`() {
        assertEquals("serif", TextDefaults.family("Cantarell") { false })
    }

    @Test fun `the default family needs no font`() {
        assertEquals("serif", TextDefaults.family("serif") { false })
    }

    @Test fun `no saved family gives the default`() {
        assertEquals("serif", TextDefaults.family(null) { true })
        assertEquals("serif", TextDefaults.family("", { true }))
        assertEquals("serif", TextDefaults.family("   ") { true })
    }
}
