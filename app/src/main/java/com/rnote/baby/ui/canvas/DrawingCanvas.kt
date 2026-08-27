package com.rnote.baby.ui.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStrokeStyle
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.rnote.baby.model.BrushStyle
import com.rnote.baby.model.InkPoint
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.PressureCurve
import com.rnote.baby.model.Stroke
import com.rnote.baby.model.StrokePoint
import com.rnote.baby.model.ToolConfig
import com.rnote.baby.model.ToolType
import com.rnote.baby.model.ViewportState
import com.rnote.baby.render.composeStrokePath
import java.util.IdentityHashMap
import kotlin.math.hypot

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    toolConfig: ToolConfig,
    paperStyle: PaperStyle,
    viewportState: ViewportState,
    strokes: List<Stroke>,
    selectedStrokes: SnapshotStateList<Stroke>,
    onViewportChanged: (ViewportState) -> Unit,
    onAddStroke: (Stroke) -> Unit,
    onEraseStrokes: (List<Stroke>) -> Unit,
    onSelectionDragStart: () -> Unit = {},
    onStrokesModified: (List<Stroke>) -> Unit,
    /** Called once per eraser gesture, before the first stroke of it is removed. */
    onEraseStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<InkPoint>() }
    /** Memoised stroke outlines, keyed by Stroke identity. See the draw block below. */
    val outlineCache = remember { IdentityHashMap<Stroke, Path>() }
    val lassoPoints = remember { mutableStateListOf<Offset>() }
    // selectedStrokes is owned by the caller (MainActivity) so it can be read for delete

    var isDrawing by remember { mutableStateOf(false) }
    var hoverOffset by remember { mutableStateOf<Offset?>(null) }
    var isMovingSelection by remember { mutableStateOf(false) }
    var selectionDragStart by remember { mutableStateOf(Offset.Zero) }
    // Ensures exactly one undo snapshot is taken per selection drag, on first
    // actual movement — not one per ACTION_MOVE frame, and not on a drag that
    // starts but never moves.
    var selectionMoveSnapshotTaken by remember { mutableStateOf(false) }
    // Latched at ACTION_DOWN: a gesture that began with the S-Pen side button held (or
    // with the pen's eraser end) erases for its whole duration, even if the button is
    // released halfway through. Deciding per-event instead would switch tools mid-stroke.
    var buttonEraserLatched by remember { mutableStateOf(false) }
    // One undo snapshot per eraser gesture, not one per frame that happens to hit ink.
    var eraseSnapshotTaken by remember { mutableStateOf(false) }
    // Where to paint the eraser square, in canvas units, and whether the pen is touching.
    // Rnote shows it in both states (EraserState::Proximity and ::Down) with different fills.
    var eraserCursor by remember { mutableStateOf<Offset?>(null) }
    var eraserCursorDown by remember { mutableStateOf(false) }

    // 2-finger pan/zoom tracking — done manually inside pointerInteropFilter
    // to avoid the pointerInput vs pointerInteropFilter conflict
    var lastPinchMidpoint by remember { mutableStateOf(Offset.Zero) }
    var lastPinchDistance by remember { mutableStateOf(0f) }
    var isPinching by remember { mutableStateOf(false) }

    // 1-finger pan tracking (used when finger drawing is disabled)
    var lastFingerPanPosition by remember { mutableStateOf<Offset?>(null) }

    // Rnote's eraser is `width` canvas units across, full stop — no density factor, no
    // 1.5x, no screen-space floor. Those made the tool a different physical size from
    // desktop's and stopped it scaling with zoom the way the ink it erases does.
    val eraserWidth = toolConfig.eraserWidth

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(paperStyle.currentBackgroundColor)
            // Single unified pointerInteropFilter handles drawing, erasing, pan, and pinch-zoom.
            // detectTransformGestures is intentionally NOT used — it conflicts with
            // pointerInteropFilter on the same Canvas and causes neither to work.
            .pointerInteropFilter { motionEvent ->

                // ── 2-Finger Pan & Pinch-to-Zoom ─────────────────────────────────────
                if (motionEvent.pointerCount == 2) {
                    // Cancel any in-progress single-finger stroke
                    if (isDrawing) {
                        isDrawing = false
                        currentPoints.clear()
                        lassoPoints.clear()
                    }

                    val x0 = motionEvent.getX(0)
                    val y0 = motionEvent.getY(0)
                    val x1 = motionEvent.getX(1)
                    val y1 = motionEvent.getY(1)
                    val midpoint = Offset((x0 + x1) / 2f, (y0 + y1) / 2f)
                    val distance = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()

                    when (motionEvent.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                            if (!isPinching) {
                                // First 2-finger frame — capture baseline
                                lastPinchMidpoint = midpoint
                                lastPinchDistance = distance
                                isPinching = true
                            } else {
                                // Pan delta (translation of the centroid itself)
                                val panDelta = midpoint - lastPinchMidpoint
                                // Zoom delta (ratio of current spread to previous spread)
                                val zoomDelta = if (lastPinchDistance > 0f) distance / lastPinchDistance else 1f
                                val newZoom = (viewportState.zoomScale * zoomDelta)
                                    .coerceIn(ViewportState.ZOOM_MIN, ViewportState.ZOOM_MAX)
                                val actualZoomRatio = newZoom / viewportState.zoomScale

                                // Anchor zoom at the pinch midpoint:
                                // The canvas point under lastPinchMidpoint must stay fixed after scaling.
                                // Formula: newPan = centroid + (oldPan - centroid) * zoomRatio + panDelta
                                val newPan = lastPinchMidpoint +
                                    (viewportState.panOffset - lastPinchMidpoint) * actualZoomRatio +
                                    panDelta
                                onViewportChanged(viewportState.update(newPan, newZoom))

                                lastPinchMidpoint = midpoint
                                lastPinchDistance = distance
                            }
                        }
                        MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isPinching = false
                        }
                    }
                    return@pointerInteropFilter true
                }

                // Reset pinch state when back to 1 pointer
                if (isPinching) {
                    isPinching = false
                }

                // ── Single-Pointer: Stylus / Finger Drawing ───────────────────────────
                val screenX = motionEvent.x
                val screenY = motionEvent.y
                val canvasPos = viewportState.screenToCanvas(Offset(screenX, screenY))
                val x = canvasPos.x
                val y = canvasPos.y
                val toolType = motionEvent.getToolType(0)
                val buttonState = motionEvent.buttonState

                val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
                val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_UNKNOWN

                // Rnote treats pressure as [0, 1] and substitutes Element::PRESSURE_DEFAULT
                // where the device reports none — which is what desktop records for every
                // mouse stroke. Passing Android's finger "pressure" through instead would
                // make a finger-drawn stroke here twice the width of the same stroke there.
                val rawPressure = if (isStylus) {
                    motionEvent.pressure.coerceIn(0f, 1f)
                } else {
                    StrokePoint.PRESSURE_DEFAULT
                }

                val hasStylusPrimaryButton = (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                val hasStylusSecondaryButton = (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
                val hasSecondaryButton = (buttonState and MotionEvent.BUTTON_SECONDARY) != 0

                // Stylus-only mode: finger pans the viewport instead of drawing
                if (isFinger && !toolConfig.allowFingerDrawing) {
                    when (motionEvent.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastFingerPanPosition = Offset(screenX, screenY)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val last = lastFingerPanPosition
                            if (last != null) {
                                val delta = Offset(screenX, screenY) - last
                                onViewportChanged(viewportState.update(viewportState.panOffset + delta, viewportState.zoomScale))
                            }
                            lastFingerPanPosition = Offset(screenX, screenY)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            lastFingerPanPosition = null
                        }
                    }
                    return@pointerInteropFilter true
                }

                // Hold the S-Pen side button to erase. Devices disagree on which bit the
                // barrel button sets, so all three are accepted; TOOL_TYPE_ERASER covers
                // styluses that report a flipped-to-eraser end instead of a button.
                val sPenSideButtonPressed =
                    hasStylusPrimaryButton || hasStylusSecondaryButton || hasSecondaryButton
                val eraserTipInUse = toolType == MotionEvent.TOOL_TYPE_ERASER
                val eraserRequestedNow = sPenSideButtonPressed || eraserTipInUse

                // Mid-gesture the latch decides; while hovering, the live state does, so
                // the cursor switches to the eraser square as soon as the button goes down.
                val activeTool = when {
                    isDrawing && buttonEraserLatched -> ToolType.ERASER
                    isDrawing -> toolConfig.activeTool
                    eraserRequestedNow -> ToolType.ERASER
                    else -> toolConfig.activeTool
                }

                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                        if (isStylus) {
                            if (activeTool == ToolType.ERASER) {
                                hoverOffset = null
                                eraserCursor = Offset(x, y)
                                eraserCursorDown = false
                            } else {
                                eraserCursor = null
                                hoverOffset = Offset(screenX, screenY)
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_HOVER_EXIT -> {
                        hoverOffset = null
                        eraserCursor = null
                        true
                    }

                    MotionEvent.ACTION_DOWN -> {
                        hoverOffset = null
                        isDrawing = true
                        buttonEraserLatched = eraserRequestedNow
                        eraseSnapshotTaken = false

                        val boundingBox = SelectionManager.calculateBoundingBox(selectedStrokes)
                        if (activeTool == ToolType.SELECTOR && boundingBox != null) {
                            val screenBoundingBox = Rect(
                                viewportState.canvasToScreen(boundingBox.topLeft),
                                viewportState.canvasToScreen(boundingBox.bottomRight)
                            )
                            if (screenBoundingBox.contains(Offset(screenX, screenY))) {
                                isMovingSelection = true
                                selectionMoveSnapshotTaken = false
                                selectionDragStart = Offset(x, y)
                                return@pointerInteropFilter true
                            } else {
                                selectedStrokes.clear()
                            }
                        }

                        currentPoints.clear()
                        lassoPoints.clear()
                        currentPoints.add(InkPoint(x, y, rawPressure))
                        lassoPoints.add(Offset(x, y))

                        if (activeTool == ToolType.ERASER) {
                            eraserCursor = Offset(x, y)
                            eraserCursorDown = true
                            eraseAt(Offset(x, y), eraserWidth, strokes, onEraseStrokes) {
                                if (!eraseSnapshotTaken) { onEraseStart(); eraseSnapshotTaken = true }
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isDrawing) {
                            if (isMovingSelection && selectedStrokes.isNotEmpty()) {
                                if (!selectionMoveSnapshotTaken) {
                                    onSelectionDragStart()
                                    selectionMoveSnapshotTaken = true
                                }
                                val delta = Offset(x, y) - selectionDragStart
                                selectionDragStart = Offset(x, y)
                                val updated = SelectionManager.translateStrokes(selectedStrokes, delta)
                                selectedStrokes.clear()
                                selectedStrokes.addAll(updated)
                                onStrokesModified(updated)
                                return@pointerInteropFilter true
                            }

                            currentPoints.add(InkPoint(x, y, rawPressure))
                            lassoPoints.add(Offset(x, y))

                            if (activeTool == ToolType.ERASER) {
                                eraserCursor = Offset(x, y)
                                eraserCursorDown = true
                                eraseAt(Offset(x, y), eraserWidth, strokes, onEraseStrokes) {
                                    if (!eraseSnapshotTaken) { onEraseStart(); eraseSnapshotTaken = true }
                                }
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isMovingSelection) {
                            isMovingSelection = false
                        } else if (isDrawing) {
                            if (activeTool == ToolType.SELECTOR && lassoPoints.size >= 3) {
                                val found = SelectionManager.findStrokesInLasso(lassoPoints, strokes)
                                selectedStrokes.clear()
                                selectedStrokes.addAll(found)
                            } else if (activeTool == ToolType.BRUSH && currentPoints.isNotEmpty()) {
                                val isMarker = toolConfig.brushStyle == BrushStyle.MARKER

                                onAddStroke(
                                    Stroke(
                                        points = currentPoints.toList(),
                                        color = toolConfig.currentActiveColor,
                                        // The picker's number, stored as-is. Pressure is
                                        // already on every point and gets applied at draw
                                        // time by the curve below; folding it in here too
                                        // made desktop Rnote apply it a second time.
                                        strokeWidth = toolConfig.currentActiveSize,
                                        toolType = activeTool,
                                        isHighlighter = isMarker,
                                        pressureCurve = pressureCurveFor(toolConfig)
                                    )
                                )
                            }
                            currentPoints.clear()
                            lassoPoints.clear()
                        }
                        isDrawing = false
                        buttonEraserLatched = false
                        eraseSnapshotTaken = false
                        eraserCursorDown = false
                        // The pen may still be hovering; the square is repainted by the
                        // next hover event, and hidden if the pen has left entirely.
                        eraserCursor = null
                        true
                    }

                    else -> false
                }
            }
    ) {
        // 1. Render Infinite Paper Background
        PaperBackgroundRenderer.drawPaperBackground(
            drawScope = this,
            paperStyle = paperStyle,
            zoomLevel = viewportState.effectiveScale,
            panOffset = viewportState.panOffset
        )

        // Apply Viewport Transform Matrix (Zoom & Pan)
        withTransform({
            translate(viewportState.panOffset.x, viewportState.panOffset.y)
            scale(viewportState.effectiveScale, viewportState.effectiveScale, Offset.Zero)
        }) {
            // 2. Render existing strokes.
            // Filled variable-width outlines, not constant-width stroked paths — see
            // StrokeOutline for why that's the only way the width can match desktop.
            // Outlines are ~4x the work of the old polyline, so they're memoised on the
            // Stroke instance; strokes are immutable, and every edit path (translate,
            // scale) produces a fresh copy, so identity is a safe key.
            if (outlineCache.size > strokes.size * 2 + 64) outlineCache.clear()
            strokes.forEach { stroke ->
                val path = outlineCache.getOrPut(stroke) {
                    composeStrokePath(stroke.points, stroke.strokeWidth, stroke.pressureCurve)
                }
                drawPath(path = path, color = stroke.color)
            }

            // 3. Render active stroke preview
            // Must agree with the input handler's latch, or a gesture that started with
            // the side button held would paint an ink preview while it erased.
            val activeTool =
                if (buttonEraserLatched) ToolType.ERASER else toolConfig.activeTool
            if (isDrawing && currentPoints.isNotEmpty() && activeTool == ToolType.BRUSH) {
                val isMarker = toolConfig.brushStyle == BrushStyle.MARKER
                // Built the same way as a committed stroke, so what's under the nib is
                // what gets saved — the old preview used only the latest pressure and so
                // showed one uniform width for a stroke that would be drawn tapered.
                val path = composeStrokePath(
                    currentPoints,
                    toolConfig.currentActiveSize,
                    pressureCurveFor(toolConfig)
                )
                drawPath(path = path, color = toolConfig.currentActiveColor)
            }

            // 4. Render lasso polygon preview
            if (isDrawing && activeTool == ToolType.SELECTOR && lassoPoints.size >= 2) {
                val lassoPath = Path()
                lassoPath.moveTo(lassoPoints[0].x, lassoPoints[0].y)
                for (i in 1 until lassoPoints.size) {
                    lassoPath.lineTo(lassoPoints[i].x, lassoPoints[i].y)
                }
                drawPath(
                    path = lassoPath,
                    color = Color(0xFFC792EA),
                    style = CanvasStrokeStyle(
                        width = 2f / viewportState.effectiveScale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
            }

            // 5. Render selection bounding box
            val bbox = SelectionManager.calculateBoundingBox(selectedStrokes)
            bbox?.let { box ->
                val inflated = box.inflate(12f / viewportState.effectiveScale)
                drawRoundRect(
                    color = Color(0xFF82AAFF),
                    topLeft = inflated.topLeft,
                    size = inflated.size,
                    cornerRadius = CornerRadius(8f, 8f),
                    style = CanvasStrokeStyle(
                        width = 2.5f / viewportState.effectiveScale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                )
            }

            // 6. Render the eraser square, in canvas space so it scales with zoom exactly
            // as Rnote's does (Eraser::draw_on_doc). Colours are Rnote's GNOME reds:
            // fill GNOME_REDS[0] at a=160 when down and a=51 in proximity, outline
            // GNOME_REDS[2] at a=240, two screen pixels wide at any zoom.
            eraserCursor?.let { center ->
                val bounds = EraserHitTest.eraserBounds(center, toolConfig.eraserWidth)
                val outlineWidth = 2f / viewportState.effectiveScale
                drawRect(
                    color = if (eraserCursorDown) ERASER_FILL else ERASER_PROXIMITY_FILL,
                    topLeft = bounds.topLeft,
                    size = bounds.size
                )
                val outline = bounds.deflate(outlineWidth * 0.5f)
                drawRect(
                    color = ERASER_OUTLINE,
                    topLeft = outline.topLeft,
                    size = outline.size,
                    style = CanvasStrokeStyle(width = outlineWidth)
                )
            }
        }

        // 7. Render the brush hover cursor (screen space). The eraser has its own
        // indicator above, drawn in canvas space because its size is a document size.
        hoverOffset?.let { hoverPos ->
            drawCircle(
                color = toolConfig.currentActiveColor,
                radius = (toolConfig.currentActiveSize * viewportState.effectiveScale) / 2f,
                center = hoverPos
            )
        }
    }
}

/**
 * The pressure curve a stroke drawn with [toolConfig] should carry, following how
 * desktop Rnote configures its own brushes in `pensconfig/brushconfig.rs`.
 */
private fun pressureCurveFor(toolConfig: ToolConfig): PressureCurve = when {
    // Rnote's MarkerOptions pin the curve to Const: a marker is a constant-width nib.
    toolConfig.brushStyle == BrushStyle.MARKER -> PressureCurve.CONST
    // Our "pressure sensitivity" switch is the same choice Rnote exposes as the curve.
    !toolConfig.isPressureSensitive -> PressureCurve.CONST
    else -> PressureCurve.LINEAR
}

/**
 * Rnote's `Eraser` colours (GNOME palette reds, see Eraser::draw_on_doc).
 */
private val ERASER_OUTLINE = Color(0xE0E01B24)
private val ERASER_FILL = Color(0xA0F66151)
private val ERASER_PROXIMITY_FILL = Color(0x33F66151)

/**
 * Trashes every stroke the eraser square touches, Rnote's default
 * `EraserStyle::TrashCollidingStrokes`. [onFirstHit] fires before the first removal of a
 * gesture, so the whole drag collapses into one undo step rather than one per frame.
 */
private fun eraseAt(
    center: Offset,
    eraserWidth: Float,
    strokes: List<Stroke>,
    onEraseStrokes: (List<Stroke>) -> Unit,
    onFirstHit: () -> Unit
) {
    val bounds = EraserHitTest.eraserBounds(center, eraserWidth)
    val hit = EraserHitTest.collidingStrokes(bounds, strokes)
    if (hit.isNotEmpty()) {
        onFirstHit()
        onEraseStrokes(hit)
    }
}

