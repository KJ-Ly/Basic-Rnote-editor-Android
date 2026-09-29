package io.github.kjly.brna.model

/**
 * Rnote's `ShortcutMode`: how a button's pen takes over.
 *
 * Temporary is the pen while the button is held — and, for the selector and the
 * typewriter, until what was started with it is done: the selection let go, the text box
 * left. Permanent switches for good. Toggle switches, and the same button again switches
 * back. Disabled leaves the button doing nothing.
 */
enum class ShortcutMode(val displayName: String, val apiName: String) {
    TEMPORARY("Temporary", "temporary"),
    PERMANENT("Permanent", "permanent"),
    TOGGLE("Toggle", "toggle"),
    DISABLED("Disabled", "disabled");

    companion object {
        fun fromApiName(name: String): ShortcutMode? = entries.firstOrNull { it.apiName == name }
    }
}

/**
 * Rnote's `ShortcutKey`s that a tablet has, with the titles of the rows in its "Button
 * Shortcuts" settings. A drawing pad's buttons are left out: Android has no such thing.
 *
 * [momentary] is a shortcut with nothing to hold down afterwards: its temporary pen lasts
 * until the next thing drawn with it is done, as every temporary pen does in Rnote.
 */
enum class ShortcutKey(
    val apiName: String,
    val title: String,
    val subtitle: String,
    val momentary: Boolean = false
) {
    STYLUS_PRIMARY_BUTTON(
        "stylus_primary_button", "Stylus Primary Button Action", "Set the action for the primary stylus button"
    ),
    STYLUS_SECONDARY_BUTTON(
        "stylus_secondary_button", "Stylus Secondary Button Action", "Set the action for the secondary stylus button"
    ),
    MOUSE_SECONDARY_BUTTON(
        "mouse_secondary_button", "Mouse Secondary Button Action", "Set the action for the secondary mouse button"
    ),
    TOUCH_TWO_FINGER_LONG_PRESS(
        "touch_two_finger_long_press", "Touch Two-Finger Long-Press Action",
        "Set the action for the touch two-finger long-press gesture", momentary = true
    ),
    KEYBOARD_CTRL_SPACE(
        "keyboard_ctrl_space", "Keyboard Ctrl-Space Action", "Set the action for the keyboard Ctrl plus Space shortcut"
    );

    companion object {
        fun fromApiName(name: String): ShortcutKey? = entries.firstOrNull { it.apiName == name }
    }
}

/** Rnote's `ShortcutAction::ChangePenStyle`: the pen a button brings out, and how. */
data class ShortcutAction(val tool: ToolType, val mode: ShortcutMode)

/**
 * Rnote's `Shortcuts`: what each button does. Rnote's own defaults to start with — the pen's
 * first button erases while held, as the S Pen's did here before, its second selects.
 */
data class PenShortcuts(val actions: Map<ShortcutKey, ShortcutAction> = DEFAULTS) {

    operator fun get(key: ShortcutKey): ShortcutAction? = actions[key]

    fun with(key: ShortcutKey, action: ShortcutAction): PenShortcuts = copy(actions = actions + (key to action))

    /** For the settings: `key=tool:mode`, one per line, in Rnote's names. */
    fun encode(): String = ShortcutKey.entries.mapNotNull { key ->
        actions[key]?.let { "${key.apiName}=${it.tool.apiName}:${it.mode.apiName}" }
    }.joinToString("\n")

    companion object {
        val DEFAULTS: Map<ShortcutKey, ShortcutAction> = mapOf(
            ShortcutKey.STYLUS_PRIMARY_BUTTON to ShortcutAction(ToolType.ERASER, ShortcutMode.TEMPORARY),
            ShortcutKey.STYLUS_SECONDARY_BUTTON to ShortcutAction(ToolType.SELECTOR, ShortcutMode.TEMPORARY),
            ShortcutKey.MOUSE_SECONDARY_BUTTON to ShortcutAction(ToolType.SHAPER, ShortcutMode.TEMPORARY),
            ShortcutKey.TOUCH_TWO_FINGER_LONG_PRESS to ShortcutAction(ToolType.ERASER, ShortcutMode.TOGGLE),
            ShortcutKey.KEYBOARD_CTRL_SPACE to ShortcutAction(ToolType.TOOLS, ShortcutMode.TOGGLE)
        )

        /** What [encode] wrote; a line it can't read keeps Rnote's default for that button. */
        fun decode(text: String?): PenShortcuts {
            if (text.isNullOrBlank()) return PenShortcuts()
            val read = text.lines().mapNotNull { line ->
                val key = ShortcutKey.fromApiName(line.substringBefore('=').trim()) ?: return@mapNotNull null
                val value = line.substringAfter('=', "")
                val tool = ToolType.fromApiName(value.substringBefore(':').trim()) ?: return@mapNotNull null
                val mode = ShortcutMode.fromApiName(value.substringAfter(':', "").trim()) ?: return@mapNotNull null
                key to ShortcutAction(tool, mode)
            }.toMap()
            return PenShortcuts(DEFAULTS + read)
        }
    }
}

/**
 * What pressing a shortcut does to the pen, as Rnote's `PenHolder` does it: a temporary pen
 * laid over the one picked, which comes back once it is done; a permanent one put in its
 * place; a toggle that remembers what it replaced, until another button or a pen picked by
 * hand makes that moot.
 *
 * Each call is told the pen in use and answers with the pen to use now, or null for no
 * change; the app switches, and this keeps what it needs to switch back.
 */
class PenShortcutState {

    /** The pen under a temporary one, which comes back when that is done. Null when there is none. */
    var underlying: ToolType? = null
        private set

    private var overrideKey: ShortcutKey? = null
    private var overrideHeld = false
    // A momentary shortcut's pen waits for something to be drawn with it before it can go.
    private var awaitingGesture = false
    private var togglePen: ToolType? = null
    private var previousKey: ShortcutKey? = null

    /** [key] went down with [current] in use: `handle_pressed_shortcut_key`. */
    fun press(key: ShortcutKey, shortcuts: PenShortcuts, current: ToolType): ToolType? {
        val action = shortcuts[key]
        var next: ToolType? = null
        if (action != null) when (action.mode) {
            ShortcutMode.TEMPORARY -> {
                if (underlying == null) underlying = current
                overrideKey = key
                overrideHeld = !key.momentary
                awaitingGesture = key.momentary
                next = action.tool
            }
            ShortcutMode.PERMANENT -> {
                togglePen = null
                next = underneath(action.tool)
            }
            ShortcutMode.TOGGLE -> {
                val back = togglePen
                next = if (back == null) {
                    togglePen = underlying ?: current
                    underneath(action.tool)
                } else if (previousKey != key) {
                    // Another toggle button: on to its pen, not back.
                    underneath(action.tool)
                } else {
                    togglePen = null
                    underneath(back)
                }
            }
            ShortcutMode.DISABLED -> Unit
        }
        previousKey = key
        return next?.takeIf { it != current }
    }

    /** [key] came up; the pen to go back to, if that ends a temporary pen done with. */
    fun release(key: ShortcutKey, busy: Boolean): ToolType? {
        if (key == overrideKey) overrideHeld = false
        return settle(busy)
    }

    /** Something drawn has ended; the pen to go back to, if that ends a temporary pen. */
    fun gestureEnded(busy: Boolean): ToolType? {
        awaitingGesture = false
        return settle(busy)
    }

    /**
     * The pen to go back to when the temporary one is done with: its button up, and not
     * [busy] — a selection still held, a text box still open — as Rnote takes its override
     * away only once the pen reports it has finished.
     */
    fun settle(busy: Boolean): ToolType? {
        val back = underlying ?: return null
        if (overrideHeld || awaitingGesture || busy) return null
        underlying = null
        overrideKey = null
        return back
    }

    /** A pen picked by hand: Rnote's `change_style`, with any temporary pen taken off first. */
    fun picked() {
        underlying = null
        overrideKey = null
        overrideHeld = false
        awaitingGesture = false
        togglePen = null
        previousKey = null
    }

    /**
     * Rnote's `change_style_int`: the pen picked changes, but a temporary pen out stays on top
     * of it until done, and the new pen is what comes back.
     */
    private fun underneath(tool: ToolType): ToolType? {
        if (underlying == null) return tool
        underlying = tool
        return null
    }
}
