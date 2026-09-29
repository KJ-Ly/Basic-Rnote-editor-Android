package io.github.kjly.brna.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * roughr's shapes against roughr 0.12's own, as rough_piet hands them to Rnote: the
 * numbers are what a Rust program built against it printed for the same shapes, options
 * and seeds — how many steps each set has, and its first and last. Rnote draws curves,
 * polylines and polygons in `f32`, which this does in doubles, so those agree to the
 * float's precision; everything else to the last bit.
 */
class RoughGeneratorTest {

    /** Rnote's rough defaults, as `generate_roughr_options` turns them into roughr's. */
    private fun options(fill: RoughFill?, seed: Long) = RoughOptions(
        strokeWidth = 2.4f,
        hachureAngle = (-0.715585 * 57.29577951308232).toFloat(),
        fillStyle = fill ?: RoughFill.HACHURE,
        fill = fill != null,
        seed = seed
    )

    private fun p(x: Int, y: Int) = RoughPoint(x.toDouble(), y.toDouble())

    private val corners = listOf(p(0, 0), p(80, 10), p(60, 70), p(-10, 50))

    private fun values(op: RoughOp): DoubleArray = when (op) {
        is RoughOp.Move -> doubleArrayOf(op.x, op.y)
        is RoughOp.Line -> doubleArrayOf(op.x, op.y)
        is RoughOp.Curve -> doubleArrayOf(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
    }

    private fun assertOp(expected: RoughOp, actual: RoughOp, eps: Double) {
        assertEquals(expected::class, actual::class)
        val e = values(expected)
        val a = values(actual)
        for (i in e.indices) assertEquals("value $i of $actual", e[i], a[i], eps)
    }

    private fun assertSet(set: RoughSet, type: RoughSetType, size: Int, first: RoughOp, last: RoughOp, eps: Double) {
        assertEquals(type, set.type)
        assertEquals(size, set.ops.size)
        assertOp(first, set.ops.first(), eps)
        assertOp(last, set.ops.last(), eps)
    }

    @Test
    fun `StdRng seeded as roughr seeds it`() {
        val rng = ChaCha12Rng(345L)
        val first = (0 until 40).map { rng.nextDouble() }
        assertEquals(listOf(0.6861402445722316, 0.5861631683400859, 0.7526383697648633), first.subList(0, 3))
        // Past the first block of results, where rand_core's buffer refills.
        assertEquals(listOf(0.5535498271936511, 0.8802579482720604, 0.40437998133989506), first.subList(31, 34))
    }

    @Test
    fun `a line`() {
        val drawable = RoughGenerator.line(10.0, 20.0, 200.0, 120.0, options(null, 7))
        assertEquals(1, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.PATH, 4, RoughOp.Move(10.14888783693519, 19.203324732933268), RoughOp.Curve(48.012094824880776, 41.37030745210376, 86.11693041646801, 61.915494331193194, 199.84858215312053, 119.78696914473251), 1e-9)
    }

    @Test
    fun `a rectangle, hachured`() {
        val drawable = RoughGenerator.rectangle(-50.0, -30.0, 100.0, 60.0, options(RoughFill.HACHURE, 11))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 48, RoughOp.Move(-50.0, -30.0), RoughOp.Curve(38.99276798359417, 28.987092803281307, 45.45071775332562, 21.63600116150671, 51.754547437530604, 14.463684614032829), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 16, RoughOp.Move(-51.23586565256119, -30.251023292541504), RoughOp.Curve(-49.86069276332855, 12.13402339825738, -49.62456586360931, -6.065363406942119, -50.140371441841125, -30.534312188625336), 1e-9)
    }

    @Test
    fun `a rectangle, filled solid`() {
        val drawable = RoughGenerator.rectangle(-50.0, -30.0, 100.0, 60.0, options(RoughFill.SOLID, 11))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_PATH, 4, RoughOp.Move(-50.29854190349579, -31.99742564256303), RoughOp.Line(-48.391772508621216, 28.150582864880562), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 16, RoughOp.Move(-51.23586565256119, -30.251023292541504), RoughOp.Curve(-49.86069276332855, 12.13402339825738, -49.62456586360931, -6.065363406942119, -50.140371441841125, -30.534312188625336), 1e-9)
    }

    @Test
    fun `a rectangle, zig-zag filled`() {
        val drawable = RoughGenerator.rectangle(-50.0, -30.0, 100.0, 60.0, options(RoughFill.ZIG_ZAG, 11))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 88, RoughOp.Move(-55.2492594340889, -20.273075262458686), RoughOp.Curve(41.05423281744205, 30.78396756594978, 45.65534382788515, 23.5966879198533, 50.969778558117, 14.754706437915885), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 16, RoughOp.Move(-51.23586565256119, -30.251023292541504), RoughOp.Curve(-49.86069276332855, 12.13402339825738, -49.62456586360931, -6.065363406942119, -50.140371441841125, -30.534312188625336), 1e-9)
    }

    @Test
    fun `a rectangle, crosshatched`() {
        val drawable = RoughGenerator.rectangle(-50.0, -30.0, 100.0, 60.0, options(RoughFill.CROSS_HATCH, 11))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 96, RoughOp.Move(-50.0, -30.0), RoughOp.Curve(47.49164951072286, -25.945831601358766, 43.2211791598891, -29.540787915213187, 39.50791743245115, -31.694330249235513), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 16, RoughOp.Move(-51.23586565256119, -30.251023292541504), RoughOp.Curve(-49.86069276332855, 12.13402339825738, -49.62456586360931, -6.065363406942119, -50.140371441841125, -30.534312188625336), 1e-9)
    }

    @Test
    fun `a rectangle, dotted`() {
        val drawable = RoughGenerator.rectangle(-50.0, -30.0, 100.0, 60.0, options(RoughFill.DOTS, 11))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 1452, RoughOp.Move(-52.793619666196314, -30.532262334637775), RoughOp.Curve(42.460037736054545, 19.847654284168854, 41.19819212968511, 20.428816882452452, 41.08512023602453, 20.041425517974563), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 16, RoughOp.Move(-51.23586565256119, -30.251023292541504), RoughOp.Curve(-49.86069276332855, 12.13402339825738, -49.62456586360931, -6.065363406942119, -50.140371441841125, -30.534312188625336), 1e-9)
    }

    @Test
    fun `a rectangle, dash filled`() {
        val drawable = RoughGenerator.rectangle(-50.0, -30.0, 100.0, 60.0, options(RoughFill.DASHED, 11))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 128, RoughOp.Move(-47.57083022838204, -19.90535034770062), RoughOp.Curve(39.75854904638568, 26.86856194887494, 41.85782415541983, 25.70843585683859, 45.344818527238246, 21.514009484872112), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 16, RoughOp.Move(-51.23586565256119, -30.251023292541504), RoughOp.Curve(-49.86069276332855, 12.13402339825738, -49.62456586360931, -6.065363406942119, -50.140371441841125, -30.534312188625336), 1e-9)
    }

    @Test
    fun `a rectangle, zig-zag-line filled`() {
        val drawable = RoughGenerator.rectangle(-50.0, -30.0, 100.0, 60.0, options(RoughFill.ZIG_ZAG_LINE, 11))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 128, RoughOp.Move(-51.10564543530871, -1.9284613175952743), RoughOp.Curve(39.96618745139851, 23.16826736246861, 40.75990372548263, 20.799820611037614, 40.225065965609595, 12.867932249473467), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 16, RoughOp.Move(-51.23586565256119, -30.251023292541504), RoughOp.Curve(-49.86069276332855, 12.13402339825738, -49.62456586360931, -6.065363406942119, -50.140371441841125, -30.534312188625336), 1e-9)
    }

    @Test
    fun `an ellipse, hachured`() {
        val drawable = RoughGenerator.ellipse(0.0, 0.0, 100.0, 60.0, options(RoughFill.HACHURE, 13))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 36, RoughOp.Move(-44.71718794902259, -12.67444179077653), RoughOp.Curve(28.509258302548346, 20.79135363243831, 37.102954034169, 10.402535873896323, 51.607360978036624, -6.998708394438534), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 26, RoughOp.Move(1.6696522120902597, -28.717731977472578), RoughOp.Curve(10.767549098713767, -28.45454043748839, 5.7584282353397125, -28.035401738007632, 5.8720982261444075, -27.43448261958669), 1e-9)
    }

    @Test
    fun `an ellipse, filled solid`() {
        val drawable = RoughGenerator.ellipse(0.0, 0.0, 100.0, 60.0, options(RoughFill.SOLID, 13))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_PATH, 26, RoughOp.Move(19.358729328239406, -26.465593930587158), RoughOp.Curve(22.799767843982018, -28.502330555387342, 18.631913265114733, -29.058671381496197, 18.477239405157533, -28.220134403913647), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 26, RoughOp.Move(1.6696522120902597, -28.717731977472578), RoughOp.Curve(10.767549098713767, -28.45454043748839, 5.7584282353397125, -28.035401738007632, 5.8720982261444075, -27.43448261958669), 1e-9)
    }

    @Test
    fun `a polygon, hachured`() {
        val drawable = RoughGenerator.polygon(corners, options(RoughFill.HACHURE, 17))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 40, RoughOp.Move(0.0, 0.0), RoughOp.Curve(55.879322, 66.9821, 58.8176, 64.12298, 65.163666, 58.19453), 1e-3)
        assertSet(drawable.sets[1], RoughSetType.PATH, 16, RoughOp.Move(1.1325519, 1.4005387), RoughOp.Curve(-7.0457015, 31.874971, -3.1117988, 15.623784, -0.6610938, -0.25025487), 1e-3)
    }

    @Test
    fun `a polyline`() {
        val drawable = RoughGenerator.linearPath(corners, false, options(null, 19))
        assertEquals(1, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.PATH, 12, RoughOp.Move(-1.4112151, 0.752856), RoughOp.Curve(31.739023, 62.495377, 3.6167428, 55.34188, -10.863005, 50.85205), 1e-3)
    }

    @Test
    fun `a cubic curve, hachured`() {
        val drawable = RoughGenerator.bezierCubic(p(0, 0), p(30, -40), p(70, 40), p(100, 0), options(RoughFill.HACHURE, 23))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 32, RoughOp.Move(0.0, 0.0), RoughOp.Curve(80.29633, 9.445555, 81.849625, 6.721894, 88.45924, -0.71784353), 1e-3)
        assertSet(drawable.sets[1], RoughSetType.PATH, 4, RoughOp.Move(0.0, 0.0), RoughOp.Curve(29.589792, -39.262478, 68.27341, 41.650764, 97.85546, -1.3284017), 1e-3)
    }

    @Test
    fun `a quadratic curve`() {
        val drawable = RoughGenerator.bezierQuadratic(p(0, 0), p(50, -60), p(100, 0), options(null, 29))
        assertEquals(1, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.PATH, 4, RoughOp.Move(0.0, 0.0), RoughOp.Curve(33.722034, -40.649673, 68.96508, -38.88543, 101.897896, 1.6948717), 1e-3)
    }

    @Test
    fun `an ellipse, dotted`() {
        val drawable = RoughGenerator.ellipse(0.0, 0.0, 100.0, 60.0, options(RoughFill.DOTS, 31))
        assertEquals(2, drawable.sets.size)
        assertSet(drawable.sets[0], RoughSetType.FILL_SKETCH, 924, RoughOp.Move(-40.33128156500794, -9.618450127798377), RoughOp.Curve(35.81437446843633, 9.380236864456103, 36.58655306212844, 7.618207323321775, 36.63305143207836, 7.666845783412521), 1e-9)
        assertSet(drawable.sets[1], RoughSetType.PATH, 26, RoughOp.Move(-9.678962044337107, -29.20531375670731), RoughOp.Curve(0.0019735524003863247, -29.71031197854852, -5.310163278656047, -28.54179565073723, -5.254112662588469, -28.309016069054312), 1e-9)
    }
}
