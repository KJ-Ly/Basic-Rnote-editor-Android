package io.github.kjly.brna.render

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * The random numbers desktop Rnote draws its textured brush's dots with, bit for bit:
 * rand_pcg 0.10's `Pcg64` (`Lcg128Xsl64`) seeded through rand_core 0.10's
 * `seed_from_u64`, and the samplers of rand 0.10 and rand_distr 0.6 it takes them through
 * — `Uniform<f64>`, the standard bool, `Normal` and `Exp`, the last two by the ziggurat
 * method on rand_distr's own tables ([ZigguratTables]). A stroke carries only its seed;
 * the same seed through the same steps is what puts every dot where Rnote put it.
 *
 * The 128-bit state is two longs; every operation wraps, as Rust's `wrapping_*` do.
 */
internal class Pcg64 private constructor(
    private var hi: Long,
    private var lo: Long,
    private val incHi: Long,
    private val incLo: Long
) {
    /** rand_core's `next_u64`: a step of the LCG, then the XSL RR output. */
    fun nextU64(): Long {
        step()
        val rot = (hi ushr 58).toInt()
        return java.lang.Long.rotateRight(hi xor lo, rot)
    }

    /** rand_pcg's `next_u32`: the low half of [nextU64]. */
    fun nextU32(): Int = nextU64().toInt()

    private fun step() {
        val mulLo = lo * MUL_LO
        val mulHi = unsignedMultiplyHigh(lo, MUL_LO) + hi * MUL_LO + lo * MUL_HI
        val sumLo = mulLo + incLo
        val carry = if (java.lang.Long.compareUnsigned(sumLo, mulLo) < 0) 1L else 0L
        lo = sumLo
        hi = mulHi + incHi + carry
    }

    companion object {
        private const val MUL_HI = 0x2360ED051FC65DA4L
        private const val MUL_LO = 0x4385DF649FCCF645L

        /** rand_core's `pcg32` for seeding, which fills the seed from the `u64` given. */
        private const val PCG32_MUL = 0x5851F42D4C957F2DL
        private val PCG32_INC = 0xA17654E46FBE17F3uL.toLong()

        /**
         * rand_core's `SeedableRng::seed_from_u64`: the 32 seed bytes filled from a PCG32
         * started at [seed], then rand_pcg's `from_seed` — state and increment read as
         * little-endian words, the increment made odd, and one step taken.
         */
        fun seedFromU64(seed: Long): Pcg64 {
            var state = seed
            val words = LongArray(4)
            for (w in 0 until 4) {
                var word = 0L
                for (half in 0 until 2) {
                    state = state * PCG32_MUL + PCG32_INC
                    val xorShifted = (((state ushr 18) xor state) ushr 27).toInt()
                    val rot = (state ushr 59).toInt()
                    val x = Integer.rotateRight(xorShifted, rot).toLong() and 0xFFFFFFFFL
                    word = word or (x shl (32 * half))
                }
                words[w] = word
            }
            val incLo = words[2] or 1L
            val incHi = words[3]
            // from_state_incr: the increment added to the state, then a step.
            val lo = words[0] + incLo
            val carry = if (java.lang.Long.compareUnsigned(lo, words[0]) < 0) 1L else 0L
            return Pcg64(words[1] + incHi + carry, lo, incHi, incLo).also { it.step() }
        }

        /** The high 64 bits of the 128-bit product of [a] and [b], both unsigned. */
        private fun unsignedMultiplyHigh(a: Long, b: Long): Long {
            val aLo = a and 0xFFFFFFFFL
            val aHi = a ushr 32
            val bLo = b and 0xFFFFFFFFL
            val bHi = b ushr 32
            val loLo = aLo * bLo
            val hiLo = aHi * bLo
            val loHi = aLo * bHi
            val cross = (loLo ushr 32) + (hiLo and 0xFFFFFFFFL) + (loHi and 0xFFFFFFFFL)
            return aHi * bHi + (hiLo ushr 32) + (loHi ushr 32) + (cross ushr 32)
        }
    }
}

/** rand's `Uniform<f64>` over [low, high): its scale set up once, as `Uniform::new` does. */
internal class UniformDouble(private val low: Double, high: Double) {
    private val scale: Double

    init {
        require(low < high) { "empty range" }
        // UniformFloat::new_bounded: shrink the scale by an ulp at a time until the
        // largest value a sample can take stays below high.
        var s = high - low
        while (s * MAX_RAND + low > high) s = Double.fromBits(s.toRawBits() - 1)
        scale = s
    }

    fun sample(rng: Pcg64): Double {
        // A value in [1, 2), taken to [0, 1) so the scale can't overflow; multiplied, then
        // added, in that order, as rand does.
        val value0To1 = RnoteRandom.value1To2(rng.nextU64(), 0) - 1.0
        return value0To1 * scale + low
    }

    private companion object {
        val MAX_RAND = 1.0 - Math.ulp(1.0)
    }
}

internal object RnoteRandom {

    /** f64::EPSILON. */
    private val EPSILON = Math.ulp(1.0)

    /** rand's `into_float_with_exponent`: the top 52 of 64 random bits as a float in [2^e, 2^(e+1)). */
    fun value1To2(bits: Long, exponent: Int): Double =
        Double.fromBits((bits ushr 12) or ((1023L + exponent) shl 52))

    /** rand's standard `f64`: 53 random bits in [0, 1). */
    fun standardDouble(rng: Pcg64): Double = (rng.nextU64() ushr 11).toDouble() * (1.0 / (1L shl 53).toDouble())

    /** rand's `Open01` `f64`: in (0, 1). */
    private fun open01(rng: Pcg64): Double = value1To2(rng.nextU64(), 0) - (1.0 - EPSILON / 2.0)

    /** rand's standard `bool`: the sign bit of a `u32`. */
    fun standardBool(rng: Pcg64): Boolean = rng.nextU32() < 0

    /** rand_distr's `StandardNormal`. */
    fun standardNormal(rng: Pcg64): Double = ziggurat(
        rng, symmetric = true, ZigguratTables.NORM_X, ZigguratTables.NORM_F,
        pdf = { x -> exp(-x * x / 2.0) }
    ) { u ->
        var x = 1.0
        var y = 0.0
        while (-2.0 * y < x * x) {
            val x0 = open01(rng)
            val y0 = open01(rng)
            x = ln(x0) / ZigguratTables.NORM_R
            y = ln(y0)
        }
        if (u < 0.0) x - ZigguratTables.NORM_R else ZigguratTables.NORM_R - x
    }

    /** rand_distr's `Exp1`. */
    fun exp1(rng: Pcg64): Double = ziggurat(
        rng, symmetric = false, ZigguratTables.EXP_X, ZigguratTables.EXP_F,
        pdf = { x -> exp(-x) }
    ) { ZigguratTables.EXP_R - ln(standardDouble(rng)) }

    /** rand_distr's `ziggurat` (src/utils.rs). */
    private inline fun ziggurat(
        rng: Pcg64,
        symmetric: Boolean,
        xTab: DoubleArray,
        fTab: DoubleArray,
        pdf: (Double) -> Double,
        zeroCase: (Double) -> Double
    ): Double {
        while (true) {
            val bits = rng.nextU64()
            val i = (bits and 0xFF).toInt()
            val u = if (symmetric) value1To2(bits, 1) - 3.0 else value1To2(bits, 0) - (1.0 - EPSILON / 2.0)
            val x = u * xTab[i]
            val testX = if (symmetric) abs(x) else x
            if (testX < xTab[i + 1]) return x
            if (i == 0) return zeroCase(u)
            if (fTab[i + 1] + (fTab[i] - fTab[i + 1]) * standardDouble(rng) < pdf(x)) return x
        }
    }

    /**
     * rnote-compose's `seed_advance`: the next seed is the first `u64` of a generator
     * seeded with this one. Every segment of a textured stroke gets its own.
     */
    fun seedAdvance(seed: Long): Long = Pcg64.seedFromU64(seed).nextU64()
}
