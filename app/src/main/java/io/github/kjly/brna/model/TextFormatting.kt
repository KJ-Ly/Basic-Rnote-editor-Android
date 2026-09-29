package io.github.kjly.brna.model

/**
 * Rnote's `TextAttribute` (rnote-engine/src/strokes/textstroke.rs): one kind of formatting
 * a stretch of a text box can carry, over the style the whole box has.
 */
sealed interface TextAttr {
    data class Family(val family: String) : TextAttr
    data class Size(val size: Float) : TextAttr
    data class Weight(val weight: Int) : TextAttr
    data class Color(val color: RnoteNativeColor) : TextAttr
    /** Rnote's `font_style`: "italic" or "regular". */
    data class Italic(val italic: Boolean) : TextAttr
    data class Underline(val underline: Boolean) : TextAttr
    data class Strikethrough(val strikethrough: Boolean) : TextAttr
}

/**
 * Rnote's `RangedTextAttribute`: [attribute] over [start, end) of the text, counted in
 * UTF-8 bytes as Rust indexes strings — not in the UTF-16 chars Android counts in.
 */
data class RangedTextAttr(val start: Int, val end: Int, val attribute: TextAttr)

/** The four switches on Rnote's typewriter page, and Ctrl+B / Ctrl+I / Ctrl+U. */
enum class TextToggle(
    /** The attribute's key in Rnote's JSON. */
    val key: String
) {
    BOLD("font_weight"),
    ITALIC("font_style"),
    UNDERLINE("underline"),
    STRIKETHROUGH("strikethrough")
}

/** A stretch of text, [start, end) in chars, with the style it is drawn in. */
data class TextRun(
    val start: Int,
    val end: Int,
    val family: String,
    val size: Float,
    val weight: Int,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val color: RnoteNativeColor
) {
    fun has(toggle: TextToggle): Boolean = when (toggle) {
        TextToggle.BOLD -> weight >= BOLD_THRESHOLD
        TextToggle.ITALIC -> italic
        TextToggle.UNDERLINE -> underline
        TextToggle.STRIKETHROUGH -> strikethrough
    }

    /** The same style over another stretch. */
    fun sameStyle(other: TextRun) = family == other.family && size == other.size &&
        weight == other.weight && italic == other.italic && underline == other.underline &&
        strikethrough == other.strikethrough && color == other.color

    private companion object {
        /** From semi-bold up, a weight reads as bold. */
        const val BOLD_THRESHOLD = 600
    }
}

object TextFormatting {

    /** `piet::FontWeight::BOLD`, what Rnote's bold button sets. */
    const val BOLD_WEIGHT = 700

    /** The char index in [text] of UTF-8 byte offset [byteOffset], rounded down to a whole character. */
    fun charIndex(text: String, byteOffset: Int): Int {
        if (byteOffset <= 0) return 0
        var bytes = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = utf8Length(cp)
            if (bytes + n > byteOffset) return i
            bytes += n
            i += Character.charCount(cp)
        }
        return text.length
    }

    /** The UTF-8 byte offset of char index [charIndex] in [text]; never inside a surrogate pair. */
    fun byteIndex(text: String, charIndex: Int): Int {
        var end = charIndex.coerceIn(0, text.length)
        if (end in 1 until text.length && Character.isLowSurrogate(text[end])) end--
        var bytes = 0
        var i = 0
        while (i < end) {
            val cp = text.codePointAt(i)
            bytes += utf8Length(cp)
            i += Character.charCount(cp)
        }
        return bytes
    }

    private fun utf8Length(cp: Int) = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }

    /**
     * [el]'s text cut into runs of one style each, covering all of it: the box's own
     * style, with each ranged attribute laid over the stretch it covers. Where two of the
     * same kind overlap, the later one in the file wins, as a later Pango attribute does.
     */
    fun runs(el: NativeTextElement): List<TextRun> = runs(
        el.text, el.ranges,
        TextRun(0, 0, el.fontFamily, el.fontSize, el.fontWeight, el.italic, false, false, el.color)
    )

    internal fun runs(text: String, ranges: List<RangedTextAttr>, base: TextRun): List<TextRun> {
        if (text.isEmpty()) return emptyList()
        val charRanges = ranges.map { r ->
            Triple(charIndex(text, r.start), charIndex(text, r.end), r.attribute)
        }.filter { (s, e, _) -> e > s }
        val cuts = sortedSetOf(0, text.length)
        for ((s, e, _) in charRanges) {
            cuts += s.coerceIn(0, text.length)
            cuts += e.coerceIn(0, text.length)
        }
        val bounds = cuts.toList()
        val out = ArrayList<TextRun>()
        for (i in 0 until bounds.size - 1) {
            val start = bounds[i]
            val end = bounds[i + 1]
            if (end <= start) continue
            var run = base.copy(start = start, end = end)
            for ((s, e, attr) in charRanges) {
                if (s <= start && e >= end) run = run.with(attr)
            }
            val last = out.lastOrNull()
            if (last != null && last.end == start && last.sameStyle(run)) {
                out[out.lastIndex] = last.copy(end = end)
            } else {
                out += run
            }
        }
        return out
    }

    private fun TextRun.with(attr: TextAttr): TextRun = when (attr) {
        is TextAttr.Family -> copy(family = attr.family)
        is TextAttr.Size -> copy(size = attr.size)
        is TextAttr.Weight -> copy(weight = attr.weight)
        is TextAttr.Color -> copy(color = attr.color)
        is TextAttr.Italic -> copy(italic = attr.italic)
        is TextAttr.Underline -> copy(underline = attr.underline)
        is TextAttr.Strikethrough -> copy(strikethrough = attr.strikethrough)
    }

    /**
     * Which switches are on for the chars [start, end) of [el]: those every char there
     * has. For a bare cursor it is the char after it, since that is what Rnote's range
     * shifting gives text typed there (see NativeEditing.shiftRanges).
     */
    fun togglesAt(el: NativeTextElement?, start: Int, end: Int): Set<TextToggle> {
        if (el == null) return emptySet()
        val base = TextRun(0, 0, el.fontFamily, el.fontSize, el.fontWeight, el.italic, false, false, el.color)
        val runs = runs(el)
        val from = minOf(start, end).coerceIn(0, el.text.length)
        val to = maxOf(start, end).coerceIn(0, el.text.length)
        val covered = when {
            from < to -> runs.filter { it.end > from && it.start < to }
            // At the very end no ranged attribute reaches new text: the box's own style.
            from >= el.text.length -> listOf(base)
            else -> runs.filter { from >= it.start && from < it.end }
        }
        return TextToggle.entries.filterTo(mutableSetOf()) { t -> covered.isNotEmpty() && covered.all { it.has(t) } }
    }

    /**
     * Where [new] differs from [old]: the chars [first, second) of [new] that replaced
     * something (or nothing) in [old], found as the part between their common prefix and
     * suffix — the same reading of an edit NativeEditing.shiftRanges makes.
     */
    fun changedRange(old: String, new: String): Pair<Int, Int> {
        var p = 0
        val maxP = minOf(old.length, new.length)
        while (p < maxP && old[p] == new[p]) p++
        var s = 0
        val maxS = minOf(old.length, new.length) - p
        while (s < maxS && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        return p to new.length - s
    }
}
