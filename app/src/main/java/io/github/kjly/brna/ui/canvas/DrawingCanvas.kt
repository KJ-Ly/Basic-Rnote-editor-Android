package io.github.kjly.brna.ui.canvas

import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStrokeStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import io.github.kjly.brna.audio.PenSounds
import io.github.kjly.brna.model.Affine
import io.github.kjly.brna.model.BrushStyle
import io.github.kjly.brna.model.EraserMode
import io.github.kjly.brna.model.InkPoint
import io.github.kjly.brna.model.NativeBitmapElement
import io.github.kjly.brna.model.NativeCanvasElement
import io.github.kjly.brna.model.NativeShapeElement
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.NativeVectorImageElement
import io.github.kjly.brna.model.PaperStyle
import io.github.kjly.brna.model.PenPathBuilder
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.RoughStyle
import io.github.kjly.brna.model.SelectorMode
import io.github.kjly.brna.model.ShortcutKey
import io.github.kjly.brna.model.ShapeKind
import io.github.kjly.brna.model.ShapeLine
import io.github.kjly.brna.model.ShapeLineCap
import io.github.kjly.brna.model.SnapPositions
import io.github.kjly.brna.model.Stroke
import io.github.kjly.brna.model.StrokePoint
import io.github.kjly.brna.model.ToolConfig
import io.github.kjly.brna.model.ToolType
import io.github.kjly.brna.model.ToolsMode
import io.github.kjly.brna.model.ViewportState
import io.github.kjly.brna.render.CurvedPathBuilder
import io.github.kjly.brna.render.ModeledPathBuilder
import io.github.kjly.brna.render.PenPathBuilding
import io.github.kjly.brna.render.NativeElementRenderer
import io.github.kjly.brna.render.VectorImageRenderer
import io.github.kjly.brna.storage.NativeEditing
import io.github.kjly.brna.storage.ShapeBuilders
import io.github.kjly.brna.storage.ShapeDraft
import androidx.input.motionprediction.MotionEventPredictor
import io.github.kjly.brna.render.composeStrokePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * One background thread for the zoom-detail renders: they are superseded as the view
 * moves, and letting them run side by side would only stack up memory for results that
 * are already out of date.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val detailDispatcher = Dispatchers.Default.limitedParallelism(1)

/** How long the view has to be still before sharper page renders are made for it. */
private const val DETAIL_SETTLE_MS = 250L

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
    /**
     * Elements from a desktop .rnote this app shows but doesn't edit: PDF pages and images
     * (drawn under the ink, as Rnote's document and image layers are) and text and shapes
     * (drawn over it). Brush strokes in here are ignored; [strokes] is what is drawn.
     */
    nativeElements: List<NativeCanvasElement> = emptyList(),
    modifier: Modifier = Modifier,
    /** The desktop elements (text, shapes, images) the selector currently holds. */
    selectedNatives: SnapshotStateList<NativeCanvasElement>? = null,
    /** What the Shaper just finished drawing: one shape, or the lines of a grid or axes. */
    onAddShapes: (List<NativeShapeElement>) -> Unit = {},
    /** Rnote's pen sounds, when they are switched on. */
    penSounds: PenSounds? = null,
    /** Shapes the eraser went over; part of the same gesture as [onEraseStrokes]. */
    onEraseNatives: (List<NativeCanvasElement>) -> Unit = {},
    /** Desktop elements the selector moved: old instance -> moved copy. */
    onNativesMoved: (IdentityHashMap<NativeCanvasElement, NativeCanvasElement>) -> Unit = {},
    /**
     * The splitting eraser cut strokes apart: stroke id -> the pieces that replace it
     * (none if nothing is left). Part of the same gesture as [onEraseStrokes].
     */
    onSplitStrokes: (Map<String, List<Stroke>>) -> Unit = {},
    /** The Typewriter was tapped at this canvas position: start or edit a text box there. */
    onTypewriterTap: (Float, Float) -> Unit = { _, _ -> },
    /**
     * The Tools pen's vertical space: move these strokes (by id) and desktop elements (by
     * identity) down by this much, or up for a negative amount. Once per drag, at the end.
     */
    onVerticalSpace: (Float, Set<String>, Set<NativeCanvasElement>) -> Unit = { _, _, _ -> },
    /**
     * Rnote's button shortcuts: a pen or mouse button, or the two-finger long-press, went
     * down (true) or came up (false). Answers with the pen in use after it.
     */
    onShortcutKey: (ShortcutKey, Boolean) -> ToolType = { _, _ -> toolConfig.activeTool },
    /** Something drawn — by the pen, a mouse or a finger that draws — has ended. */
    onPenGestureEnd: () -> Unit = {}
) {
    val underlays = remember(nativeElements) { nativeElements.filter(NativeElementRenderer::isUnderlay) }
    val overlays = remember(nativeElements) { nativeElements.filter(NativeElementRenderer::isOverlay) }
    // One renderer for the canvas's lifetime: its caches are per element, and editing
    // replaces only the elements that changed.
    val nativeRenderer = remember { NativeElementRenderer() }
    LaunchedEffect(overlays) { nativeRenderer.retainOnly(overlays) }

    // Pages and images are turned into bitmaps once, off the main thread, one at a time
    // (a PDF page can be megabytes of SVG). Until then a page shows as a blank sheet.
    // Keyed by the image data, not the element: moving a page makes a new element around
    // the same data, and that must not be rendered all over again.
    val underlayBitmaps = remember { mutableStateMapOf<Any, android.graphics.Bitmap>() }
    LaunchedEffect(underlays) {
        val wanted = underlays.mapNotNull(::imageKey).toSet()
        (underlayBitmaps.keys - wanted).forEach { underlayBitmaps.remove(it) }
        for (el in underlays) {
            val key = imageKey(el) ?: continue
            if (key in underlayBitmaps) continue
            val bitmap = withContext(Dispatchers.Default) {
                when (el) {
                    is NativeVectorImageElement -> VectorImageRenderer.rasterize(el)
                    is NativeBitmapElement -> NativeElementRenderer.decodeBitmap(el)
                    else -> null
                }
            }
            if (bitmap != null) underlayBitmaps[key] = bitmap
        }
    }

    // Sharper renders of the visible part of each PDF page, made once the view has come
    // to rest at a zoom the one-off bitmap can't resolve.
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val detailTiles = remember { mutableStateMapOf<NativeCanvasElement, VectorImageRenderer.DetailTile>() }
    // underlayBitmaps.size is a key so a page whose base bitmap arrives while the view is
    // already zoomed in still gets its sharp render without waiting for the next pan.
    LaunchedEffect(underlays, viewportState, viewSize, underlayBitmaps.size) {
        delay(DETAIL_SETTLE_MS)
        if (viewSize.width == 0 || viewSize.height == 0) return@LaunchedEffect
        val scale = viewportState.effectiveScale
        val viewLeft = -viewportState.panOffset.x / scale
        val viewTop = -viewportState.panOffset.y / scale
        val viewRight = viewLeft + viewSize.width / scale
        val viewBottom = viewTop + viewSize.height / scale
        val wanted = HashMap<NativeCanvasElement, VectorImageRenderer.DetailTile>()
        underlays.forEach { el ->
            if (el !is NativeVectorImageElement) return@forEach
            val base = underlayBitmaps[el.svgData] ?: return@forEach
            // Device pixels per document unit the base bitmap has to offer.
            val basePxPerUnit = base.width / (el.maxX - el.minX).coerceAtLeast(1f)
            if (scale <= basePxPerUnit * 1.25f) return@forEach
            val l = maxOf(el.minX, viewLeft)
            val t = maxOf(el.minY, viewTop)
            val r = minOf(el.maxX, viewRight)
            val b = minOf(el.maxY, viewBottom)
            if (r <= l || b <= t) return@forEach
            val existing = detailTiles[el]
            if (existing != null && existing.pxPerUnit == scale && existing.covers(l, t, r, b)) {
                wanted[el] = existing
                return@forEach
            }
            val tile = withContext(detailDispatcher) {
                VectorImageRenderer.renderRegion(el, l, t, r, b, scale)
            } ?: return@forEach
            wanted[el] = tile
        }
        (detailTiles.keys - wanted.keys).forEach { detailTiles.remove(it) }
        detailTiles.putAll(wanted)
    }

    val vectorPaint = remember {
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    }
    val placeholderPaint = remember {
        android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
    }

    // The Shaper's drag, in canvas units; null when no shape is being drawn.
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }
    // A grid's first cell, drawn and waiting for the second drag that repeats it. Only
    // meaningful while the Shaper stays on the grid.
    var gridCell by remember { mutableStateOf<GridCell?>(null) }
    // A polyline, polygon, curve or foci ellipse between the strokes that make it. Another
    // pen or shape drops it unfinished, as Rnote cancels its builder.
    var draft by remember { mutableStateOf<ShapeDraft?>(null) }
    LaunchedEffect(toolConfig.activeTool, toolConfig.shapeKind) {
        gridCell = null
        draft = null
    }
    /** [p] on the page's pattern when Snap Positions is on, as Rnote's `snap_position`. */
    fun snapped(p: Offset): Offset = if (toolConfig.snapPositions) SnapPositions.snap(p, paperStyle) else p

    // Where the pen is about to be, from Android's motion prediction: drawn at the tip of
    // the stroke being written so the ink keeps up with the pen, never saved.
    val view = LocalView.current
    val predictor = remember(view) { MotionEventPredictor.newInstance(view) }
    val predictedPoints = remember { mutableStateListOf<InkPoint>() }

    // The Laser's finished trails, in canvas units. Once the pen lifts they fade out
    // together in a second, as Rnote's LaserTool fades; drawing again brings them back.
    val laserTrails = remember { mutableStateListOf<List<Offset>>() }
    var laserFading by remember { mutableStateOf(false) }
    var laserOpacity by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(laserFading) {
        if (!laserFading) return@LaunchedEffect
        val start = withFrameMillis { it }
        var elapsed = 0L
        while (elapsed < LASER_FADE_MS) {
            elapsed = withFrameMillis { it } - start
            laserOpacity = (1f - elapsed.toFloat() / LASER_FADE_MS).coerceIn(0f, 1f)
        }
        laserTrails.clear()
        laserOpacity = 1f
        laserFading = false
    }

    val currentPoints = remember { mutableStateListOf<InkPoint>() }
    // The Textured brush's seed for the stroke being drawn: a new one for every stroke,
    // as Rnote's `new_style_seeds` gives, so its dots stay put while it is drawn.
    var texturedSeed by remember { mutableStateOf(0L) }
    // The rough style's seed for the shape being drawn: new with every shape, however
    // many strokes of the pen it takes, as Rnote's shaper picks one when it starts one.
    var roughSeed by remember { mutableStateOf(0L) }
    // Rnote's path builder for the stroke being drawn, when the brush curves or models its paths.
    var pathBuilder by remember { mutableStateOf<PenPathBuilding?>(null) }
    /** Memoised stroke bounds and outlines, keyed by Stroke identity. See the draw block below. */
    val outlineCache = remember { IdentityHashMap<Stroke, CachedOutline>() }
    val lassoPoints = remember { mutableStateListOf<Offset>() }
    // selectedStrokes is owned by the caller (MainActivity) so it can be read for delete

    var isDrawing by remember { mutableStateOf(false) }
    var hoverOffset by remember { mutableStateOf<Offset?>(null) }
    var isMovingSelection by remember { mutableStateOf(false) }
    var selectionDragStart by remember { mutableStateOf(Offset.Zero) }
    // With Snap Positions on: the selection's corner nearest where it was taken, which is
    // what lands on the pattern as it moves (Rnote's `SnapCorner`).
    var selectionSnapCorner by remember { mutableStateOf<Offset?>(null) }
    // Ensures exactly one undo snapshot is taken per selection drag, on first
    // actual movement — not one per ACTION_MOVE frame, and not on a drag that
    // starts but never moves.
    var selectionMoveSnapshotTaken by remember { mutableStateOf(false) }
    // A scale or rotate by one of the selection's handles; null when there is none.
    var transformDrag by remember { mutableStateOf<TransformDrag?>(null) }
    // Latched at ACTION_DOWN: the pen a gesture began with — a button's, or the eraser for
    // the pen's eraser end — is the pen for all of it, even if the button is let go
    // halfway through. Deciding per-event instead would switch tools mid-stroke.
    var gestureTool by remember { mutableStateOf<ToolType?>(null) }
    // The buttons held as last seen, and the pen the app answered with for them until the
    // canvas is recomposed with it: a press and a touch can come within the same frame.
    val shortcutInput = remember { ShortcutInput() }
    SideEffect { shortcutInput.pendingTool = null }
    val scope = rememberCoroutineScope()
    val touchSlop = remember(view) { ViewConfiguration.get(view.context).scaledTouchSlop.toFloat() }
    /** Tells the app of each button that went down or came up since last time. */
    fun syncShortcutKeys(held: Set<ShortcutKey>) {
        val was = shortcutInput.held
        if (held == was) return
        shortcutInput.held = held
        for (key in was - held) shortcutInput.pendingTool = onShortcutKey(key, false)
        for (key in held - was) shortcutInput.pendingTool = onShortcutKey(key, true)
    }
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

    // A Typewriter tap: where it went down (screen px, and canvas units), and whether it
    // has since moved too far to be a tap. A finger that pans the view isn't a tap.
    var tapDownScreen by remember { mutableStateOf<Offset?>(null) }
    var tapDownCanvas by remember { mutableStateOf(Offset.Zero) }

    // A vertical-space drag with the Tools pen. Only drawn shifted until the pen lifts, so
    // a long note isn't rebuilt on every frame; the document changes once, at the end.
    var spaceDrag by remember { mutableStateOf<SpaceDrag?>(null) }

    // Rnote's eraser is `width` canvas units across, full stop — no density factor, no
    // 1.5x, no screen-space floor. Those made the tool a different physical size from
    // desktop's and stopped it scaling with zoom the way the ink it erases does.
    val eraserWidth = toolConfig.eraserWidth
    val splitEraser = toolConfig.eraserMode == EraserMode.SPLIT

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .background(paperStyle.currentBackgroundColor)
            // Single unified pointerInteropFilter handles drawing, erasing, pan, and pinch-zoom.
            // detectTransformGestures is intentionally NOT used — it conflicts with
            // pointerInteropFilter on the same Canvas and causes neither to work.
            .pointerInteropFilter { motionEvent ->

                if (motionEvent.pointerCount != 2) shortcutInput.cancelLongPress()

                // ── 2-Finger Pan & Pinch-to-Zoom ─────────────────────────────────────
                if (motionEvent.pointerCount == 2) {
                    tapDownScreen = null
                    transformDrag = null
                    spaceDrag = null
                    // Cancel any in-progress single-finger stroke
                    if (isDrawing) {
                        isDrawing = false
                        currentPoints.clear()
                        lassoPoints.clear()
                        pathBuilder = null
                    }

                    val x0 = motionEvent.getX(0)
                    val y0 = motionEvent.getY(0)
                    val x1 = motionEvent.getX(1)
                    val y1 = motionEvent.getY(1)
                    val midpoint = Offset((x0 + x1) / 2f, (y0 + y1) / 2f)
                    val distance = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()

                    // Rnote's two-finger long-press shortcut: two fingers put down and held
                    // still for GTK's long-press time — 500 ms, times the 0.8 Rnote asks for.
                    val fingers = listOf(Offset(x0, y0), Offset(x1, y1))
                    when (motionEvent.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            shortcutInput.cancelLongPress()
                            val bothFingers = motionEvent.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER &&
                                motionEvent.getToolType(1) == MotionEvent.TOOL_TYPE_FINGER
                            if (bothFingers) {
                                shortcutInput.longPressStart = fingers
                                shortcutInput.longPress = scope.launch {
                                    delay(TWO_FINGER_LONG_PRESS_MS)
                                    shortcutInput.longPress = null
                                    shortcutInput.pendingTool = onShortcutKey(ShortcutKey.TOUCH_TWO_FINGER_LONG_PRESS, true)
                                }
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val start = shortcutInput.longPressStart
                            if (start != null && fingers.indices.any { (fingers[it] - start[it]).getDistance() > touchSlop }) {
                                shortcutInput.cancelLongPress()
                            }
                        }
                        else -> shortcutInput.cancelLongPress()
                    }

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
                                // Zoom delta (ratio of current spread to previous spread); none at
                                // all with Rnote's "Block Pinch to Zoom", which leaves the panning.
                                val zoomDelta = if (lastPinchDistance > 0f && !toolConfig.blockPinchZoom) {
                                    distance / lastPinchDistance
                                } else {
                                    1f
                                }
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

                // Held on a keyboard, Ctrl turns the Shaper's constraints on — or off — for
                // as long as it is held, as in Rnote.
                val ctrlHeld = (motionEvent.metaState and KeyEvent.META_CTRL_ON) != 0

                // Stylus-only mode: finger pans the viewport instead of drawing
                if (isFinger && !toolConfig.allowFingerDrawing) {
                    when (motionEvent.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastFingerPanPosition = Offset(screenX, screenY)
                            // With the Typewriter, a finger tap types too: nobody reaches
                            // for the pen to put a cursor somewhere.
                            tapDownScreen = if (toolConfig.activeTool == ToolType.TYPEWRITER) Offset(screenX, screenY) else null
                            tapDownCanvas = canvasPos
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val last = lastFingerPanPosition
                            if (last != null) {
                                val delta = Offset(screenX, screenY) - last
                                onViewportChanged(viewportState.update(viewportState.panOffset + delta, viewportState.zoomScale))
                            }
                            lastFingerPanPosition = Offset(screenX, screenY)
                            tapDownScreen?.let { if ((Offset(screenX, screenY) - it).getDistance() > TAP_SLOP_PX) tapDownScreen = null }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            lastFingerPanPosition = null
                            if (motionEvent.actionMasked == MotionEvent.ACTION_UP && tapDownScreen != null) {
                                onTypewriterTap(tapDownCanvas.x, tapDownCanvas.y)
                            }
                            tapDownScreen = null
                        }
                    }
                    return@pointerInteropFilter true
                }

                // Rnote's button shortcuts (see StylusButtons): each button going down or up is
                // handed to the app, which switches pens as that button is set to — by default
                // the S Pen's button erases while held. Only between gestures: a button pressed
                // while writing counts once the pen lifts, so no stroke is cut in two.
                if (!isDrawing) syncShortcutKeys(StylusButtons.keysOf(toolType, buttonState))
                // The pen's eraser end erases, whatever the buttons say, as Rnote's eraser mode does.
                val eraserTipInUse = toolType == MotionEvent.TOOL_TYPE_ERASER

                // Mid-gesture the latch decides; while hovering, the pen as it is now, so the
                // cursor switches to the eraser square as soon as the button goes down.
                val activeTool = when {
                    isDrawing -> gestureTool ?: toolConfig.activeTool
                    eraserTipInUse -> ToolType.ERASER
                    else -> shortcutInput.pendingTool ?: toolConfig.activeTool
                }

                // Samsung's One UI does not report a barrel-button-held stylus gesture with
                // the standard action codes. Measured on an SM-T870: DOWN/UP/MOVE arrive as
                // 211/212/213, while buttonState (BUTTON_STYLUS_PRIMARY) and toolType
                // (TOOL_TYPE_STYLUS) are both correct. Unmapped, those codes match no branch
                // below and fall through to `else -> false`, which is why holding the button
                // appeared to do nothing whatsoever -- not a failure to detect the button.
                val action = when (motionEvent.actionMasked) {
                    SAMSUNG_ACTION_PEN_DOWN -> MotionEvent.ACTION_DOWN
                    SAMSUNG_ACTION_PEN_UP -> MotionEvent.ACTION_UP
                    SAMSUNG_ACTION_PEN_MOVE -> MotionEvent.ACTION_MOVE
                    else -> motionEvent.actionMasked
                }

                // Android's motion prediction learns where the pen is heading from its events.
                if (isStylus && motionEvent.actionMasked in PREDICTED_ACTIONS) predictor.record(motionEvent)

                when (action) {
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
                        // The pen's events straight to the canvas as they come, not a frame's
                        // worth at a time, so the ink is drawn from the newest point there is.
                        if (isStylus) view.requestUnbufferedDispatch(motionEvent)
                        hoverOffset = null
                        isDrawing = true
                        predictedPoints.clear()
                        gestureTool = activeTool
                        eraseSnapshotTaken = false

                        val boundingBox = selectionBounds(selectedStrokes, selectedNatives)
                        if (activeTool == ToolType.SELECTOR && boundingBox != null) {
                            val handle = handleAt(boundingBox, viewportState, Offset(screenX, screenY))
                            if (handle != null) {
                                val corners = listOf(
                                    Offset(boundingBox.left, boundingBox.top),
                                    Offset(boundingBox.right, boundingBox.top),
                                    Offset(boundingBox.right, boundingBox.bottom),
                                    Offset(boundingBox.left, boundingBox.bottom)
                                )
                                val natives = selectedNatives?.toList() ?: emptyList()
                                transformDrag = TransformDrag(
                                    rotate = handle == ROTATE_HANDLE,
                                    pivot = if (handle == ROTATE_HANDLE) {
                                        Offset((boundingBox.left + boundingBox.right) / 2f, (boundingBox.top + boundingBox.bottom) / 2f)
                                    } else corners[(handle + 2) % 4],
                                    corner = if (handle == ROTATE_HANDLE) Offset.Zero else corners[handle],
                                    start = Offset(x, y),
                                    strokes = selectedStrokes.toList(),
                                    natives = natives,
                                    current = natives
                                )
                                selectionMoveSnapshotTaken = false
                                return@pointerInteropFilter true
                            }
                            // Rnote's single selection: tapping something not yet selected
                            // adds it, before a tap inside the box can start a move.
                            if (toolConfig.selectorMode == SelectorMode.SINGLE) {
                                val picked = SelectionManager.pickAt(
                                    Offset(x, y), strokes, nativeElements, PICK_TOLERANCE_PX / viewportState.effectiveScale
                                )
                                val added = when (picked) {
                                    is SelectionManager.Pick.Ink ->
                                        (picked.stroke !in selectedStrokes).also { if (it) selectedStrokes.add(picked.stroke) }
                                    is SelectionManager.Pick.Element -> selectedNatives != null &&
                                        selectedNatives.none { it === picked.element }.also {
                                            if (it) selectedNatives.add(picked.element)
                                        }
                                    null -> false
                                }
                                if (added) {
                                    isDrawing = false
                                    return@pointerInteropFilter true
                                }
                            }
                            val screenBoundingBox = Rect(
                                viewportState.canvasToScreen(boundingBox.topLeft),
                                viewportState.canvasToScreen(boundingBox.bottomRight)
                            )
                            if (screenBoundingBox.contains(Offset(screenX, screenY))) {
                                isMovingSelection = true
                                selectionMoveSnapshotTaken = false
                                selectionDragStart = Offset(x, y)
                                selectionSnapCorner = SnapPositions.nearestCorner(boundingBox, Offset(x, y))
                                return@pointerInteropFilter true
                            } else {
                                selectedStrokes.clear()
                                selectedNatives?.clear()
                            }
                        }

                        if (activeTool == ToolType.TOOLS && toolConfig.toolsMode == ToolsMode.VERTICAL_SPACE) {
                            // What moves is settled here, as in Rnote: dragging back up past
                            // the line must not start picking up what was above it.
                            spaceDrag = SpaceDrag(
                                y,
                                VerticalSpace.strokesBelow(strokes, y),
                                VerticalSpace.nativesBelow(nativeElements, y)
                            )
                            return@pointerInteropFilter true
                        }
                        if (activeTool == ToolType.TOOLS) {
                            // A new laser trail stops the fade: the earlier ones are back too.
                            laserFading = false
                            laserOpacity = 1f
                        }
                        if (activeTool == ToolType.SHAPER) {
                            val pos = snapped(Offset(x, y))
                            // A draft of another shape (the shape was switched a moment ago) starts over.
                            val pending = draft?.takeIf { it.kind == toolConfig.shapeKind }
                            if (ShapeDraft.isMultiStroke(toolConfig.shapeKind)) {
                                if (pending == null) roughSeed = kotlin.random.Random.nextLong()
                                // The next stroke of a shape drawn in several; the first starts it.
                                draft = pending?.down(
                                    pos,
                                    toolConfig.shapeConstraints.withCtrl(ctrlHeld),
                                    maxOf(ShapeDraft.FINISH_DISTANCE, FINISH_TOLERANCE_PX / viewportState.effectiveScale)
                                ) ?: ShapeDraft.start(toolConfig.shapeKind, pos)
                                shapeStart = null
                                shapeEnd = null
                            } else {
                                // The grid's second drag spans from its first cell's corner,
                                // wherever the pen comes down, as in Rnote's GridBuilder.
                                val cell = gridCell.takeIf { toolConfig.shapeKind == ShapeKind.GRID }
                                if (cell == null) roughSeed = kotlin.random.Random.nextLong()
                                val start = cell?.start ?: pos
                                shapeStart = start
                                shapeEnd = shapeEndFor(toolConfig, start, pos, ctrlHeld)
                            }
                        }
                        tapDownScreen = if (activeTool == ToolType.TYPEWRITER) Offset(screenX, screenY) else null
                        tapDownCanvas = Offset(x, y)

                        currentPoints.clear()
                        lassoPoints.clear()
                        currentPoints.add(InkPoint(x, y, rawPressure))
                        lassoPoints.add(Offset(x, y))
                        texturedSeed = kotlin.random.Random.nextLong()
                        // Rnote's brush: the marker squeaks once as it touches down, the
                        // others scratch for as long as they move.
                        if (activeTool == ToolType.BRUSH) {
                            if (toolConfig.brushStyle == BrushStyle.MARKER) penSounds?.marker() else penSounds?.brush()
                        }
                        // The stroke starts at the pen-down sample either way; with the
                        // curved or modeled builder, what follows is what it makes of the pen.
                        pathBuilder = if (activeTool != ToolType.BRUSH) {
                            null
                        } else {
                            when (toolConfig.penPathBuilder) {
                                PenPathBuilder.SIMPLE -> null
                                PenPathBuilder.CURVED -> CurvedPathBuilder(InkPoint(x, y, rawPressure))
                                PenPathBuilder.MODELED ->
                                    ModeledPathBuilder(InkPoint(x, y, rawPressure), eventSeconds(motionEvent, motionEvent.historySize))
                            }
                        }

                        if (activeTool == ToolType.ERASER) {
                            eraserCursor = Offset(x, y)
                            eraserCursorDown = true
                            eraseAt(Offset(x, y), eraserWidth, splitEraser, strokes, onEraseStrokes, onSplitStrokes, overlays, onEraseNatives) {
                                if (!eraseSnapshotTaken) { onEraseStart(); eraseSnapshotTaken = true }
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isDrawing) {
                            spaceDrag?.let { space ->
                                val snapOffset: ((Float) -> Float)? = if (toolConfig.snapPositions) {
                                    { v -> snapped(Offset(x, v)).y }
                                } else {
                                    null
                                }
                                space.offset = VerticalSpace.offset(space.startY, y, snapOffset)
                                return@pointerInteropFilter true
                            }
                            val drag = transformDrag
                            if (drag != null) {
                                if (!selectionMoveSnapshotTaken) {
                                    onSelectionDragStart()
                                    selectionMoveSnapshotTaken = true
                                }
                                // Rnote snaps the dragged corner only when the ratio is free.
                                val m = dragTransform(
                                    drag, Offset(x, y), toolConfig.lockAspectRatio,
                                    if (toolConfig.snapPositions) ::snapped else null
                                )
                                if (drag.strokes.isNotEmpty()) {
                                    val updated = SelectionManager.transformStrokes(drag.strokes, m)
                                    selectedStrokes.clear()
                                    selectedStrokes.addAll(updated)
                                    onStrokesModified(updated)
                                }
                                val selected = selectedNatives
                                if (selected != null && drag.natives.isNotEmpty()) {
                                    val next = drag.natives.map { NativeEditing.transform(it, m) }
                                    val moved = IdentityHashMap<NativeCanvasElement, NativeCanvasElement>()
                                    drag.current.forEachIndexed { i, el -> moved[el] = next[i] }
                                    drag.current = next
                                    selected.clear()
                                    selected.addAll(next)
                                    onNativesMoved(moved)
                                }
                                return@pointerInteropFilter true
                            }
                            val natives = selectedNatives
                            if (isMovingSelection &&
                                (selectedStrokes.isNotEmpty() || !natives.isNullOrEmpty())
                            ) {
                                val corner = selectionSnapCorner
                                val delta = if (toolConfig.snapPositions && corner != null) {
                                    // Rnote's snapped translate: the corner goes to the pattern
                                    // point nearest where the pen would take it, and the pen's
                                    // leftover movement waits for the next point.
                                    val step = snapped(corner + (Offset(x, y) - selectionDragStart)) - corner
                                    val threshold = SnapPositions.TRANSLATE_THRESHOLD_PX / viewportState.effectiveScale
                                    if (step.getDistance() > threshold) step else Offset.Zero
                                } else {
                                    Offset(x, y) - selectionDragStart
                                }
                                if (delta == Offset.Zero) return@pointerInteropFilter true
                                if (!selectionMoveSnapshotTaken) {
                                    onSelectionDragStart()
                                    selectionMoveSnapshotTaken = true
                                }
                                selectionDragStart += delta
                                selectionSnapCorner = corner?.plus(delta)
                                if (selectedStrokes.isNotEmpty()) {
                                    val updated = SelectionManager.translateStrokes(selectedStrokes, delta)
                                    selectedStrokes.clear()
                                    selectedStrokes.addAll(updated)
                                    onStrokesModified(updated)
                                }
                                if (!natives.isNullOrEmpty()) {
                                    val moved = IdentityHashMap<NativeCanvasElement, NativeCanvasElement>()
                                    val updated = natives.map { old ->
                                        NativeEditing.translate(old, delta.x, delta.y).also { moved[old] = it }
                                    }
                                    natives.clear()
                                    natives.addAll(updated)
                                    onNativesMoved(moved)
                                }
                                return@pointerInteropFilter true
                            }

                            val start = shapeStart
                            if (activeTool == ToolType.SHAPER && start != null) {
                                shapeEnd = shapeEndFor(toolConfig, start, snapped(Offset(x, y)), ctrlHeld)
                                return@pointerInteropFilter true
                            }
                            val pending = draft
                            if (activeTool == ToolType.SHAPER && pending != null) {
                                draft = pending.move(snapped(Offset(x, y)), toolConfig.shapeConstraints.withCtrl(ctrlHeld))
                                return@pointerInteropFilter true
                            }
                            if (activeTool == ToolType.TYPEWRITER) {
                                tapDownScreen?.let {
                                    if ((Offset(screenX, screenY) - it).getDistance() > TAP_SLOP_PX) tapDownScreen = null
                                }
                                return@pointerInteropFilter true
                            }

                            // Android hands the pen's samples over a frame at a time, the ones in
                            // between as the event's history. Rnote takes every one (its input
                            // handling walks the event's history too), so a quick curve keeps
                            // the shape it was written with, and a quick eraser misses nothing.
                            val builder = pathBuilder.takeIf { activeTool == ToolType.BRUSH }
                            if (activeTool == ToolType.BRUSH && toolConfig.brushStyle != BrushStyle.MARKER) penSounds?.brush()
                            for (h in 0..motionEvent.historySize) {
                                val point = if (h < motionEvent.historySize) {
                                    val at = viewportState.screenToCanvas(
                                        Offset(motionEvent.getHistoricalX(h), motionEvent.getHistoricalY(h))
                                    )
                                    val pressure = if (isStylus) {
                                        motionEvent.getHistoricalPressure(h).coerceIn(0f, 1f)
                                    } else {
                                        StrokePoint.PRESSURE_DEFAULT
                                    }
                                    InkPoint(at.x, at.y, pressure)
                                } else {
                                    InkPoint(x, y, rawPressure)
                                }
                                if (builder != null) {
                                    currentPoints.addAll(builder.move(point, eventSeconds(motionEvent, h)))
                                } else {
                                    currentPoints.add(point)
                                }
                                lassoPoints.add(Offset(point.x, point.y))
                                if (activeTool == ToolType.ERASER) {
                                    eraseAt(Offset(point.x, point.y), eraserWidth, splitEraser, strokes, onEraseStrokes, onSplitStrokes, overlays, onEraseNatives) {
                                        if (!eraseSnapshotTaken) { onEraseStart(); eraseSnapshotTaken = true }
                                    }
                                }
                            }
                            if (activeTool == ToolType.ERASER) {
                                eraserCursor = Offset(x, y)
                                eraserCursorDown = true
                            }
                            // Where the pen is heading, drawn ahead of the ink and replaced
                            // with every event. At the pen's own pressure, so the tip
                            // doesn't swell or thin on a guess.
                            predictedPoints.clear()
                            // A curved or modeled stroke trails the pen a little; Rnote draws
                            // what it has not made into segments yet, and the pen's own
                            // prediction goes on from there.
                            builder?.let { predictedPoints.addAll(it.prediction) }
                            if (isStylus && activeTool == ToolType.BRUSH && motionEvent.actionMasked == MotionEvent.ACTION_MOVE) {
                                predictor.predict()?.takeIf { it.pointerCount > 0 }?.let { predicted ->
                                    for (h in 0..predicted.historySize) {
                                        val sx = if (h < predicted.historySize) predicted.getHistoricalX(h) else predicted.x
                                        val sy = if (h < predicted.historySize) predicted.getHistoricalY(h) else predicted.y
                                        val at = viewportState.screenToCanvas(Offset(sx, sy))
                                        predictedPoints.add(InkPoint(at.x, at.y, rawPressure))
                                    }
                                }
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasDrawing = isDrawing
                        spaceDrag?.let { space ->
                            spaceDrag = null
                            if (action == MotionEvent.ACTION_UP && abs(space.offset) > VerticalSpace.MIN_OFFSET) {
                                onVerticalSpace(space.offset, space.strokeIds, space.natives)
                            }
                        }
                        if (transformDrag != null) {
                            transformDrag = null
                        } else if (isMovingSelection) {
                            isMovingSelection = false
                        } else if (isDrawing) {
                            if (activeTool == ToolType.SELECTOR) {
                                val path = lassoPoints.toList()
                                val polyline = path.map { it.x to it.y }
                                var foundStrokes: List<Stroke> = emptyList()
                                var foundNatives: List<NativeCanvasElement> = emptyList()
                                when (toolConfig.selectorMode) {
                                    SelectorMode.POLYGON -> if (path.size >= 3) {
                                        foundStrokes = SelectionManager.findStrokesInLasso(path, strokes)
                                        foundNatives = nativeElements.filter { NativeEditing.insideLasso(it, polyline) }
                                    }
                                    SelectorMode.RECTANGLE -> if (path.size >= 2) {
                                        val a = path.first()
                                        val b = path.last()
                                        foundStrokes = SelectionManager.strokesInRect(a, b, strokes)
                                        foundNatives = nativeElements.filter {
                                            NativeEditing.insideRect(it, minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
                                        }
                                    }
                                    SelectorMode.SINGLE -> {
                                        // What is under the pen where it lifts, as in Rnote.
                                        val picked = path.lastOrNull()?.let { point ->
                                            SelectionManager.pickAt(
                                                point, strokes, nativeElements, PICK_TOLERANCE_PX / viewportState.effectiveScale
                                            )
                                        }
                                        when (picked) {
                                            is SelectionManager.Pick.Ink -> foundStrokes = listOf(picked.stroke)
                                            is SelectionManager.Pick.Element -> foundNatives = listOf(picked.element)
                                            null -> Unit
                                        }
                                    }
                                    SelectorMode.INTERSECTING_PATH -> if (path.size >= 3) {
                                        foundStrokes = SelectionManager.strokesCrossedByPath(path, strokes)
                                        foundNatives = nativeElements.filter { NativeEditing.crossedByPath(it, polyline) }
                                    }
                                }
                                selectedStrokes.clear()
                                selectedStrokes.addAll(foundStrokes)
                                selectedNatives?.let { natives ->
                                    natives.clear()
                                    natives.addAll(foundNatives)
                                }
                            } else if (activeTool == ToolType.TYPEWRITER) {
                                if (action == MotionEvent.ACTION_UP && tapDownScreen != null) {
                                    onTypewriterTap(tapDownCanvas.x, tapDownCanvas.y)
                                }
                            } else if (activeTool == ToolType.SHAPER) {
                                val pending = draft
                                if (pending != null) {
                                    if (action == MotionEvent.ACTION_UP) {
                                        val lifted = pending.up()
                                        draft = lifted.draft
                                        lifted.finished?.let { points ->
                                            ShapeDraft.toShape(
                                                pending.kind, points, nativeColorOf(toolConfig.penColor),
                                                toolConfig.shaperWidth, nativeColorOf(toolConfig.fillColor), toolConfig.shapeLine,
                                                toolConfig.shapeRough(roughSeed)
                                            )?.let { onAddShapes(listOf(it)) }
                                        }
                                    } else {
                                        // A cancelled stroke places nothing; the shape waits for the next.
                                        draft = pending.copy(current = null, finishing = false)
                                    }
                                }
                                val start = shapeStart
                                val end = shapeEnd
                                if (start != null && end != null) {
                                    val kind = toolConfig.shapeKind
                                    val color = nativeColorOf(toolConfig.penColor)
                                    val fill = nativeColorOf(toolConfig.fillColor)
                                    val width = toolConfig.shaperWidth
                                    val cell = gridCell
                                    val lines: List<ShapeBuilders.Segment>? = when {
                                        kind == ShapeKind.GRID && cell == null -> {
                                            // The first drag only sets the cell; too small a
                                            // one cancels, as in Rnote.
                                            val w = end.x - start.x
                                            val h = end.y - start.y
                                            if (abs(w) >= ShapeBuilders.GRID_CELL_MIN && abs(h) >= ShapeBuilders.GRID_CELL_MIN) {
                                                gridCell = GridCell(start, Offset(w, h))
                                            }
                                            emptyList()
                                        }
                                        kind == ShapeKind.GRID && cell != null -> {
                                            gridCell = null
                                            ShapeBuilders.grid(cell.start.x, cell.start.y, cell.size.x, cell.size.y, end.x, end.y)
                                        }
                                        ShapeBuilders.isMultiLine(kind) ->
                                            ShapeBuilders.axes(kind, start.x, start.y, end.x, end.y)
                                        else -> null
                                    }
                                    val line = toolConfig.shapeLine
                                    val rough = toolConfig.shapeRough(roughSeed)
                                    val shapes = if (lines != null) {
                                        roughLines(lines, color, width, fill, line, rough)
                                    } else {
                                        listOfNotNull(NativeEditing.createShape(kind, start.x, start.y, end.x, end.y, color, width, fill, line, rough))
                                    }
                                    if (shapes.isNotEmpty()) onAddShapes(shapes)
                                }
                            } else if (activeTool == ToolType.TOOLS && toolConfig.toolsMode == ToolsMode.LASER) {
                                // Kept on screen, never in the note, and fading from now.
                                if (lassoPoints.isNotEmpty()) laserTrails.add(lassoPoints.toList())
                                laserFading = true
                            } else if (activeTool == ToolType.BRUSH && currentPoints.isNotEmpty()) {
                                val isMarker = toolConfig.brushStyle == BrushStyle.MARKER
                                // Rnote hands the lift to its builder as the stroke's last
                                // sample, where it is and as hard as the pen reports; a
                                // cancelled stroke keeps what it has.
                                val builder = pathBuilder
                                if (builder != null && action == MotionEvent.ACTION_UP) {
                                    currentPoints.addAll(
                                        builder.up(InkPoint(x, y, rawPressure), eventSeconds(motionEvent, motionEvent.historySize))
                                    )
                                }

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
                                        pressureCurve = toolConfig.strokePressureCurve,
                                        textured = toolConfig.strokeTextured(texturedSeed)
                                    )
                                )
                            }
                            currentPoints.clear()
                            lassoPoints.clear()
                        }
                        isDrawing = false
                        tapDownScreen = null
                        shapeStart = null
                        shapeEnd = null
                        selectionSnapCorner = null
                        predictedPoints.clear()
                        pathBuilder = null
                        gestureTool = null
                        eraseSnapshotTaken = false
                        eraserCursorDown = false
                        // The pen may still be hovering; the square is repainted by the
                        // next hover event, and hidden if the pen has left entirely.
                        eraserCursor = null
                        // A temporary pen goes once what was drawn with it is done, and a
                        // button let go while writing counts now.
                        if (wasDrawing) onPenGestureEnd()
                        syncShortcutKeys(StylusButtons.keysOf(toolType, buttonState))
                        true
                    }

                    else -> false
                }
            }
    ) {
        // The note as it stands, in a layer of its own. The pen adding a point redraws only
        // the layer above it; this one is redrawn when the note or the view changes. What
        // lies outside the view isn't drawn at all, so a long note costs no more to write
        // in than a short one.
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            // 1. Render Infinite Paper Background
            PaperBackgroundRenderer.drawPaperBackground(
                drawScope = this,
                paperStyle = paperStyle,
                zoomLevel = viewportState.effectiveScale,
                panOffset = viewportState.panOffset
            )

            val viewScale = viewportState.effectiveScale
            val visible = Rect(
                -viewportState.panOffset.x / viewScale,
                -viewportState.panOffset.y / viewScale,
                (size.width - viewportState.panOffset.x) / viewScale,
                (size.height - viewportState.panOffset.y) / viewScale
            )
            val space = spaceDrag
            fun inView(el: NativeCanvasElement, shift: Float) =
                el.maxX >= visible.left && el.minX <= visible.right &&
                    el.maxY + shift >= visible.top && el.minY + shift <= visible.bottom

            // Apply Viewport Transform Matrix (Zoom & Pan)
            withTransform({
                translate(viewportState.panOffset.x, viewportState.panOffset.y)
                scale(viewportState.effectiveScale, viewportState.effectiveScale, Offset.Zero)
            }) {
                // 1b. Imported PDF pages and images (Rnote's document/image layers), beneath all ink.
                if (underlays.isNotEmpty()) {
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        underlays.forEach { el ->
                            val shift = space?.shiftOf(el) ?: 0f
                            if (!inView(el, shift)) return@forEach
                            if (shift != 0f) {
                                nc.save()
                                nc.translate(0f, shift)
                            }
                            val bitmap = imageKey(el)?.let { underlayBitmaps[it] }
                            when (el) {
                                is NativeVectorImageElement -> {
                                    val dst = android.graphics.RectF(
                                        -el.halfExtentX, -el.halfExtentY, el.halfExtentX, el.halfExtentY
                                    )
                                    nc.save()
                                    nc.concat(VectorImageRenderer.matrixFor(el))
                                    if (bitmap != null) nc.drawBitmap(bitmap, null, dst, vectorPaint)
                                    else nc.drawRect(dst, placeholderPaint)
                                    nc.restore()
                                    detailTiles[el]?.let { tile ->
                                        nc.drawBitmap(
                                            tile.bitmap, null,
                                            android.graphics.RectF(tile.left, tile.top, tile.right, tile.bottom),
                                            vectorPaint
                                        )
                                    }
                                }
                                is NativeBitmapElement ->
                                    bitmap?.let { nativeRenderer.drawBitmap(nc, el, it) }
                                else -> Unit
                            }
                            if (shift != 0f) nc.restore()
                        }
                    }
                }

                // 2. Render existing strokes.
                // Filled variable-width outlines, not constant-width stroked paths — see
                // StrokeOutline for why that's the only way the width can match desktop.
                // Outlines are ~4x the work of the old polyline, so they're memoised on the
                // Stroke instance, and only made once a stroke comes into view; strokes are
                // immutable, and every edit path (translate, scale) produces a fresh copy, so
                // identity is a safe key.
                if (outlineCache.size > strokes.size * 2 + 64) outlineCache.clear()
                strokes.forEach { stroke ->
                    val cached = outlineCache.getOrPut(stroke) { CachedOutline(strokeBounds(stroke)) }
                    val shift = if (space != null && space.offset != 0f && stroke.id in space.strokeIds) space.offset else 0f
                    if (!visible.overlaps(cached.bounds.translate(0f, shift))) return@forEach
                    val path = cached.path
                        ?: composeStrokePath(stroke.points, stroke.strokeWidth, stroke.pressureCurve, stroke.textured)
                            .also { cached.path = it }
                    if (shift != 0f) {
                        translate(0f, shift) { drawPath(path = path, color = stroke.color) }
                    } else {
                        drawPath(path = path, color = stroke.color)
                    }
                }

                // 2b. Desktop text boxes and shapes, over the ink.
                if (overlays.isNotEmpty()) {
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        for (el in overlays) {
                            val shift = space?.shiftOf(el) ?: 0f
                            if (!inView(el, shift)) continue
                            if (shift != 0f) {
                                nc.save()
                                nc.translate(0f, shift)
                            }
                            when (el) {
                                is NativeTextElement -> nativeRenderer.drawText(nc, el)
                                is NativeShapeElement -> nativeRenderer.drawShape(nc, el)
                                else -> Unit
                            }
                            if (shift != 0f) nc.restore()
                        }
                    }
                }
            }

            // 7. Veil the canvas outside the document, over the strokes so ink out there is
            // dimmed too — see PaperBackgroundRenderer.drawOutOfBoundsScrim.
            PaperBackgroundRenderer.drawOutOfBoundsScrim(
                drawScope = this,
                paperStyle = paperStyle,
                zoomLevel = viewportState.effectiveScale,
                panOffset = viewportState.panOffset
            )
        }

        // What the pen is doing, over the note: the stroke being written, the shape being
        // dragged out, the lasso, the laser, the selection, the eraser and the hover cursor.
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            val space = spaceDrag
            withTransform({
                translate(viewportState.panOffset.x, viewportState.panOffset.y)
                scale(viewportState.effectiveScale, viewportState.effectiveScale, Offset.Zero)
            }) {
                // 3. Render active stroke preview
                // Must agree with the input handler's latch, or a gesture that started with
                // the side button held would paint an ink preview while it erased.
                val activeTool =
                    gestureTool ?: toolConfig.activeTool
                if (isDrawing && currentPoints.isNotEmpty() && activeTool == ToolType.BRUSH) {
                    // Built the same way as a committed stroke, so what's under the nib is
                    // what gets saved — the old preview used only the latest pressure and so
                    // showed one uniform width for a stroke that would be drawn tapered. The
                    // predicted points run on ahead of the pen; they are never saved.
                    val path = composeStrokePath(
                        if (predictedPoints.isEmpty()) currentPoints else currentPoints + predictedPoints,
                        toolConfig.currentActiveSize,
                        toolConfig.strokePressureCurve,
                        toolConfig.strokeTextured(texturedSeed)
                    )
                    drawPath(path = path, color = toolConfig.currentActiveColor)
                }

                // 3b. Shape being dragged out with the Shaper.
                val previewStart = shapeStart
                val previewEnd = shapeEnd
                val dragging = isDrawing && previewStart != null && previewEnd != null
                if (activeTool == ToolType.SHAPER) {
                    val kind = toolConfig.shapeKind
                    val cell = gridCell
                    // Shapes made of lines are previewed as plain lines: a grid can be thousands
                    // of them, far too many to build as shapes on every frame. A few rough ones
                    // are built, since plain lines would not show their wobble.
                    val lines: List<ShapeBuilders.Segment>? = when {
                        kind == ShapeKind.GRID && cell != null ->
                            if (dragging) {
                                ShapeBuilders.grid(cell.start.x, cell.start.y, cell.size.x, cell.size.y, previewEnd!!.x, previewEnd.y)
                            } else {
                                ShapeBuilders.cellOutline(
                                    cell.start.x, cell.start.y, cell.start.x + cell.size.x, cell.start.y + cell.size.y
                                )
                            }
                        kind == ShapeKind.GRID ->
                            if (dragging) ShapeBuilders.cellOutline(previewStart!!.x, previewStart.y, previewEnd!!.x, previewEnd.y)
                            else emptyList()
                        ShapeBuilders.isMultiLine(kind) ->
                            if (dragging) ShapeBuilders.axes(kind, previewStart!!.x, previewStart.y, previewEnd!!.x, previewEnd.y)
                            else emptyList()
                        else -> null
                    }
                    val rough = toolConfig.shapeRough(roughSeed)
                    if (lines != null && rough != null && lines.size <= ROUGH_PREVIEW_LINES_MAX) {
                        // Rough lines wobble as the placed ones will, which a few of can afford.
                        val shapes = roughLines(
                            lines, nativeColorOf(toolConfig.penColor), toolConfig.shaperWidth,
                            nativeColorOf(toolConfig.fillColor), toolConfig.shapeLine, rough
                        )
                        drawIntoCanvas { canvas ->
                            for (shape in shapes) nativeRenderer.drawShape(canvas.nativeCanvas, shape, cache = false)
                        }
                    } else if (lines != null) {
                        // Dashed and capped as the lines will be, which costs nothing per line.
                        val rounded = toolConfig.shapeLine.cap == ShapeLineCap.ROUNDED
                        val dashes = NativeElementRenderer.dashPattern(
                            toolConfig.shapeLine.style.apiName, toolConfig.shaperWidth, rounded
                        )?.let { PathEffect.dashPathEffect(it, 0f) }
                        for (line in lines) {
                            drawLine(
                                color = toolConfig.penColor,
                                start = Offset(line.x1, line.y1),
                                end = Offset(line.x2, line.y2),
                                strokeWidth = toolConfig.shaperWidth,
                                cap = if (rounded) StrokeCap.Round else StrokeCap.Butt,
                                pathEffect = dashes
                            )
                        }
                    } else if (dragging) {
                        NativeEditing.createShape(
                            kind, previewStart!!.x, previewStart.y, previewEnd!!.x, previewEnd.y,
                            nativeColorOf(toolConfig.penColor), toolConfig.shaperWidth, nativeColorOf(toolConfig.fillColor),
                            toolConfig.shapeLine, rough
                        )?.let { preview ->
                            drawIntoCanvas { canvas -> nativeRenderer.drawShape(canvas.nativeCanvas, preview, cache = false) }
                        }
                    }

                    // A shape drawn in several strokes, as far as it has got.
                    draft?.let { pending ->
                        val hover = hoverOffset?.let { viewportState.screenToCanvas(it) }
                        drawDraft(
                            pending, hover, toolConfig, rough, viewportState.effectiveScale,
                            maxOf(ShapeDraft.FINISH_DISTANCE, FINISH_TOLERANCE_PX / viewportState.effectiveScale)
                        ) { shape ->
                            drawIntoCanvas { canvas -> nativeRenderer.drawShape(canvas.nativeCanvas, shape, cache = false) }
                        }
                    }
                }

                // 4. Render the selector's path: the lasso or the line drawn through things, or
                //    for the rectangle mode the box from where the pen went down to where it is.
                //    A tap in single mode draws nothing.
                if (isDrawing && activeTool == ToolType.SELECTOR && lassoPoints.size >= 2 &&
                    toolConfig.selectorMode != SelectorMode.SINGLE
                ) {
                    val lassoPath = Path()
                    if (toolConfig.selectorMode == SelectorMode.RECTANGLE) {
                        val a = lassoPoints.first()
                        val b = lassoPoints.last()
                        lassoPath.addRect(Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)))
                    } else {
                        lassoPath.moveTo(lassoPoints[0].x, lassoPoints[0].y)
                        for (i in 1 until lassoPoints.size) {
                            lassoPath.lineTo(lassoPoints[i].x, lassoPoints[i].y)
                        }
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

                // 4b. The Laser, as Rnote draws it: a red line with a light core, the same width
                //     on screen at any zoom. The trail being drawn is the pen's path so far.
                val liveTrail = if (isDrawing && activeTool == ToolType.TOOLS && toolConfig.toolsMode == ToolsMode.LASER) {
                    lassoPoints.toList()
                } else {
                    emptyList()
                }
                if (laserTrails.isNotEmpty() || liveTrail.isNotEmpty()) {
                    val zoom = viewportState.zoomScale
                    for (trail in laserTrails + listOf(liveTrail)) {
                        if (trail.isEmpty()) continue
                        val path = Path().apply {
                            moveTo(trail[0].x, trail[0].y)
                            // A tap is a dot: a line of no length, drawn with round ends.
                            if (trail.size == 1) lineTo(trail[0].x, trail[0].y)
                            for (i in 1 until trail.size) lineTo(trail[i].x, trail[i].y)
                        }
                        for ((color, width) in LASER_LINES) {
                            drawPath(
                                path = path,
                                color = color.copy(alpha = laserOpacity),
                                style = CanvasStrokeStyle(width = width / zoom, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }

                // 5. Render selection bounding box
                val bbox = selectionBounds(selectedStrokes, selectedNatives)
                bbox?.let { box ->
                    val scale = viewportState.effectiveScale
                    val inflated = box.inflate(HANDLE_INFLATE_PX / scale)
                    drawRoundRect(
                        color = SELECTION_COLOR,
                        topLeft = inflated.topLeft,
                        size = inflated.size,
                        cornerRadius = CornerRadius(8f, 8f),
                        style = CanvasStrokeStyle(
                            width = 2.5f / scale,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    )
                    // Handles, a fixed size on screen: the corners scale, the knob above rotates.
                    if (toolConfig.activeTool == ToolType.SELECTOR) {
                        val handles = handlePositions(box, scale)
                        val half = HANDLE_SIZE_PX / 2f / scale
                        val outline = CanvasStrokeStyle(width = 2f / scale)
                        val knob = handles[ROTATE_HANDLE]
                        drawLine(
                            color = SELECTION_COLOR,
                            start = Offset(knob.x, inflated.top),
                            end = knob,
                            strokeWidth = 2f / scale
                        )
                        for (i in 0 until 4) {
                            val topLeft = handles[i] - Offset(half, half)
                            val size = Size(half * 2f, half * 2f)
                            drawRect(color = Color.White, topLeft = topLeft, size = size)
                            drawRect(color = SELECTION_COLOR, topLeft = topLeft, size = size, style = outline)
                        }
                        drawCircle(color = Color.White, radius = half * 1.2f, center = knob)
                        drawCircle(color = SELECTION_COLOR, radius = half * 1.2f, center = knob, style = outline)
                    }
                }

                // 5b. The vertical space being made, as Rnote's VerticalSpaceTool::draw_on_doc
                // draws it: the room as a faint band across the view, a dashed green line where
                // the drag started, a blue one where everything below now begins.
                space?.let { drag ->
                    val zoom = viewportState.zoomScale
                    val left = viewportState.screenToCanvas(Offset.Zero).x
                    val right = viewportState.screenToCanvas(Offset(viewSize.width.toFloat(), 0f)).x
                    val end = drag.startY + drag.offset
                    drawRect(
                        color = SPACE_FILL,
                        topLeft = Offset(left, minOf(drag.startY, end)),
                        size = Size(right - left, abs(drag.offset))
                    )
                    drawLine(
                        color = SPACE_THRESHOLD_LINE,
                        start = Offset(left, drag.startY),
                        end = Offset(right, drag.startY),
                        strokeWidth = 3f / zoom,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f / zoom, 6f / zoom), 0f)
                    )
                    drawLine(
                        color = SPACE_OFFSET_LINE,
                        start = Offset(left, end),
                        end = Offset(right, end),
                        strokeWidth = 1.5f / zoom
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

            // 8. Render the brush hover cursor (screen space). The eraser has its own
            // indicator above, drawn in canvas space because its size is a document size.
            hoverOffset?.let { hoverPos ->
                if (toolConfig.activeTool == ToolType.TOOLS) {
                    // Where the line would go: everything reaching below it moves.
                    drawLine(
                        color = SPACE_THRESHOLD_LINE.copy(alpha = 0.5f),
                        start = Offset(0f, hoverPos.y),
                        end = Offset(size.width, hoverPos.y),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                    )
                } else if (toolConfig.activeTool == ToolType.TYPEWRITER) {
                    // A text cursor as tall as a line of the chosen size: tapping here puts the
                    // top-left of the new text box at the pen tip.
                    val height = toolConfig.textSize * 1.2f * viewportState.effectiveScale
                    drawLine(
                        color = toolConfig.penColor,
                        start = hoverPos,
                        end = hoverPos + Offset(0f, height),
                        strokeWidth = 2f
                    )
                } else {
                    drawCircle(
                        color = toolConfig.currentActiveColor,
                        radius = (toolConfig.currentActiveSize * viewportState.effectiveScale) / 2f,
                        center = hoverPos
                    )
                }
            }
        }
    }
}

/** Rnote's two-finger long-press: GTK's long-press time, 500 ms, times the 0.8 Rnote asks for. */
private const val TWO_FINGER_LONG_PRESS_MS = 400L

/** The canvas's side of Rnote's button shortcuts; see [DrawingCanvas]'s `onShortcutKey`. */
private class ShortcutInput {
    /** The buttons held as of the last pen or mouse event looked at. */
    var held: Set<ShortcutKey> = emptySet()
    /** The pen the app answered with, until the canvas is recomposed with it. */
    var pendingTool: ToolType? = null
    /** A two-finger long-press being waited out, and where its fingers went down. */
    var longPress: Job? = null
    var longPressStart: List<Offset>? = null

    fun cancelLongPress() {
        longPress?.cancel()
        longPress = null
        longPressStart = null
    }
}

/**
 * One UI's proprietary MotionEvent actions for a stylus gesture with the barrel button
 * held, which it substitutes for ACTION_DOWN / ACTION_UP / ACTION_MOVE. Not in the SDK;
 * measured on an SM-T870 by logging every event the canvas receives.
 */
private const val SAMSUNG_ACTION_PEN_DOWN = 211
private const val SAMSUNG_ACTION_PEN_UP = 212
private const val SAMSUNG_ACTION_PEN_MOVE = 213

/** A grid's first cell: its corner where the pen went down, and its size and direction. */
private class GridCell(val start: Offset, val size: Offset)

/** A stroke's bounds, and its outline once it has come into view. */
private class CachedOutline(val bounds: Rect) {
    var path: Path? = null
}

/** A stroke's extent with room for its width: enough to tell whether it is in view. */
private fun strokeBounds(stroke: Stroke): Rect {
    val points = stroke.points
    if (points.isEmpty()) return Rect.Zero
    var left = points[0].x
    var top = points[0].y
    var right = left
    var bottom = top
    for (p in points) {
        if (p.x < left) left = p.x
        if (p.x > right) right = p.x
        if (p.y < top) top = p.y
        if (p.y > bottom) bottom = p.y
    }
    val margin = stroke.strokeWidth
    return Rect(left - margin, top - margin, right + margin, bottom + margin)
}

/**
 * A shape being drawn in several strokes, as far as it has got, as Rnote's builders show
 * theirs: the shape itself once there is enough of it, dashed guides along a curve's
 * control points and between the foci, and a ring on every point placed. A polyline's or
 * polygon's last corner gets a green ring where putting the pen down would finish it.
 */
private fun DrawScope.drawDraft(
    draft: ShapeDraft,
    hover: Offset?,
    toolConfig: ToolConfig,
    rough: RoughStyle?,
    scale: Float,
    finishDistance: Float,
    drawShape: (NativeShapeElement) -> Unit
) {
    val color = nativeColorOf(toolConfig.penColor)
    val fill = nativeColorOf(toolConfig.fillColor)
    val width = toolConfig.shaperWidth
    val line = toolConfig.shapeLine
    val kind = draft.kind
    val isPoly = kind == ShapeKind.POLYLINE || kind == ShapeKind.POLYGON
    val current = draft.current
    val placed = if (current != null && !draft.finishing) draft.points + current else draft.points
    val preview = when {
        // Two corners of a polygon are only a line so far.
        kind == ShapeKind.POLYGON && placed.size == 2 -> ShapeDraft.toShape(ShapeKind.POLYLINE, placed, color, width, fill, line, rough)
        isPoly -> ShapeDraft.toShape(kind, placed, color, width, fill, line, rough)
        placed.size == 3 && (kind == ShapeKind.QUADBEZ || kind == ShapeKind.FOCI_ELLIPSE) ->
            ShapeDraft.toShape(kind, placed, color, width, fill, line, rough)
        placed.size == 4 && kind == ShapeKind.CUBBEZ -> ShapeDraft.toShape(kind, placed, color, width, fill, line, rough)
        else -> null
    }
    preview?.let(drawShape)

    val guideWidth = 1.5f / scale
    if (!isPoly) {
        val dashes = PathEffect.dashPathEffect(floatArrayOf(6f / scale, 4f / scale), 0f)
        for (i in 1 until placed.size) {
            drawLine(color = DRAFT_GUIDE, start = placed[i - 1], end = placed[i], strokeWidth = guideWidth, pathEffect = dashes)
        }
    }
    val radius = DRAFT_POINT_RADIUS_PX / scale
    for (p in placed) {
        drawCircle(color = Color.White, radius = radius, center = p)
        drawCircle(color = DRAFT_GUIDE, radius = radius, center = p, style = CanvasStrokeStyle(width = guideWidth))
    }
    if (isPoly) {
        val last = draft.points.last()
        val finishes = draft.finishing ||
            (current == null && hover != null && (hover - last).getDistance() < finishDistance)
        if (finishes) {
            drawCircle(color = DRAFT_FINISH, radius = radius * 1.8f, center = last, style = CanvasStrokeStyle(width = guideWidth * 2f))
        }
    }
}

/**
 * The lines of a shape built from lines, each a shape of its own; in the rough style each
 * wobbling its own way, the seed moved on after every one, as Rnote's shaper moves it.
 */
private fun roughLines(
    lines: List<ShapeBuilders.Segment>,
    color: RnoteNativeColor,
    width: Float,
    fill: RnoteNativeColor,
    line: ShapeLine,
    rough: RoughStyle?
): List<NativeShapeElement> {
    var style = rough
    return lines.mapNotNull {
        NativeEditing.createShape(ShapeKind.LINE, it.x1, it.y1, it.x2, it.y2, color, width, fill, line, style)
            ?.also { style = style?.advanced() }
    }
}

/**
 * When sample [h] of [event] was taken — its history first, then the event itself — in
 * seconds on the event clock; to the nanosecond where Android reports it so.
 */
private fun eventSeconds(event: MotionEvent, h: Int): Double =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        (if (h < event.historySize) event.getHistoricalEventTimeNanos(h) else event.eventTimeNanos) / 1e9
    } else {
        (if (h < event.historySize) event.getHistoricalEventTime(h) else event.eventTime) / 1e3
    }

/** Past this many lines, a shape built from lines is previewed as plain lines even when rough. */
private const val ROUGH_PREVIEW_LINES_MAX = 32

/** The rings and guides of a shape being drawn in several strokes: Rnote's indicator blue. */
private val DRAFT_GUIDE = Color(0xFF3584E4)
/** Rnote's finish indicator: GNOME green. */
private val DRAFT_FINISH = Color(0xFF33D17A)
/** The rings on a draft's points, in screen px. */
private const val DRAFT_POINT_RADIUS_PX = 5f

/**
 * Where the Shaper's drag ends for the pen at [pos]: bent by the constraints — switched
 * on or off while [ctrl] is held — as each of Rnote's builders bends it, lines and arrows
 * always allowing level and upright; then, for a line or an arrow, turned to the nearest
 * 15° when that is switched on.
 */
private fun shapeEndFor(toolConfig: ToolConfig, start: Offset, pos: Offset, ctrl: Boolean): Offset {
    val kind = toolConfig.shapeKind
    val constraints = toolConfig.shapeConstraints.withCtrl(ctrl)
    val isLine = kind == ShapeKind.LINE || kind == ShapeKind.ARROW
    val end = start + (if (isLine) constraints.withAxes() else constraints).constrain(pos - start)
    if (!toolConfig.snapAngles || !isLine) return end
    val (x, y) = ShapeBuilders.snapAngle(start.x, start.y, end.x, end.y)
    return Offset(x, y)
}

/** A vertical-space drag: what moves, fixed when the pen went down, and how far it has. */
private class SpaceDrag(
    val startY: Float,
    val strokeIds: Set<String>,
    /** Identity set, as [VerticalSpace.nativesBelow] makes it. */
    val natives: Set<NativeCanvasElement>
) {
    var offset by mutableFloatStateOf(0f)

    fun shiftOf(el: NativeCanvasElement): Float = if (el in natives) offset else 0f
}

// Rnote's VerticalSpaceTool colours: GNOME_BRIGHTS[2] at a=23, GNOME_GREENS[4] at a=240,
// GNOME_BLUES[3].
private val SPACE_FILL = Color(0x17DEDDDA)
private val SPACE_THRESHOLD_LINE = Color(0xF026A269)
private val SPACE_OFFSET_LINE = Color(0xFF1C71D8)

/**
 * Rnote's `Eraser` colours (GNOME palette reds, see Eraser::draw_on_doc).
 */
private val ERASER_OUTLINE = Color(0xE0E01B24)
private val ERASER_FILL = Color(0xA0F66151)
private val ERASER_PROXIMITY_FILL = Color(0x33F66151)

/**
 * Rnote's two eraser styles. `TrashCollidingStrokes` (the default) removes every stroke
 * the eraser square touches; `SplitCollidingStrokes` ([split]) cuts out only the part of
 * each ink stroke under it. Shapes are removed whole either way, and text and images are
 * left alone, as in Rnote. [onFirstHit] fires before the first change of a gesture, so
 * the whole drag collapses into one undo step rather than one per frame.
 */
private fun eraseAt(
    center: Offset,
    eraserWidth: Float,
    split: Boolean,
    strokes: List<Stroke>,
    onEraseStrokes: (List<Stroke>) -> Unit,
    onSplitStrokes: (Map<String, List<Stroke>>) -> Unit,
    overlays: List<NativeCanvasElement>,
    onEraseNatives: (List<NativeCanvasElement>) -> Unit,
    onFirstHit: () -> Unit
) {
    val bounds = EraserHitTest.eraserBounds(center, eraserWidth)
    val hit = if (split) emptyList() else EraserHitTest.collidingStrokes(bounds, strokes)
    val pieces = LinkedHashMap<String, List<Stroke>>()
    if (split) {
        for (stroke in strokes) EraserHitTest.splitStroke(bounds, stroke)?.let { pieces[stroke.id] = it }
    }
    val hitShapes = overlays.filter {
        it is NativeShapeElement &&
            NativeEditing.eraserHits(it, bounds.left, bounds.top, bounds.right, bounds.bottom)
    }
    if (hit.isNotEmpty() || pieces.isNotEmpty() || hitShapes.isNotEmpty()) onFirstHit()
    if (hit.isNotEmpty()) onEraseStrokes(hit)
    if (pieces.isNotEmpty()) onSplitStrokes(pieces)
    if (hitShapes.isNotEmpty()) onEraseNatives(hitShapes)
}

/**
 * A drag on one of the selection's handles. The transform is worked out afresh from
 * where the drag began and applied to the selection as it was then, every frame —
 * adding up small per-frame steps instead would drift and put the rounding into the file.
 */
private class TransformDrag(
    val rotate: Boolean,
    /** Scaling: the corner opposite the one dragged. Rotating: the selection's centre. */
    val pivot: Offset,
    /** Scaling: the selection corner being dragged. */
    val corner: Offset,
    /** Where the drag began, in canvas units. */
    val start: Offset,
    val strokes: List<Stroke>,
    val natives: List<NativeCanvasElement>,
    /** The desktop elements as the document holds them now, in the order of [natives]. */
    var current: List<NativeCanvasElement>
)

private val SELECTION_COLOR = Color(0xFF82AAFF)

/** The dashed box sits this far (screen px) outside the selection; the handles on its corners. */
private const val HANDLE_INFLATE_PX = 12f
private const val HANDLE_SIZE_PX = 14f
/** How close (screen px) the pen has to come to a handle to take it. */
private const val HANDLE_HIT_PX = 32f
/**
 * How far (screen px) beside a line a tap in single selection still takes it. Rnote
 * needs the pointer on the ink itself; a pen on glass is less exact than a mouse.
 */
private const val PICK_TOLERANCE_PX = 12f

/** Rnote's `LaserTool::FULL_FADE_DURATION`. */
private const val LASER_FADE_MS = 1000L

/**
 * Rnote's laser: GNOME red 6 px wide with a GNOME light 1 px core, in screen px at 96 dpi
 * — canvas units at 100 % — so the same size at any zoom.
 */
private val LASER_LINES = listOf(Color(0xFFED333B) to 6f, Color(0xFFF6F5F4) to 1f)
/** How far (screen px) the rotate knob stands above the box. */
private const val ROTATE_HANDLE_OFFSET_PX = 36f
/** Index of the rotate knob in [handlePositions]; 0–3 are the corners, clockwise from top-left. */
private const val ROTATE_HANDLE = 4
/** Rnote won't scale a selection flat or inside out; nor will this. */
private const val MIN_SCALE = 0.02f
private const val MAX_SCALE = 50f

/** Where the handles of a selection with bounds [box] are drawn, in canvas units. */
private fun handlePositions(box: Rect, scale: Float): List<Offset> {
    val r = box.inflate(HANDLE_INFLATE_PX / scale)
    return listOf(
        Offset(r.left, r.top), Offset(r.right, r.top), Offset(r.right, r.bottom), Offset(r.left, r.bottom),
        Offset((r.left + r.right) / 2f, r.top - ROTATE_HANDLE_OFFSET_PX / scale)
    )
}

/**
 * The handle nearest the screen point [screen], if one is within reach. A touch inside the
 * selection itself is never a handle: it moves the selection, however small that is.
 */
private fun handleAt(box: Rect, viewport: ViewportState, screen: Offset): Int? {
    val topLeft = viewport.canvasToScreen(Offset(box.left, box.top))
    val bottomRight = viewport.canvasToScreen(Offset(box.right, box.bottom))
    if (screen.x in topLeft.x..bottomRight.x && screen.y in topLeft.y..bottomRight.y) return null
    var best: Int? = null
    var bestDistance = HANDLE_HIT_PX
    handlePositions(box, viewport.effectiveScale).forEachIndexed { i, p ->
        val d = (viewport.canvasToScreen(p) - screen).getDistance()
        if (d <= bestDistance) { best = i; bestDistance = d }
    }
    return best
}

/**
 * The transform a handle drag at [pos] stands for. Scaling moves the dragged corner with
 * the pen and keeps the opposite one where it is — uniformly with [lockAspect], as Rnote's
 * "Lock Aspect Ratio" does. Rotating turns about the centre by the angle swept since the
 * drag began.
 */
private fun dragTransform(
    drag: TransformDrag,
    pos: Offset,
    lockAspect: Boolean,
    /** Snap Positions, when on: the dragged corner goes to the pattern unless the ratio is locked. */
    snap: ((Offset) -> Offset)? = null
): FloatArray {
    val p = drag.pivot
    if (drag.rotate) {
        val from = atan2(drag.start.y - p.y, drag.start.x - p.x)
        val to = atan2(pos.y - p.y, pos.x - p.x)
        return Affine.rotateAbout(p.x, p.y, to - from)
    }
    val free = drag.corner + (pos - drag.start)
    val moved = if (snap != null && !lockAspect) snap(free) else free
    val w0 = drag.corner.x - p.x
    val h0 = drag.corner.y - p.y
    var sx: Float
    var sy: Float
    if (lockAspect) {
        val len2 = w0 * w0 + h0 * h0
        val s = if (len2 > 0.25f) ((moved.x - p.x) * w0 + (moved.y - p.y) * h0) / len2 else 1f
        sx = s; sy = s
    } else {
        // A selection with no width (a vertical line) can't be stretched sideways, and so on.
        sx = if (abs(w0) > 0.5f) (moved.x - p.x) / w0 else 1f
        sy = if (abs(h0) > 0.5f) (moved.y - p.y) / h0 else 1f
    }
    sx = sx.coerceIn(MIN_SCALE, MAX_SCALE)
    sy = sy.coerceIn(MIN_SCALE, MAX_SCALE)
    return Affine.scaleAbout(p.x, p.y, sx, sy)
}

/** How far (screen px) a Typewriter tap may drift and still count as a tap. */
private const val TAP_SLOP_PX = 24f

/**
 * How close (screen px) the pen has to come down to a polyline's or polygon's last corner
 * to finish it, where Rnote's 8 document units would be a pinpoint zoomed out.
 */
private const val FINISH_TOLERANCE_PX = 16f

/** The pen's events that motion prediction learns from; hovering and One UI's own codes are not. */
private val PREDICTED_ACTIONS = setOf(
    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL
)

/** What a page or image is rendered from; the key its bitmap is kept under. */
private fun imageKey(el: NativeCanvasElement): Any? = when (el) {
    is NativeVectorImageElement -> el.svgData
    is NativeBitmapElement -> el.rgbaBase64 ?: el.pixels
    else -> null
}

private fun nativeColorOf(c: Color) = RnoteNativeColor(c.red, c.green, c.blue, c.alpha)

/** The box around everything selected, ink and desktop elements alike; null if nothing is. */
private fun selectionBounds(strokes: List<Stroke>, natives: List<NativeCanvasElement>?): Rect? {
    var box = SelectionManager.calculateBoundingBox(strokes)
    natives?.forEach { el ->
        val r = Rect(el.minX, el.minY, el.maxX, el.maxY)
        box = box?.let { Rect(minOf(it.left, r.left), minOf(it.top, r.top), maxOf(it.right, r.right), maxOf(it.bottom, r.bottom)) } ?: r
    }
    return box
}

