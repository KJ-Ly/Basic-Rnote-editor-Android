package io.github.kjly.brna.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.kjly.brna.model.NativeTextElement
import io.github.kjly.brna.model.RnoteNativeColor
import io.github.kjly.brna.model.TextRun
import io.github.kjly.brna.model.TextToggle
import io.github.kjly.brna.model.ViewportState
import io.github.kjly.brna.render.NativeElementRenderer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How a text box looks, in document units: from its element, or the Typewriter's settings for a new one. */
data class TextBoxStyle(
    val family: String,
    val size: Float,
    val weight: Int,
    val italic: Boolean,
    val color: RnoteNativeColor,
    val alignment: String,
    val maxWidth: Float?,
    /** How much the box's transform scales it: the selector scales text through its transform, not its font size. */
    val scale: Float = 1f
) {
    companion object {
        fun of(el: NativeTextElement): TextBoxStyle {
            val t = el.transform
            return TextBoxStyle(
                el.fontFamily, el.fontSize, el.fontWeight, el.italic, el.color, el.alignment, el.maxWidth,
                scale = sqrt(abs(t[0] * t[3] - t[1] * t[2])).takeIf { it > 0f } ?: 1f
            )
        }
    }
}

/**
 * The Typewriter's text field, laid over the canvas where the text box is, so text is
 * typed where it will stand — Rnote's typewriter, which edits in place, rather than a
 * dialog. It is an ordinary Android text field underneath: cursor, selection handles,
 * copy and paste, and a hardware keyboard all work as anywhere else.
 *
 * The box is shown upright while it is edited, even one turned on the desktop. The
 * canvas leaves the box out while this is showing (see MainActivity), so it isn't
 * drawn twice.
 */
@Composable
fun InlineTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    style: TextBoxStyle,
    /** The box's formatting, for [value]'s text; see TextFormatting.runs. */
    runs: List<TextRun>,
    /** The box's top-left corner, in document units. */
    topLeft: Offset,
    viewportState: ViewportState,
    /** How far down the canvas is visible above the keyboard, in px. */
    visibleBottom: Float,
    /** Moves the view up (negative) by this many px, to keep the cursor above the keyboard. */
    onPan: (Float) -> Unit,
    onToggle: (TextToggle) -> Unit,
    onDone: () -> Unit,
    /** A key on a keyboard that only moves the cursor: Rnote's typewriter thumps for it. */
    onCursorKey: () -> Unit = {}
) {
    val density = LocalDensity.current
    val pxPerUnit = viewportState.effectiveScale * style.scale
    val screen = viewportState.canvasToScreen(topLeft)
    val color = Color(NativeElementRenderer.argb(style.color))
    val family = composeFamily(style.family)
    val textStyle = TextStyle(
        color = color,
        fontSize = with(density) { (style.size * pxPerUnit).toSp() },
        fontFamily = family,
        fontWeight = FontWeight(style.weight.coerceIn(1, 1000)),
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = when (style.alignment) {
            "center" -> TextAlign.Center
            "end" -> TextAlign.End
            "fill" -> TextAlign.Justify
            else -> TextAlign.Start
        }
    )
    val transformation = remember(runs, style, pxPerUnit, density) { FormattingTransformation(runs, style, pxPerUnit, density) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // Keep the line being typed above the keyboard, as a text field in a scrolling page is.
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val caretBottom = layout?.let { l ->
        screen.y + l.getCursorRect(value.selection.end.coerceIn(0, l.layoutInput.text.length)).bottom
    }
    val margin = with(density) { 24.dp.toPx() }
    LaunchedEffect(caretBottom, visibleBottom) {
        val bottom = caretBottom ?: return@LaunchedEffect
        if (visibleBottom > margin && bottom > visibleBottom - margin) onPan(visibleBottom - margin - bottom)
    }

    val outline = Color(0x993584E4)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        cursorBrush = SolidColor(color.copy(alpha = 1f)),
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        onTextLayout = { layout = it },
        modifier = Modifier
            .offset { IntOffset(screen.x.roundToInt(), screen.y.roundToInt()) }
            .then(
                if (style.maxWidth != null && style.maxWidth > 0f) {
                    Modifier.width(with(density) { (style.maxWidth * pxPerUnit).toDp() })
                } else {
                    // No wrap width: as wide as the longest line, even past the screen's edge.
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true).widthIn(min = 2.dp)
                }
            )
            // Rnote outlines the text box being typed into.
            .drawBehind {
                drawRect(
                    color = outline,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                )
            }
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Escape -> { onDone(); true }
                    // Rnote's shortcuts: win.text-bold / -italic / -underline.
                    event.isCtrlPressed && event.key == Key.B -> { onToggle(TextToggle.BOLD); true }
                    event.isCtrlPressed && event.key == Key.I -> { onToggle(TextToggle.ITALIC); true }
                    event.isCtrlPressed && event.key == Key.U -> { onToggle(TextToggle.UNDERLINE); true }
                    event.key in CURSOR_KEYS -> { onCursorKey(); false }
                    else -> false
                }
            }
    )
}

/** The keys that move the cursor without typing, which Rnote's typewriter answers with a thump. */
private val CURSOR_KEYS = setOf(
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
    Key.MoveHome, Key.MoveEnd, Key.PageUp, Key.PageDown
)

/**
 * Compose's own family for a name Rnote uses. The generic ones resolve weight and slant
 * the way NativeElementRenderer.typeface does; for a family the tablet doesn't have — a
 * desktop font like Cantarell — Android falls back to its sans-serif, and so does this.
 */
private fun composeFamily(name: String): FontFamily = when (name.lowercase()) {
    "serif" -> FontFamily.Serif
    "monospace", "mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

/**
 * Shows the box's formatting in the text field: the field holds plain text, and this
 * lays each run's style over it, char for char, so no offsets move.
 */
private class FormattingTransformation(
    private val runs: List<TextRun>,
    private val style: TextBoxStyle,
    private val pxPerUnit: Float,
    private val density: Density
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        // Runs made for another text — a keystroke not yet through — are left off for a frame.
        if (runs.isEmpty() || runs.last().end != text.length) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val styled = AnnotatedString.Builder(text)
        for (run in runs) {
            styled.addStyle(spanStyle(run), run.start, run.end)
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun spanStyle(run: TextRun): SpanStyle {
        val decorations = listOfNotNull(
            TextDecoration.Underline.takeIf { run.underline },
            TextDecoration.LineThrough.takeIf { run.strikethrough }
        )
        return SpanStyle(
            color = Color(NativeElementRenderer.argb(run.color)),
            fontSize = with(density) { (run.size * pxPerUnit).toSp() },
            fontWeight = FontWeight(run.weight.coerceIn(1, 1000)),
            fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (run.family == style.family) null else composeFamily(run.family),
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations)
        )
    }

    // Compose compares transformations to decide whether to re-run them.
    override fun equals(other: Any?) = other is FormattingTransformation &&
        other.runs == runs && other.style == style && other.pxPerUnit == pxPerUnit && other.density == density

    override fun hashCode() = runs.hashCode() * 31 + style.hashCode()
}
