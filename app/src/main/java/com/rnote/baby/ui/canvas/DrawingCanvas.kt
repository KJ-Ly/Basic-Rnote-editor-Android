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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStrokeStyle
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import com.rnote.baby.model.InkPoint
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.Stroke
import com.rnote.baby.model.ToolConfig
import com.rnote.baby.model.ToolType
import com.rnote.baby.model.ViewportState
import kotlin.math.hypot
import kotlin.math.sqrt

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
    onStrokesModified: (List<Stroke>) -> Unit,
    onUndoRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val currentPoints = remember { mutableStateListOf<InkPoint>() }
    val lassoPoints = remember { mutableStateListOf<Offset>() }
    // selectedStrokes is owned by the caller (MainActivity) so it can be read for delete

    var isDrawing by remember { mutableStateOf(false) }
    var hoverOffset by remember { mutableStateOf<Offset?>(null) }
    var lastUndoTriggerTime by remember { mutableStateOf(0L) }
    var isMovingSelection by remember { mutableStateOf(false) }
    var selectionDragStart by remember { mutableStateOf(Offset.Zero) }
    // Edge-trigger: tracks whether the side button was already down so undo
    // fires exactly once per press, not repeatedly while the button is held.
    var sideButtonWasDown by remember { mutableStateOf(false) }

    // 2-finger pan/zoom tracking — done manually inside pointerInteropFilter
    // to avoid the pointerInput vs pointerInteropFilter conflict
    var lastPinchMidpoint by remember { mutableStateOf(Offset.Zero) }
    var lastPinchDistance by remember { mutableStateOf(0f) }
    var isPinching by remember { mutableStateOf(false) }

    // 1-finger pan tracking (used when finger drawing is disabled)
    var lastFingerPanPosition by remember { mutableStateOf<Offset?>(null) }

    val eraserRadiusPx = (toolConfig.eraserWidth * density * 1.5f).coerceAtLeast(48f)

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
                                val newZoom = (viewportState.zoomScale * zoomDelta).coerceIn(0.25f, 5.0f)
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
                val rawPressure = motionEvent.pressure.coerceIn(0.05f, 2.0f)
                val toolType = motionEvent.getToolType(0)
                val buttonState = motionEvent.buttonState

                val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
                val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_UNKNOWN

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

                // S-Pen side button → Undo, fires once per press (edge-triggered)
                val sPenSideButtonPressed = hasStylusPrimaryButton || hasStylusSecondaryButton || hasSecondaryButton
                if (sPenSideButtonPressed) {
                    if (!sideButtonWasDown) {
                        // Leading edge: button just went down — fire undo once
                        sideButtonWasDown = true
                        onUndoRequested()
                    }
                    // Button still held — do nothing further
                    return@pointerInteropFilter true
                } else {
                    // Trailing edge: button released — arm for next press
                    sideButtonWasDown = false
                }

                val activeTool = toolConfig.activeTool

                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                        if (isStylus) hoverOffset = Offset(screenX, screenY)
                        true
                    }

                    MotionEvent.ACTION_HOVER_EXIT -> {
                        hoverOffset = null
                        true
                    }

                    MotionEvent.ACTION_DOWN -> {
                        hoverOffset = null
                        isDrawing = true

                        val boundingBox = SelectionManager.calculateBoundingBox(selectedStrokes)
                        if (activeTool == ToolType.SELECT && boundingBox != null) {
                            val screenBoundingBox = Rect(
                                viewportState.canvasToScreen(boundingBox.topLeft),
                                viewportState.canvasToScreen(boundingBox.bottomRight)
                            )
                            if (screenBoundingBox.contains(Offset(screenX, screenY))) {
                                isMovingSelection = true
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
                            eraseStrokesNear(Offset(x, y), eraserRadiusPx / viewportState.zoomScale, strokes, onEraseStrokes)
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isDrawing) {
                            if (isMovingSelection && selectedStrokes.isNotEmpty()) {
                                val delta = Offset(x, y) - selectionDragStart
                                selectionDragStart = Offset(x, y)
                                val updated = SelectionManager.translateStrokes(selectedStrokes, delta)
                                selectedStrokes.clear()
                                selectedStrokes.addAll(updated)
                                onStrokesModified(strokes)
                                return@pointerInteropFilter true
                            }

                            currentPoints.add(InkPoint(x, y, rawPressure))
                            lassoPoints.add(Offset(x, y))

                            if (activeTool == ToolType.ERASER) {
                                eraseStrokesNear(Offset(x, y), eraserRadiusPx / viewportState.zoomScale, strokes, onEraseStrokes)
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isMovingSelection) {
                            isMovingSelection = false
                        } else if (isDrawing) {
                            if (activeTool == ToolType.SELECT && lassoPoints.size >= 3) {
                                val found = SelectionManager.findStrokesInLasso(lassoPoints, strokes)
                                selectedStrokes.clear()
                                selectedStrokes.addAll(found)
                            } else if (activeTool != ToolType.ERASER && activeTool != ToolType.SELECT && currentPoints.isNotEmpty()) {
                                val strokeColor = if (activeTool == ToolType.HIGHLIGHTER) {
                                    toolConfig.highlighterColor
                                } else {
                                    toolConfig.penColor
                                }

                                val avgPressure = currentPoints.map { it.pressure }.average().toFloat()
                                val baseWidth = if (activeTool == ToolType.HIGHLIGHTER) {
                                    toolConfig.highlighterWidth
                                } else {
                                    toolConfig.strokeWidth
                                }

                                val finalWidth = if (toolConfig.isPressureSensitive && isStylus) {
                                    baseWidth * (0.35f + 1.15f * avgPressure)
                                } else {
                                    baseWidth
                                }

                                onAddStroke(
                                    Stroke(
                                        points = currentPoints.toList(),
                                        color = strokeColor,
                                        strokeWidth = finalWidth,
                                        toolType = activeTool,
                                        isHighlighter = activeTool == ToolType.HIGHLIGHTER,
                                        alpha = if (activeTool == ToolType.HIGHLIGHTER) 0.35f else 1.0f
                                    )
                                )
                            }
                            currentPoints.clear()
                            lassoPoints.clear()
                        }
                        isDrawing = false
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
            zoomLevel = viewportState.zoomScale,
            panOffset = viewportState.panOffset
        )

        // Apply Viewport Transform Matrix (Zoom & Pan)
        withTransform({
            translate(viewportState.panOffset.x, viewportState.panOffset.y)
            scale(viewportState.zoomScale, viewportState.zoomScale, Offset.Zero)
        }) {
            // 2. Render existing strokes
            strokes.forEach { stroke ->
                val path = InkSmoother.createSmoothPath(stroke.points)
                val strokeCap = if (stroke.toolType == ToolType.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round

                drawPath(
                    path = path,
                    color = stroke.color,
                    alpha = stroke.alpha,
                    style = CanvasStrokeStyle(
                        width = stroke.width,
                        cap = strokeCap,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Render active stroke preview
            val activeTool = toolConfig.activeTool
            if (isDrawing && currentPoints.isNotEmpty() && activeTool != ToolType.ERASER && activeTool != ToolType.SELECT) {
                val path = InkSmoother.createSmoothPath(currentPoints)
                val color = if (activeTool == ToolType.HIGHLIGHTER) toolConfig.highlighterColor else toolConfig.penColor
                val lastPressure = currentPoints.last().pressure
                val baseWidth = if (activeTool == ToolType.HIGHLIGHTER) toolConfig.highlighterWidth else toolConfig.strokeWidth
                val previewWidth = if (toolConfig.isPressureSensitive) baseWidth * (0.35f + 1.15f * lastPressure) else baseWidth
                val alpha = if (activeTool == ToolType.HIGHLIGHTER) 0.35f else 1.0f

                drawPath(
                    path = path,
                    color = color,
                    alpha = alpha,
                    style = CanvasStrokeStyle(
                        width = previewWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 4. Render lasso polygon preview
            if (isDrawing && activeTool == ToolType.SELECT && lassoPoints.size >= 2) {
                val lassoPath = Path()
                lassoPath.moveTo(lassoPoints[0].x, lassoPoints[0].y)
                for (i in 1 until lassoPoints.size) {
                    lassoPath.lineTo(lassoPoints[i].x, lassoPoints[i].y)
                }
                drawPath(
                    path = lassoPath,
                    color = Color(0xFFC792EA),
                    style = CanvasStrokeStyle(
                        width = 2f / viewportState.zoomScale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
            }

            // 5. Render selection bounding box
            val bbox = SelectionManager.calculateBoundingBox(selectedStrokes)
            bbox?.let { box ->
                val inflated = box.inflate(12f / viewportState.zoomScale)
                drawRoundRect(
                    color = Color(0xFF82AAFF),
                    topLeft = inflated.topLeft,
                    size = inflated.size,
                    cornerRadius = CornerRadius(8f, 8f),
                    style = CanvasStrokeStyle(
                        width = 2.5f / viewportState.zoomScale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                )
            }
        }

        // 6. Render hover cursor (screen space)
        hoverOffset?.let { hoverPos ->
            val activeTool = toolConfig.activeTool
            if (activeTool == ToolType.ERASER) {
                drawCircle(
                    color = Color.Red.copy(alpha = 0.6f),
                    radius = eraserRadiusPx,
                    center = hoverPos,
                    style = CanvasStrokeStyle(width = 3f)
                )
            } else {
                val cursorColor = if (activeTool == ToolType.HIGHLIGHTER) toolConfig.highlighterColor else toolConfig.penColor
                drawCircle(
                    color = cursorColor,
                    radius = (toolConfig.strokeWidth * viewportState.zoomScale) / 2f,
                    center = hoverPos
                )
            }
        }
    }
}

private fun eraseStrokesNear(
    touchPos: Offset,
    eraserRadiusPx: Float,
    strokes: List<Stroke>,
    onEraseStrokes: (List<Stroke>) -> Unit
) {
    val erasedStrokes = strokes.filter { stroke ->
        val points = stroke.points
        if (points.isEmpty()) false
        else {
            val totalHit = eraserRadiusPx + (stroke.width / 2f)
            points.any { pt -> hypot(pt.x - touchPos.x, pt.y - touchPos.y) <= totalHit }
        }
    }
    if (erasedStrokes.isNotEmpty()) {
        onEraseStrokes(erasedStrokes)
    }
}
