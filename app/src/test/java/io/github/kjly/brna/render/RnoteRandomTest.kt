package io.github.kjly.brna.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The numbers below are what rand_pcg 0.10.2, rand 0.10.1 and rand_distr 0.6.0 give —
 * the versions desktop Rnote 0.14 is built with — printed by a Rust program built
 * against them. A textured stroke's dots are only Rnote's dots if every one matches.
 */
class RnoteRandomTest {

    @Test
    fun `Pcg64 seeded from a u64 gives Rust's numbers`() {
        val rng = Pcg64.seedFromU64(42)
        assertEquals(
            listOf(4178418447715145737uL, 4410739922618931473uL, 14034899209665866285uL, 9736923071240364268uL),
            List(4) { rng.nextU64().toULong() }
        )
    }

    @Test
    fun `the next seed is the first number of a generator seeded with this one`() {
        assertEquals(4178418447715145737uL, RnoteRandom.seedAdvance(42).toULong())
    }

    @Test
    fun `uniform, normal, exponential and bool samples follow Rust's, one after another`() {
        val rng = Pcg64.seedFromU64(7)
        val uniform = UniformDouble(-2.5, 4.0)
        assertEquals(
            listOf(-2.4967047536492784, -0.9963572946934505, 3.3603415119237674),
            List(3) { uniform.sample(rng) }
        )
        assertEquals(
            listOf(0.4133372350029456, -0.03334723972595288, 0.11881879412302039, -1.3566299177050594, -0.6151875804107791),
            List(5) { RnoteRandom.standardNormal(rng) }
        )
        assertEquals(
            listOf(1.2282933166034276, 2.7435042245625976, 0.12128324174440856, 1.571095743010628, 1.5007584607519908),
            List(5) { RnoteRandom.exp1(rng) }
        )
        assertEquals(
            listOf(true, true, true, true, false, false, false, false),
            List(8) { RnoteRandom.standardBool(rng) }
        )
    }
}
