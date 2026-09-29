package io.github.kjly.brna.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryTest {

    @Test
    fun `each tab's recovery file is found by its tab's id`() {
        assertEquals(1727164800123L, Recovery.slotOf("recovery-1727164800123.rnote"))
    }

    @Test
    fun `the single copy from before tabs is still found`() {
        assertEquals(Recovery.LEGACY_SLOT, Recovery.slotOf("recovery.rnote"))
    }

    @Test
    fun `half-written copies, their descriptions and other files are not recovery slots`() {
        assertNull(Recovery.slotOf("recovery-1727164800123.rnote.tmp"))
        assertNull(Recovery.slotOf("recovery-1727164800123.properties"))
        assertNull(Recovery.slotOf("recovery.properties"))
        assertNull(Recovery.slotOf("Mathe.rnote"))
        assertNull(Recovery.slotOf("recovery-abc.rnote"))
        // Slot 0 is the old single copy's, never written under a tab's name.
        assertNull(Recovery.slotOf("recovery-0.rnote"))
    }
}
