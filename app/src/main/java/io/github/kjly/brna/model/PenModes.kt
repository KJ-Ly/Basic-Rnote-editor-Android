package io.github.kjly.brna.model

/**
 * Rnote's `PenMode`: which end of the stylus is in use. The tip is the pen, the other end —
 * on a stylus that has one — the eraser. Only a stylus changes it: a finger or a mouse
 * leaves it as the stylus last left it, as in Rnote.
 */
enum class PenMode(val apiName: String) {
    PEN("pen"),
    ERASER("eraser")
}

/**
 * Rnote's "Stylus pen modes" settings (0.15, #1772): the pen each end of the stylus uses —
 * its `pen_mode_pen_style` and `pen_mode_eraser_style` — and whether that end is locked.
 * A locked end keeps its pen when another is picked in the pen picker, which says so
 * ("Tool Locked") with a button to unlock it; the keyboard's Ctrl+1 to 6 and the settings
 * still change it, as in Rnote. Rnote's defaults: the tip free, the eraser end locked.
 */
data class PenModes(
    val penTool: ToolType = ToolType.BRUSH,
    val eraserTool: ToolType = ToolType.ERASER,
    val lockPen: Boolean = false,
    val lockEraser: Boolean = true
) {
    fun tool(mode: PenMode): ToolType = if (mode == PenMode.PEN) penTool else eraserTool

    fun locked(mode: PenMode): Boolean = if (mode == PenMode.PEN) lockPen else lockEraser

    fun withTool(mode: PenMode, tool: ToolType): PenModes =
        if (mode == PenMode.PEN) copy(penTool = tool) else copy(eraserTool = tool)

    fun withLock(mode: PenMode, lock: Boolean): PenModes =
        if (mode == PenMode.PEN) copy(lockPen = lock) else copy(lockEraser = lock)

    /**
     * Rnote's `set_pen_style_with_lock`: what picking [tool] in the pen picker does, with
     * [current] in use and [mode] the end in use. A locked end refuses another pen, and
     * leaves the one it has as it is — a button's temporary pen too, which picking it
     * again would otherwise make the end's own.
     */
    fun pick(mode: PenMode, tool: ToolType, current: ToolType): Pick = when {
        !locked(mode) -> Pick.SWITCH
        tool != current -> Pick.LOCKED
        else -> Pick.NOTHING
    }

    enum class Pick { SWITCH, LOCKED, NOTHING }

    /** For the settings: `key=value`, one per line, in Rnote's names. */
    fun encode(): String = listOf(
        "${PenMode.PEN.apiName}=${penTool.apiName}",
        "${PenMode.ERASER.apiName}=${eraserTool.apiName}",
        "lock_${PenMode.PEN.apiName}=$lockPen",
        "lock_${PenMode.ERASER.apiName}=$lockEraser"
    ).joinToString("\n")

    companion object {
        /** What [encode] wrote; a line it can't read keeps Rnote's default for it. */
        fun decode(text: String?): PenModes {
            val defaults = PenModes()
            if (text.isNullOrBlank()) return defaults
            val values = text.lines().associate { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
            fun tool(key: String, default: ToolType) =
                values[key]?.let(ToolType::fromApiName)?.takeIf { it.isImplemented } ?: default
            fun lock(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
            return PenModes(
                penTool = tool(PenMode.PEN.apiName, defaults.penTool),
                eraserTool = tool(PenMode.ERASER.apiName, defaults.eraserTool),
                lockPen = lock("lock_${PenMode.PEN.apiName}", defaults.lockPen),
                lockEraser = lock("lock_${PenMode.ERASER.apiName}", defaults.lockEraser)
            )
        }
    }
}
