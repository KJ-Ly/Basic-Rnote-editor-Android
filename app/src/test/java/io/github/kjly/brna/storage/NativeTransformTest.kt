package io.github.kjly.brna.storage

import androidx.compose.ui.graphics.Color
import io.github.kjly.brna.model.Affine
import io.github.kjly.brna.model.LineShape
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.RectShape
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.ShapeKind
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.ui.canvas.SelectionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class NativeTransformTest {

    private val black = RnoteNativeColor(0f, 0f, 0f, 1f)
    private val eps = 1e-3f

    @Test
    fun `scaling about a point keeps that point where it is`() {
        val m = Affine.scaleAbout(10f, 20f, 3f, 0.5f)
        assertEquals(10f, Affine.mapX(m, 10f, 20f), eps)
        assertEquals(20f, Affine.mapY(m, 10f, 20f), eps)
        assertEquals(40f, Affine.mapX(m, 20f, 40f), eps)
        assertEquals(30f, Affine.mapY(m, 20f, 40f), eps)
    }

    @Test
    fun `a quarter turn on screen takes right to down`() {
        val m = Affine.rotateAbout(0f, 0f, (PI / 2).toFloat())
        assertEquals(0f, Affine.mapX(m, 1f, 0f), eps)
        assertEquals(1f, Affine.mapY(m, 1f, 0f), eps)
        assertEquals(1f, Affine.widthFactor(m), eps)
    }

    @Test
    fun `composing applies the right-hand transform first`() {
        val move = floatArrayOf(1f, 0f, 0f, 1f, 5f, 0f)
        val double = Affine.scaleAbout(0f, 0f, 2f, 2f)
        val m = Affine.compose(double, move)
        assertEquals(12f, Affine.mapX(m, 1f, 0f), eps)
    }

    @Test
    fun `a scaled rectangle keeps its extents and scales its transform and line width`() {
        val rect = NativeEditing.createShape(ShapeKind.RECTANGLE, 0f, 0f, 100f, 50f, black, 2f)!!
        val scaled = NativeEditing.transform(rect, Affine.scaleAbout(0f, 0f, 2f, 2f)) as NativeShapeElement
        val shape = scaled.shape as RectShape
        assertEquals(50f, shape.halfExtentX, eps)
        assertEquals(2f, shape.transform[0], eps)
        assertEquals(100f, shape.transform[4], eps)
        assertEquals(200f, scaled.maxX, eps)
        assertEquals(100f, scaled.maxY, eps)
        assertEquals(4f, scaled.strokeWidth, eps)
        val style = scaled.raw!!.asJsonObject.getAsJsonObject("style").getAsJsonObject("smooth")
        assertEquals(4.0, style.get("stroke_width").asDouble, 1e-6)
        // The shape as it was is untouched: undo holds on to it.
        assertEquals(1f, (rect.shape as RectShape).transform[0], 0f)
    }

    @Test
    fun `a rotated line turns its endpoints and keeps its width`() {
        val line = NativeEditing.createShape(ShapeKind.LINE, 0f, 0f, 100f, 0f, black, 3f)!!
        val turned = NativeEditing.transform(line, Affine.rotateAbout(0f, 0f, (PI / 2).toFloat())) as NativeShapeElement
        val shape = turned.shape as LineShape
        assertEquals(0f, shape.x2, eps)
        assertEquals(100f, shape.y2, eps)
        assertEquals(3f, turned.strokeWidth, eps)
    }

    @Test
    fun `text is scaled through its transform, its font size unchanged`() {
        val text = NativeEditing.createText("Hallo", 10f, 10f, 20f, black, null)!!
        val scaled = NativeEditing.transform(text, Affine.scaleAbout(10f, 10f, 2f, 2f)) as NativeTextElement
        assertEquals(20f, scaled.fontSize, 0f)
        assertEquals(2f, scaled.transform[0], eps)
        assertEquals(10f, scaled.transform[4], eps)
        assertEquals("Hallo", scaled.text)
        assertTrue(scaled.maxX - scaled.minX > (text.maxX - text.minX) * 1.9f)
    }

    @Test
    fun `an image's placement is rotated in the file too`() {
        val json = """{"bitmapimage":{"image":{"data":"AAAA","rectangle":{"cuboid":{"half_extents":[1.0,1.0]},""" +
            """"transform":{"affine":[1.0,0.0,0.0,0.0,1.0,0.0,1.0,1.0,1.0]}},"pixel_width":1,"pixel_height":1,""" +
            """"memory_format":"R8g8b8a8Premultiplied"},"rectangle":{"cuboid":{"half_extents":[10.0,5.0]},""" +
            """"transform":{"affine":[1.0,0.0,0.0,0.0,1.0,0.0,50.0,50.0,1.0]}}}}"""
        val image = RnoteNativeParser.parseElementJson(json) as NativeBitmapElement
        val turned = NativeEditing.transform(image, Affine.rotateAbout(50f, 50f, (PI / 2).toFloat())) as NativeBitmapElement
        // Its 20 × 10 box stands upright now: 10 wide, 20 tall.
        assertEquals(10f, turned.maxX - turned.minX, eps)
        assertEquals(20f, turned.maxY - turned.minY, eps)
        val affine = turned.raw!!.asJsonObject.getAsJsonObject("rectangle")
            .getAsJsonObject("transform").getAsJsonArray("affine")
        assertEquals(0.0, affine[0].asDouble, 1e-6)
        assertEquals(1.0, affine[1].asDouble, 1e-6)
        assertEquals(50.0, affine[6].asDouble, 1e-4)
        assertTrue(turned.rgbaBase64 === image.rgbaBase64)
    }

    @Test
    fun `a PDF page scales about the handle's pivot`() {
        val page = NativeVectorImageElement(
            "<svg/>", 100f, 100f, 50f, 50f, floatArrayOf(1f, 0f, 0f, 1f, 50f, 50f), "document",
            0f, 0f, 100f, 100f
        )
        val scaled = NativeEditing.transform(page, Affine.scaleAbout(0f, 0f, 0.5f, 0.5f)) as NativeVectorImageElement
        assertEquals(0f, scaled.minX, eps)
        assertEquals(50f, scaled.maxX, eps)
        assertTrue(scaled.svgData === page.svgData)
    }

    @Test
    fun `ink scales its width with the geometric mean, as Rnote's does`() {
        val stroke = Stroke(
            points = listOf(StrokePoint(0f, 0f), StrokePoint(10f, 0f)),
            color = Color.Black,
            strokeWidth = 2f
        )
        val scaled = SelectionManager.transformStrokes(listOf(stroke), Affine.scaleAbout(0f, 0f, 4f, 1f)).single()
        assertEquals(40f, scaled.points[1].x, eps)
        assertEquals(4f, scaled.strokeWidth, eps)
        assertEquals(stroke.id, scaled.id)
    }

    @Test
    fun `an element read from a tree is the one read from its text`() {
        val shape = NativeEditing.createShape(ShapeKind.ELLIPSE, 0f, 0f, 40f, 20f, black, 2f)!!
        val tree = com.google.gson.JsonObject().apply { add("shapestroke", shape.raw!!.deepCopy()) }
        val fromTree = RnoteNativeParser.parseElementTree(tree) as NativeShapeElement
        assertEquals(shape.maxX, fromTree.maxX, eps)
        assertEquals(shape.strokeWidth, fromTree.strokeWidth, 0f)
    }
}
