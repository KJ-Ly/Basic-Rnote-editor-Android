package io.github.kjly.brna.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rnote's `Color::to_inverted_brightness_color` (rnote-compose/src/color.rs), what its
 * selector's "Invert Color Brightness" does to every colour of the selection: the colour
 * taken to Okhwb — Björn Ottosson's hue, whiteness and blackness, built on his Okhsv —
 * whiteness and blackness swapped, and back, by the conversions of the palette crate Rnote
 * uses. Black and white trade places, a dark blue turns light blue, a colour as vivid as
 * sRGB has stays as it is, and inverting twice gives the colour back.
 *
 * Components are sRGB in [0, 1]; alpha is not touched.
 */
object InvertedBrightness {

    fun of(r: Float, g: Float, b: Float): FloatArray {
        val (h, s, v) = srgbToOkhsv(r.toDouble(), g.toDouble(), b.toDouble())
        // Okhsv to Okhwb is whiteness (1 − s)·v and blackness 1 − v; these are swapped.
        var white = max(1.0 - v, 0.0)
        var black = max((1.0 - s) * v, 0.0)
        // palette's clamp on the way back: the two scaled to add up to one at most.
        val sum = white + black
        if (sum > 1.0) {
            white /= sum
            black /= sum
        }
        // Okhwb to Okhsv: value 1 − blackness, saturation 1 − whiteness / value.
        val value = 1.0 - black
        val saturation = if (value > 0.0) 1.0 - white / value else 0.0
        val rgb = okhsvToSrgb(h, saturation, value)
        return FloatArray(3) { rgb[it].coerceIn(0.0, 1.0).toFloat() }
    }

    // ── Ottosson's Okhsv (bottosson.github.io/posts/colorpicker), as palette has it ────

    private data class Hsv(val h: Double, val s: Double, val v: Double)

    private fun srgbToOkhsv(r: Double, g: Double, b: Double): Hsv {
        val lab = linearSrgbToOklab(toLinear(r), toLinear(g), toLinear(b))
        val l = lab[0]
        if (l <= 0.0) return Hsv(0.0, 0.0, 0.0)
        val c = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
        // No hue to speak of: a grey, whose value is its lightness through the toe.
        if (c < ACHROMATIC) return Hsv(0.0, 0.0, toe(l))
        val a_ = lab[1] / c
        val b_ = lab[2] / c
        val h = 0.5 + 0.5 * atan2(-lab[2], -lab[1]) / PI

        val cusp = findCusp(a_, b_)
        val sMax = cusp[1] / cusp[0]
        val tMax = cusp[1] / (1.0 - cusp[0])
        val k = 1.0 - S0 / sMax

        val t = tMax / (c + l * tMax)
        val lV = t * l
        val cV = t * c
        val lVt = toeInv(lV)
        val cVt = cV * lVt / lV

        val scale = oklabToLinearSrgb(lVt, a_ * cVt, b_ * cVt)
        val scaleL = cbrt(1.0 / max(max(scale[0], scale[1]), max(scale[2], 0.0)))
        var ll = l / scaleL
        var cc = c / scaleL
        cc = cc * toe(ll) / ll
        ll = toe(ll)

        val v = ll / lV
        val s = (S0 + tMax) * cV / (tMax * S0 + tMax * k * cV)
        return Hsv(h, s, v)
    }

    private fun okhsvToSrgb(h: Double, s: Double, v: Double): DoubleArray {
        if (v <= 0.0) return doubleArrayOf(0.0, 0.0, 0.0)
        val a_ = cos(2.0 * PI * h)
        val b_ = sin(2.0 * PI * h)

        val cusp = findCusp(a_, b_)
        val sMax = cusp[1] / cusp[0]
        val tMax = cusp[1] / (1.0 - cusp[0])
        val k = 1.0 - S0 / sMax

        val lV = 1.0 - s * S0 / (S0 + tMax - tMax * k * s)
        val cV = s * tMax * S0 / (S0 + tMax - tMax * k * s)
        var l = v * lV
        var c = v * cV

        val lVt = toeInv(lV)
        val cVt = cV * lVt / lV
        val lNew = toeInv(l)
        c = c * lNew / l
        l = lNew

        val scale = oklabToLinearSrgb(lVt, a_ * cVt, b_ * cVt)
        val scaleL = cbrt(1.0 / max(max(scale[0], scale[1]), max(scale[2], 0.0)))
        l *= scaleL
        c *= scaleL

        val rgb = oklabToLinearSrgb(l, c * a_, c * b_)
        return DoubleArray(3) { fromLinear(rgb[it]) }
    }

    /** Where the hue ([a], [b]) is most colourful: its lightness and chroma. */
    private fun findCusp(a: Double, b: Double): DoubleArray {
        val sCusp = maxSaturation(a, b)
        val rgb = oklabToLinearSrgb(1.0, sCusp * a, sCusp * b)
        val lCusp = cbrt(1.0 / max(max(rgb[0], rgb[1]), rgb[2]))
        return doubleArrayOf(lCusp, lCusp * sCusp)
    }

    /** Ottosson's `compute_max_saturation`: a polynomial, then one step of Halley's method. */
    private fun maxSaturation(a: Double, b: Double): Double {
        val k0: Double; val k1: Double; val k2: Double; val k3: Double; val k4: Double
        val wl: Double; val wm: Double; val ws: Double
        if (-1.88170328 * a - 0.80936493 * b > 1) {
            k0 = 1.19086277; k1 = 1.76576728; k2 = 0.59662641; k3 = 0.75515197; k4 = 0.56771245
            wl = 4.0767416621; wm = -3.3077115913; ws = 0.2309699292
        } else if (1.81444104 * a - 1.19445276 * b > 1) {
            k0 = 0.73956515; k1 = -0.45954404; k2 = 0.08285427; k3 = 0.12541070; k4 = 0.14503204
            wl = -1.2684380046; wm = 2.6097574011; ws = -0.3413193965
        } else {
            k0 = 1.35733652; k1 = -0.00915799; k2 = -1.15130210; k3 = -0.50559606; k4 = 0.00692167
            wl = -0.0041960863; wm = -0.7034186147; ws = 1.7076147010
        }
        var s = k0 + k1 * a + k2 * b + k3 * a * a + k4 * a * b

        val kL = 0.3963377774 * a + 0.2158037573 * b
        val kM = -0.1055613458 * a - 0.0638541728 * b
        val kS = -0.0894841775 * a - 1.2914855480 * b
        val l_ = 1.0 + s * kL
        val m_ = 1.0 + s * kM
        val s_ = 1.0 + s * kS
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val sc = s_ * s_ * s_
        val lDs = 3.0 * kL * l_ * l_
        val mDs = 3.0 * kM * m_ * m_
        val sDs = 3.0 * kS * s_ * s_
        val lDs2 = 6.0 * kL * kL * l_
        val mDs2 = 6.0 * kM * kM * m_
        val sDs2 = 6.0 * kS * kS * s_
        val f = wl * l + wm * m + ws * sc
        val f1 = wl * lDs + wm * mDs + ws * sDs
        val f2 = wl * lDs2 + wm * mDs2 + ws * sDs2
        s -= f * f1 / (f1 * f1 - 0.5 * f * f2)
        return s
    }

    private fun toe(x: Double): Double {
        val t = K3 * x - K1
        return 0.5 * (t + sqrt(t * t + 4.0 * K2 * K3 * x))
    }

    private fun toeInv(x: Double): Double = (x * x + K1 * x) / (K3 * (x + K2))

    private fun linearSrgbToOklab(r: Double, g: Double, b: Double): DoubleArray {
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun oklabToLinearSrgb(lightness: Double, a: Double, b: Double): DoubleArray {
        val l_ = lightness + 0.3963377774 * a + 0.2158037573 * b
        val m_ = lightness - 0.1055613458 * a - 0.0638541728 * b
        val s_ = lightness - 0.0894841775 * a - 1.2914855480 * b
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        )
    }

    private fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun fromLinear(c: Double): Double =
        if (c <= 0.0031308) 12.92 * c else 1.055 * max(c, 0.0).pow(1.0 / 2.4) - 0.055

    /** Okhsv's fixed saturation of the triangle's corner. */
    private const val S0 = 0.5

    /** The toe that makes Oklab's lightness track perceived lightness near black. */
    private const val K1 = 0.206
    private const val K2 = 0.03
    private const val K3 = (1.0 + K1) / (1.0 + K2)

    /** Chroma below which a colour is a grey, its hue meaningless. */
    private const val ACHROMATIC = 1e-9
}
