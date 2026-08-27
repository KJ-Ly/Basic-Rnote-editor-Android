# Third-Party Notices

## Icons (`app/src/main/java/com/rnote/baby/ui/icons/`)

The vector path data for the following icons is adapted from desktop Rnote's
own symbolic icon set:

- Source: https://github.com/flxzt/rnote
- Path: `crates/rnote-ui/data/icons/scalable/actions/*.svg`
- License: GPL-3.0 (see `LICENSE` at the root of this repository)
- Copyright: the Rnote contributors

**`CustomIcons.kt`** — the six `PenPicker` pen icons: Brush, Shaper,
Typewriter, Eraser, Selector, Tools (`pen-*-symbolic.svg`).

**`GeneratedIcons.kt`** — icons used in `ColorPicker` and `PenConfigStrip`:
Undo/Redo (`edit-{undo,redo}-symbolic.svg`), Stroke/Fill color pads and the
more-colors button (`stroke-color`, `fill-color`, `preferences-color-symbolic.svg`),
brush styles (`pen-brush-style-{marker,solid,textured}-symbolic.svg`), eraser
modes (`pen-eraser-{trash,split}-colliding-strokes-symbolic.svg`), the
selector polygon mode (`pen-selector-polygon-symbolic.svg`), and selection
actions (`selection-{select-all,deselect-all,duplicate,trash,invert-color,resize-lock-aspectratio}-symbolic.svg`).
Several of the source SVGs (stroke-color, fill-color, pen-brush-style-*)
contain a decorative background dot-texture pattern alongside the real glyph;
only the real glyph's path(s) were extracted (see `GeneratedIcons.kt`'s KDoc
for how they were distinguished).

Path coordinates were transcribed as-is from the original SVGs' `d`
attributes into Compose `ImageVector`s via `addPathNodes`; no other assets
from the Rnote project are included in this repository.

This repository is licensed GPL-3.0 in its entirety (see `LICENSE`) so that
incorporating this GPL-3.0 material is fully compliant.
