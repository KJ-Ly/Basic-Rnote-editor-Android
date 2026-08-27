# BRNA UI Redesign — Design Doc

## Context

BRNA's current UI is a single crowded bottom `FloatingToolBar` (~600dp intrinsic
width — tools + sizes + colors all in one row, no wrap, no scroll) plus a top
`RnoteTopBar` packing 7–8 always-visible icons. There is no adaptive/responsive
logic anywhere in the project (verified: zero hits for `BoxWithConstraints`,
`WindowSizeClass`, or `LocalConfiguration` in any Kotlin source).

The ask was to make the UI more familiar to desktop Rnote users while staying
suited to smaller screens. Research into desktop Rnote's actual UI (its GTK
`.ui` files, not assumptions) turned up the key fact that reframes this ask:
**desktop Rnote's layout already *is* a tablet layout.** It isn't a
menu-bar-and-panels desktop app — the canvas is surrounded by three floating
overlay toolbars (color picker top-center, pen picker bottom-center, per-pen
config strip on a side edge) plus one collapsible sidebar. "Familiar to desktop
users" and "suited to smaller screens" turn out to be the same design, not
competing goals. Target device for this pass: Samsung tablet (Tab S w/ S-Pen).

## Region map

| Rnote region | position | BRNA today | BRNA after |
|---|---|---|---|
| Pen picker | bottom-center | `FloatingToolBar` — tools + sizes + colors crammed in one row | `PenPicker` — pens + undo/redo only |
| Color picker | top-center | 5 swatches wedged into the bottom bar; legacy `ColorPickerSheet` | `ColorPicker` — stroke/fill pads + palette + color dialog |
| Per-pen config strip | side edge, vertically centered | none — sizes are inline / in a dialog | `PenConfigStrip` — swaps content by active pen |
| Sidebar (Workspace/Settings) | left, collapsible | `PageSettingsSheet` modal bottom sheet | same content, docked as a side panel above the width breakpoint |
| Headerbar | top | `RnoteTopBar` — title + 7–8 always-visible icons | trimmed — pattern/theme/rotation/finger-draw move to Settings/Canvas Menu, matching where Rnote keeps them |

Undo/redo moves from the top bar into the pen picker — this matches Rnote
(they made the same move in their v0.7.0) and is a better fit for a tablet:
bottom-center is thumb-reachable, top-right isn't.

## Terminology mapping

| BRNA (current) | Rnote (target) |
|---|---|
| Pen | **Brush** |
| Lasso Select | **Selector** |
| Highlighter (top-level tool) | **Brush → Marker style** (see below) |
| "Blank" pattern | **None** |
| — (not built) | **Shaper**, **Typewriter**, **Tools** |

Rnote's eraser has two modes — **Trash Strokes** / **Split Strokes** — BRNA
only has trash. Rnote's selector has 4 modes (polygon/rectangle/single/
intersecting-path) plus real selection actions (duplicate, invert brightness,
lock aspect ratio); BRNA has lasso-only and delete-only.

## Decision: Highlighter becomes a Brush style, not a tool

Desktop Rnote has no highlighter *tool*. It's `BrushStyle::Marker` — "mark
underneath other strokes" — and translucency/layering is expressed via the
stroke's chrono **layer**, which is exactly how BRNA's own `.rnote` writer
already encodes it (see `RnoteNativeSerializer`'s `layer: "highlighter"`).
This is the faithful move and costs nothing ergonomically, since the pen
config strip is always visible — switching Brush style is still a single tap,
not buried in a menu.

`ToolConfig.ToolType` becomes: `BRUSH, SHAPER*, TYPEWRITER*, ERASER, SELECTOR,
TOOLS*` (starred = UI slot present, disabled, no implementation yet). Brush
gains a `BrushStyle { MARKER, SOLID, TEXTURED }` field; `MARKER` reproduces
today's highlighter behavior (translucent, wide default width).

## Icons — resolved: vendored, project is now GPL-3.0

Originally deferred as a licensing decision, not a design one (Rnote ships
~100 custom `*-symbolic` SVGs under GPL-3.0; BRNA had no `LICENSE` of its
own). Revisited once Material Icon approximations proved visibly wrong for
Eraser and Selector against real screenshots. Resolution: the user confirmed
BRNA may eventually be shared/published, so the project adopted **GPL-3.0**
in full (`LICENSE` at repo root) and the six `PenPicker` pen icons (Brush,
Shaper, Typewriter, Eraser, Selector, Tools) are now vendored directly from
Rnote's actual `pen-*-symbolic.svg` path data — see
`ui/icons/CustomIcons.kt` and `THIRD_PARTY_NOTICES.md`. Only these six files;
no other Rnote assets are included.

## What we are *not* copying from Rnote

Its own adaptivity is thinner than marketed: exactly one `AdwBreakpoint`
(collapse sidebar under 1250sp), a hard 530px window-width minimum, Flathub
lists it "Desktop Only," and a UI-scale-for-touch request was closed
not-planned. The one idea worth taking is **Focus Mode** (hide all toolbars) —
worth adding as a stretch goal, not a v1 requirement.

## Two prerequisite bug fixes

Found during the capability audit, unrelated to layout but touched by this
work either way:

1. **`nativeElements` dropped on save/export** — `MainActivity`'s
   `onSaveDocument`/`onExportSvg`/`onExportPng` builders construct
   `NoteDocument` without `nativeElements`, silently discarding
   imported text/shape/bitmap passthrough even though the storage layer
   round-trips them correctly. Undermines the `.rnote` interop fix.
2. **Selection drag is a no-op** — `onStrokesModified` ignores its
   parameter; moved strokes never reach the document, and an undo snapshot
   is pushed on every motion frame during the drag instead of once per
   gesture. Any selector work in the new config strip needs this fixed first.

## Implementation plan (phased)

1. Fix the two prerequisite bugs.
2. Centralize color into `ui/theme/Colors.kt` — the current palette is
   duplicated across 8+ files with no single source of truth; doing this
   before adding 3 new components avoids a 9th copy.
3. `PenPicker` — bottom-center bar: pen toggle group + undo/redo.
4. `ColorPicker` — top-center bar: stroke/fill pads + palette + color dialog,
   replacing the legacy `ColorPickerSheet`.
5. `PenConfigStrip` — side strip, content swapped by active pen. Biggest
   lift: new `BrushStyle` field, Eraser mode toggle, Selector mode + actions
   wired to the (now-fixed) translate/scale, disabled stub pages for
   Shaper/Typewriter/Tools.
6. Wire the three regions into `MainActivity`, add a `BoxWithConstraints`
   width breakpoint (no new dependency) so the strip/sidebar degrade on
   narrow widths.
7. `PageSettingsSheet` → docked side panel above the breakpoint, modal sheet
   below it.
8. Rebuild, install, manually verify every pen switch, selection drag
   persistence, undo/redo from the new picker, breakpoint behavior on
   rotate/resize, and a full save → desktop Rnote round-trip with a
   Brush/Marker (née highlighter) stroke.

## Status

See `HANDOFF.md` for the authoritative "what's implemented" list — this doc
tracks the redesign specifically and should be folded into `HANDOFF.md`'s
history once the phases above are complete.
