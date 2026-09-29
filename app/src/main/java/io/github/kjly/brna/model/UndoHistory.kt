package io.github.kjly.brna.model

/**
 * How far back undo reaches: Rnote's `HISTORY_MAX_LEN`, 100 steps. Every step holds a
 * copy of the note's element lists, so an unbounded history grows with every stroke for
 * as long as a note stays open — and with tabs, for every open note at once.
 */
object UndoHistory {

    const val LIMIT = 100

    /** Drops the oldest steps of [stack] (oldest first) beyond [limit]. */
    fun <T> trim(stack: MutableList<T>, limit: Int = LIMIT) {
        // One at a time: after a single push that is one removal, and it works the same
        // on any list, Compose's snapshot lists included.
        while (stack.size > limit) stack.removeAt(0)
    }
}
