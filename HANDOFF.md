# Baby Rnote Android — Project Handoff Document

## What This Is

An Android Jetpack Compose note-taking app that aims for file-format interoperability with [desktop Rnote](https://github.com/flxzt/rnote) (a GTK4/Rust vector drawing application for Linux). The app can open, edit, and save `.rnote` files produced by the desktop version. It does NOT embed the Rust engine — it reimplements the rendering and persistence layers in pure Kotlin/Compose.

The project is named `com.rnote.baby` and lives at `app/src/main/java/com/rnote/baby/`.

---

## Architecture Overview

```
com.rnote.baby/
├── MainActivity.kt              # Entry point, state management, SAF launchers
├── model/                        # Data layer
│   ├── ToolConfig.kt             # ToolType (Brush/Shaper*/Typewriter*/Eraser/Selector/Tools*),
│   │                              # BrushStyle (Marker/Solid/Textured*), BrushSizePreset (* = UI slot, no impl yet)
│   ├── PaperStyle.kt             # Paper background config (colors, patterns, dims)
│   ├── PageSize.kt               # Standard page sizes (A2-A6, Letter, Legal, Custom)
│   ├── LayoutMode.kt             # Fixed Size / Continuous Vertical / Infinite
│   ├── Stroke.kt                 # Stroke + StrokePoint data classes
│   ├── InkPoint.kt               # Alias for StrokePoint (compatibility shim)
│   ├── RnoteDocument.kt          # NoteDocument — in-memory editable document
│   ├── RnoteNativeDocument.kt    # Native .rnote schema model (engine snapshot)
│   ├── Vector2D.kt               # 2D vector math
│   └── ViewportState.kt          # Pan/zoom camera state
├── ui/
│   ├── canvas/
│   │   ├── DrawingCanvas.kt      # Core canvas: touch/stylus input, stroke rendering
│   │   ├── PaperBackgroundRenderer.kt  # Page tiling, patterns (dots/grid/lines/iso)
│   │   ├── InkSmoother.kt        # Quadratic Bézier path smoothing
│   │   └── SelectionManager.kt   # Lasso selection, bounding box, translate, scale
│   ├── components/               # Desktop-Rnote-familiar floating regions — see UI_REDESIGN.md
│   │   ├── PenPicker.kt          # Bottom-center: pen toggle group + undo/redo
│   │   ├── ColorPicker.kt        # Top-center: stroke/fill pads + palette + full-palette dialog
│   │   ├── PenConfigStrip.kt     # Left edge, vertically centered: per-pen settings (swaps by active pen)
│   │   ├── PageSettingsSheet.kt  # 3-section settings; docked side panel at tablet width, modal sheet below the breakpoint
│   │   └── RnoteTopBar.kt        # Trimmed top bar: title, finger-draw toggle, page settings, overflow menu
│   ├── icons/
│   │   ├── CustomIcons.kt        # PenPicker's 6 pen icons, vendored from real Rnote SVGs (GPL-3.0)
│   │   └── GeneratedIcons.kt     # ColorPicker/PenConfigStrip icons (undo/redo, brush styles, eraser
│   │                              #   modes, selection actions), same source — see THIRD_PARTY_NOTICES.md
│   └── theme/
│       ├── Theme.kt              # Material3 dark/light color schemes
│       └── Colors.kt             # BrnaColors — centralized chrome/palette tokens for the floating regions
├── storage/
│   ├── FileManager.kt            # File I/O coordinator (load/save/export)
│   ├── DocumentSerializer.kt     # JSON serializer for our native format
│   ├── RnoteNativeParser.kt      # Streaming GZIP+JSON parser for .rnote files
│   ├── RnoteNativeSerializer.kt  # Serializer back to .rnote format
│   └── SettingsManager.kt        # SharedPreferences persistence
├── export/
│   ├── SvgExporter.kt            # SVG vector export
│   └── ImageExporter.kt          # PNG raster export
└── bridge/
    └── RnoteNativeBridge.kt      # JNI stubs (prepared for future Rust FFI)
```

---

## What's Implemented

### Drawing Engine
- **Brush tool** with pressure-sensitive stroke width (S-Pen / stylus). Two working styles:
  **Solid** (the old "Pen") and **Marker** (the old "Highlighter" — translucent blending). A
  third style, **Textured**, has a UI slot in `PenConfigStrip` but no rendering yet.
  Matches desktop Rnote, which also has no separate highlighter tool — highlighting is a
  brush style, and the translucent/layered identity round-trips through `.rnote` via the
  stroke's chrono `layer` field (see `RnoteNativeSerializer`), not a bespoke field.
- **Eraser tool** (proximity-based stroke deletion / "Trash Strokes" mode). A **Split Strokes**
  mode has a UI slot but no implementation yet.
- **Selector tool** (lasso — ray-casting polygon containment, 25% threshold; the "Select With a
  Polygon" mode is implemented, the other 3 Rnote selector modes have UI slots but no impl)
  - Selected strokes can be dragged/translated, and the move is now actually persisted
    into the document (previously a no-op — see UI_REDESIGN.md)
  - Select All / Deselect All / Duplicate / Delete are wired in `PenConfigStrip`
  - Bounding box overlay with dashed border
- **Shaper**, **Typewriter**, **Tools** pens have a slot in `PenPicker`/`PenConfigStrip` but
  are disabled — no implementation. See Roadmap.
- **Brush size controls**: 3 presets (S/M/L) per tool + numerical adjuster dialog with slider and direct input
- **Ink smoothing**: Quadratic Bézier midpoint interpolation (InkSmoother.kt)
- **Hover cursor**: Stylus hover preview (brush circle or eraser ring)

### Input Handling
- **Stylus-only mode** (default): Finger pans/zooms, only stylus draws
- **Finger drawing mode**: Toggle to allow finger drawing
- **S-Pen side button**: Edge-triggered undo (press once = undo, hold = no repeat)
- **S-Pen Air Actions**: Page Down = Undo, Page Up = Redo (via onKeyUp)
- **2-finger pinch-to-zoom**: Anchored to pinch midpoint (0.25x–5.0x range)
- **2-finger pan**: Simultaneous with zoom

### Paper & Background
- **6 pattern types**: Dots, Grid, Lines, Isometric Grid, Isometric Dots, Blank
- **Page sizes**: A2–A6, US Letter, US Legal, Custom, Infinite
- **Layout modes**: Fixed Size (single page), Continuous Vertical (column), Infinite (2D tiled grid)
- **Format borders**: Toggleable page boundary lines
- **Origin indicator**: Green × marker at canvas (0,0)
- **Custom colors**: Background and grid/dot colors with preset swatches
- **Pattern spacing**: Independent width/height sliders
- **DPI setting**: 24–300 dpi with real-time mm/inch unit conversion
- **Orientation**: Portrait/Landscape toggle
- **Dark/Light mode**

### File I/O
- **Save/Load our JSON format**: Simple JSON with strokes, paper style, metadata
- **Save/Load native .rnote format**: GZIP-compressed JSON matching desktop Rnote's engine snapshot schema
  - Supports v0.14+ path format (`start` + `segments` with `lineto`/`quadbez`/`cubbez`)
  - Case-insensitive style matching (`smooth`/`textured`)
  - Chrono component ordering with `{t: N}` format
  - Preserves non-brush elements (text, shapes, bitmaps) as passthrough `nativeElements`
- **SVG export**: W3C-compliant SVG with Bézier paths
- **PNG export**: Rasterized bitmap export
- **Auto-detection**: GZIP magic bytes (0x1F 0x8B) distinguish .rnote from JSON on load

### Settings Persistence
- All paper style fields saved to SharedPreferences
- Finger drawing toggle persisted
- Enum parsing with safe fallbacks

---

## What's NOT Implemented (Roadmap)

These are features present in desktop Rnote that haven't been ported yet:

### High Priority
1. **Selection transformation handles** — Corner scaling, rotation handle. The `SelectionManager` already has `scaleStrokes()` but no UI handles. (Duplicate/Select All/Deselect All/Delete are now implemented, in `PenConfigStrip`'s Selector page.)
2. **Shaper pen** — Lines, arrows, rectangles, ellipses, freehand auto-shape recognition. Has a UI slot in `PenPicker`/`PenConfigStrip` (disabled). The native document model (`RnoteNativeDocument.kt`) already defines `NativeShapeElement` variants.
3. **Multi-layer support** — Desktop Rnote has layers with visibility/lock/reorder. Our model is flat (single stroke list).
4. **Typewriter pen** — `NativeTextElement` is modeled but not editable. Has a UI slot (disabled).

### Medium Priority
5. **Brush path models** — Desktop Rnote has 3 stroke renderers: Simple Polyline, Curved Bézier, Modeled 2D Outline Mesh. We currently use quadratic Bézier midpoint interpolation only. Also has a **Textured** brush style slot (disabled) alongside the working Solid/Marker styles.
6. **Split Strokes eraser mode** — Only whole-stroke "Trash Strokes" erasing is implemented; has a UI slot (disabled).
7. **Undo/redo for selection transforms** — Currently only stroke add/erase/clear/drag are undoable (drag now pushes exactly one snapshot per gesture — see UI_REDESIGN.md); paper style / viewport / tool changes are not.
8. **Palm rejection** — No explicit palm rejection beyond stylus-only mode.
9. **Zoom-to-fit / Zoom-to-selection**
10. **Tools pen** — Insert Vertical Space / Move View / Zoom In-Out / Laser. Has a UI slot (disabled).

### Lower Priority / Aspirational
11. **Rust engine integration via NDK** — `RnoteNativeBridge.kt` has JNI stubs for `librnote_engine_android.so`. Desktop Rnote's engine is split into `rnote-compose` (math) and `rnote-engine` (document/strokes), which are GTK-free Rust crates. These could theoretically be cross-compiled for Android via `cargo-ndk`. See [rnote#390](https://github.com/flxzt/rnote/issues/390) for context — GTK4 UI cannot run on Android, but the engine crates have no GTK dependency.
12. **PDF import/export**
13. **Clipboard support** (copy/paste strokes)
14. **Document tabs / file browser**
15. **Handedness toggle** — Desktop Rnote mirrors its whole UI (sidebar side, `PenConfigStrip` side, popover direction) via one setting. Deliberately not built in this pass — see UI_REDESIGN.md — but cheap to add now that the config strip is a real region.
16. **Focus Mode** — Rnote's answer to small screens: hide all floating chrome. Noted as worth stealing in UI_REDESIGN.md, not yet built.

---

## Known Quirks & Design Decisions

- **`PageSize.INFINITE` vs `LayoutMode.INFINITE`**: Legacy overlap. `PageSize.INFINITE` was the original way to represent infinite canvas before `LayoutMode` was added. `PaperBackgroundRenderer` checks `pageSize.isInfinite` to decide between paged and infinite rendering. These should eventually be unified.

- **`Stroke.toolType` vs `Stroke.isHighlighter`**: Redundant boolean. Both exist because `isHighlighter` was added before `toolType`. They're kept in sync at creation time but could theoretically diverge.

- **`StrokePoint.timestamp` returns hardcoded 0L**: Points don't carry timestamps. The field exists as a compatibility shim.

- **Export dimensions**: SVG and PNG exporters now use `effectivePageWidthPx`/`effectivePageHeightPx` from the document's paper style, with a floor of 1920×1080 to avoid tiny exports on infinite canvas mode.

- **Fill color has a UI slot but no model support**: `ColorPicker`'s Stroke/Fill pad toggle shows Fill as disabled — `Stroke` only has a single `color` field today, there's no separate fill concept to wire it to.

- **`PenConfigStrip` docks left, unconditionally**: matches desktop Rnote's right-handed default (sidebar and pen config both dock left — see UI_REDESIGN.md). No handedness toggle yet (Roadmap #15), so this isn't user-configurable.

- **Adaptive breakpoint is a single global flag**: `isCompactWidth` (< 600dp) is computed once in `MainActivity` via `LocalConfiguration` and threaded down as a plain `Boolean`, not `BoxWithConstraints`-per-region. Simple and sufficient for the current three floating regions, but doesn't rebalance individual bar contents (e.g. `PenPicker`'s icon row isn't itself responsive) if the width is narrow but not narrow enough to hide the whole strip.

---

## Build & Run

**Neither the JDK nor adb is on `PATH`** — both need absolute paths. Assuming
otherwise is what makes `gradlew` die with "JAVA_HOME is not set and no 'java'
command could be found" and `adb` with "command not found"; neither error means
the tool is missing.

```powershell
# Set JAVA_HOME to Android Studio's bundled JBR
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

# Compile check
.\gradlew.bat compileDebugKotlin

# Build debug APK
.\gradlew.bat assembleDebug

# Install to connected device
.\gradlew.bat installDebug
```

### Driving the app on-device

The connected tablet (serial `R52N90MG5RZ`, an SM-T870) can be navigated
directly from a shell — no emulator needed. An earlier session recorded that
"adb/emulator isn't available in this environment" and handed all on-device
verification back to the user; that was wrong, and only true of bare `adb`
with no path.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

& $adb devices        # want "device" — "offline"/"unauthorized" won't accept input
& $adb -s R52N90MG5RZ shell am start -n com.rnote.baby/.MainActivity
& $adb -s R52N90MG5RZ shell input tap <x> <y>

# Screenshot. From bash this is one step (verified working):
#   adb -s R52N90MG5RZ exec-out screencap -p > shot.png
# Do NOT use that redirect in PowerShell — it re-encodes the binary stream as
# text and corrupts the PNG. Capture on-device and pull instead (verified):
& $adb -s R52N90MG5RZ shell screencap -p /sdcard/shot.png
& $adb -s R52N90MG5RZ pull /sdcard/shot.png .
```

Read the screenshot, locate the next target, tap, repeat. Caveats:

- `installDebug` silently skips offline devices — the emulator entry usually is
  one, so check `adb devices` before blaming the build.
- Taps are raw screen coordinates read off a screenshot, so any layout change
  invalidates them, and a mis-tap hits whatever is actually there in a real
  document.
- This only verifies what *renders*. Stylus pressure, tilt, and palm rejection
  still need a hand on the device.

**Min SDK**: Check `app/build.gradle.kts` for `minSdk` value.
**Target**: Jetpack Compose with Material3.
**No native libraries required** — pure Kotlin/Compose (the JNI bridge is stubbed).

---

## Test File

`test.rnote` in the project root is a v0.14 format file from desktop Rnote that can be used to verify import compatibility. Open it via the app's "Open" menu item.

---

## Recent Changes (August 2026)

- **Canvas is now density-aware, and the dot pattern scales with zoom** — two separate bugs found by measuring BRNA and desktop Rnote screenshots pixel-by-pixel rather than by eye:
  - `PaperBackgroundRenderer` drew each dot as a **fixed 1.6px-radius circle in screen space** while the spacing scaled with zoom, so the further you zoomed the more the dots fell behind — at 500% the measured dot/spacing ratio was 0.025 against desktop's 0.047. Desktop Rnote (`rnote-engine/src/document/background.rs`) uses `DOTS_WIDTH = 1.5` canvas units drawn as a **rounded square** (corner radius width/3) in *document* space, on a `PATTERN_SIZE_DEFAULT = 32.0` grid. Now matched: measured ratio 0.0471 vs desktop's 0.0473.
  - The same screen-space bug affected every other pattern and was fixed with it: ruled, grid and isometric-grid lines were flat `1f`/`0.8f` widths and now use Rnote's `LINE_WIDTH = 0.5` canvas px scaled by zoom. Rnote's isometric **dots** are also not round — `gen_iso_dots_pattern()` draws small hexagons of `HEXAGON_HEIGHT = 2.0`, so `drawIsoDotHexagon()` replaces the circle there. (The hexagon's vertex order is ours; Rnote's `QUARTER_SQRT_THREE`/`HALF_SQRT_THREE` figures are the half-width and width of exactly this pointy-top hexagon.) All five patterns measured on-device against expectation: DOTS/GRID/LINES spacing 91.7px vs 91.5 predicted, line width ~1.43px, ISO_DOTS hexagon 6.0px and row spacing 79.4px vs 79.3 predicted.
  - More fundamentally, the canvas mapped **1 canvas unit → 1 device px**, ignoring display density entirely (`density` was used only for the eraser's touch radius). Canvas units are defined at `CANVAS_DPI = 96`, so on a 275 dpi tablet panel "100%" rendered a document ~2.9x physically smaller than the same document on a 96 dpi desktop — dots 0.70" apart against desktop's 1.98" at the *same* percentage. `ViewportState` now carries a `displayScale` (real panel dpi ÷ `CANVAS_DPI`, from `xdpi`/`ydpi` with a sanity check against the density bucket) and exposes `effectiveScale = zoomScale * displayScale`. **Every screen↔canvas conversion and every screen-space size derived from a canvas-space one must use `effectiveScale`; `zoomScale` is only the number shown to the user.**
  - Because a zoom figure is now device-independent, desktop's `Camera::ZOOM_MIN`/`ZOOM_MAX` (0.2 / 6.0) are used **verbatim**, replacing the old `0.25f..5.0f` clamp. An earlier attempt scaled `ZOOM_MAX` per-device instead (`6.0 * dpi/96` ≈ 21.25 here) — that was a patch over the missing display scale and was dropped in favour of fixing the base.
  - Visible consequence: existing documents render ~2.9x larger at 100% on a tablet than they did before, with stroke widths and page boundaries scaling to match. This is correct — it's what desktop shows — and saved `.rnote` files are untouched, since all of this is a view transform only.

- **Wheel picker uses a tiered step size** instead of one flat step for the whole range — `tieredStrokeSizeValues()` in `PenConfigStrip.kt`: 0.1 up to 12, 0.5 up to 50, 1 up to 100, 2 up to 128 (each tier capped at the tool's actual `maxRange` if smaller, e.g. the brush's Solid style tops out at 64). Fine control where it matters (small strokes), without a 1280-row wheel to scroll through for large ones.
- **Stroke size picker rebuilt again as a scroll-wheel popup**, replacing the inline drag-to-scrub spin button (user preferred a scrollable popup after all — see the two entries below for the earlier attempts). `PenConfigStrip`'s numeric chip now opens a `Dialog` containing `WheelNumberPicker`: a `LazyColumn` of values behind a fixed center highlight band, live-reporting whichever value is centered as you scroll (via `derivedStateOf` over `LazyListState.layoutInfo`, comparing each visible item's screen-space center to the viewport's), and animating to rest exactly centered once scrolling stops rather than depending on a snap-fling API that may not be present in this Compose Foundation version. Tapping any row also selects it directly and scrolls it to center.
- **Replaced the spin-button's text entry with drag-to-scrub** (user feedback: typing into a 36dp-wide `BasicTextField` with the system decimal keyboard was fiddly on a touchscreen). `PenConfigStrip`'s `SpinButton` no longer has a keyboard path at all — the up/down chevrons still nudge by one step, and dragging vertically on the number itself scrubs the value continuously, scaled so a ~200dp drag sweeps the tool's full range (same pattern as Figma/Procreate numeric drag fields). Still fully inline, no popup.
- **Reverted Brush/Marker icons to Material equivalents "for now"** (user feedback: the vendored `pen-brush-symbolic`/`pen-brush-style-marker-symbolic` shapes were illegible at small size) — `PenPicker`'s Brush tool icon and `PenConfigStrip`'s Marker brush-style icon are back to `Icons.Default.Brush`/`Icons.Default.Highlight`. The vendored `ImageVector`s (`CustomIcons.Brush`, `GeneratedIcons.BrushStyleMarker`) are left defined but unused rather than deleted, in case this gets revisited. Also: shrank the `PenConfigStrip` stroke-size preset dots (were 8/14/20dp, now 5/9/13dp — read as oversized blobs), and replaced the numeric-chip-opens-a-dialog size editor with an always-inline vertical spin-button (up-chevron / editable number / down-chevron) stacked above the preset dots, matching desktop Rnote's actual `RnStrokeWidthPicker` layout — no popup at all now, per explicit request.
- **Vendored the remaining top/side-bar icons from real Rnote SVGs** (extending the `PenPicker`-only vendoring from the previous entry, per user request to match "exactly"): Undo/Redo, `ColorPicker`'s Stroke/Fill pads and more-colors button, and every icon in `PenConfigStrip` (brush styles, eraser modes, the selector polygon mode, and all six selection actions) — `ui/icons/GeneratedIcons.kt`. Icon names were read directly from Rnote's own `.ui` files (`brushpage.ui`, `eraserpage.ui`, `selectorpage.ui`, `colorpicker.ui`, `penpicker.ui`) rather than guessed from the rendered screenshot, then the exact SVGs fetched by name. Several of those SVGs bundle a decorative background dot-texture pattern alongside the real glyph (an Inkscape/GNOME convention); the real glyph was identified and extracted programmatically rather than by eye, since the decorative dots use legacy space-separated path syntax while the real glyph is a modern comma-separated Inkscape export.

- **Project is now GPL-3.0-licensed; the six `PenPicker` pen icons are vendored directly from desktop Rnote.** Previously deferred as a licensing question (see UI_REDESIGN.md); user confirmed BRNA may eventually be shared, so the clean path was to adopt GPL-3.0 in full (`LICENSE` at repo root, canonical text pulled from `flxzt/rnote` itself) rather than keep hand-approximating icons indefinitely. `ui/icons/CustomIcons.kt` now builds `ImageVector`s directly from the real `pen-{brush,shaper,typewriter,eraser,selector,tools}-symbolic.svg` path data (transcribed via Compose's `addPathNodes`, exact coordinates, no re-drawing by eye) — attribution and scope in `THIRD_PARTY_NOTICES.md`. Only these six files are vendored; no other Rnote assets.
- **Second round of icon/style correction against a fresh screenshot.** Fetched desktop Rnote's actual symbolic SVG source (`crates/rnote-ui/data/icons/scalable/actions/`) instead of guessing further from Material Icons:
  - The real eraser icon is a tilted rectangle ("eraser block") — nothing in Material Icons resembles it, which is why the earlier `Backspace` guess still looked wrong. Hand-built a custom `ImageVector` (`ui/icons/CustomIcons.kt`) matching that silhouette instead.
  - Confirmed `CropFree` (Selector) was already an accurate match — the real `pen-selector-symbolic.svg` is literally 4 corner brackets. Also confirmed Shaper/Tools icon choices (`Category`, `Build`) already resemble the real `pen-shaper-symbolic.svg`/`pen-tools-symbolic.svg` reasonably well.
  - Bigger structural fix: desktop Rnote's pen icons are a single neutral color always — only the button's *background* changes for the active pen (a GTK checked-toggle style). BRNA was giving each tool its own tint (blue Brush, purple Selector, etc.), which read as a distinctly non-Rnote "rainbow toolbar." `PenPicker`/`PenConfigStrip` now use one shared accent tint for whichever pen is active and a neutral white for the rest, matching the real convention. `BrnaColors.BrushTint`/`EraserTint`/`SelectorTint` removed as no longer applicable.
- **Corrected the redesign against real desktop Rnote screenshots** (side-by-side comparison, not just the .ui source research below). Fixes:
  - Default stroke width was 6px (Medium preset) — real Rnote defaults to 2px (Small). Default eraser width was 32px — real Rnote's eraser presets are 4/9/24, not the invented 16/32/64. `BrushSizePreset`'s Solid/Marker/Eraser px values now match Rnote's actual gschema defaults exactly; `ToolConfig`'s default `strokeWidth`/`highlighterWidth`/`eraserWidth` follow.
  - Default stroke color was blue (`0xFF82AAFF`) — real Rnote defaults to black. The whole quick-access palette was invented pastel colors instead of Rnote's actual 9-slot default (black/white/transparent/light-blue/blue/green/yellow/orange/dark-red, GNOME/Adwaita-derived) — `BrnaColors.PenPalette`/`FullPalette` now use the real values, and a transparent swatch renders as a checkerboard instead of being invisible.
  - `PenPicker`'s Eraser icon was `AutoFixHigh` (a magic wand) and Selector was `Gesture` (a squiggle) — neither reads as its tool. Swapped to `Backspace` and `CropFree`.
  - `PenConfigStrip`'s stroke-width presets were lettered "S"/"M"/"L" circles — real Rnote's `RnStrokeWidthPicker` shows plain dots whose size previews the actual stroke width, no letters. Now matches.
  - `ColorPicker`'s Fill-color pad and "more colors" button both used `FormatColorFill`, an unintentional duplicate that read as a UI bug. Stroke pad is now `Edit`, more-colors is now `Palette`.
- **UI redesign toward desktop-Rnote familiarity — see UI_REDESIGN.md for the full design doc.**
  Replaced the single crowded bottom `FloatingToolBar` + `ColorPickerSheet` with three floating
  regions matching desktop Rnote's actual overlay layout (`PenPicker` bottom-center, `ColorPicker`
  top-center, `PenConfigStrip` on the left edge), trimmed `RnoteTopBar` to match (undo/redo moved
  into `PenPicker`; pattern/theme/rotation quick-toggles removed as duplicates of Page Settings
  controls), and gave `PageSettingsSheet` a docked side-panel mode above a 600dp width breakpoint.
  `ToolType` was renamed to match Rnote's six pens (`BRUSH`/`SHAPER`/`TYPEWRITER`/`ERASER`/
  `SELECTOR`/`TOOLS`); Highlighter is no longer a separate tool — it's `BrushStyle.MARKER`,
  matching how desktop Rnote (and BRNA's own `.rnote` writer) actually represent it. Centralized
  the previously 8-file-duplicated color palette into `ui/theme/Colors.kt`. Also fixed two
  pre-existing bugs surfaced along the way: `nativeElements` (imported text/shapes/images) were
  silently dropped on every save/export despite the storage layer round-tripping them correctly,
  and dragging a selection was a complete no-op (moved strokes never reached the document, and an
  undo snapshot was pushed on every motion frame instead of once per drag).
- **Fixed `.rnote` files saved by BRNA failing to open in desktop Rnote.** The native serializer was writing a schema that only vaguely resembled the real one and desktop Rnote's Rust/serde deserializer rejects anything that doesn't match exactly (unlike our own lenient streaming parser). Verified against desktop Rnote's actual source (`flxzt/rnote`) and a real `.rnote` sample. Root causes, most severe first:
  - `PenPath` (every stroke's point data) was written as `{"elements":[...]}`. The real `PenPath` struct only has `start` + `segments` fields with no legacy alias — this alone caused deserialization to fail on the very first stroke, aborting the whole file load. Now writes `{"start":{...},"segments":[{"lineto":{"end":{...}}},...]}`.
  - Stroke `style` was tagged `"Smooth"` (capitalized); the real `Style` enum is externally tagged with lowercase variant names (`"smooth"`/`"rough"`/`"textured"`) — an unrecognized tag is a hard deserialize error.
  - `chrono_components` entries were written in an invented `{"stroke_key":{"index":N,"generation":1}}` shape instead of the real slotmap-style `{"value":{"t":N,"layer":...},"version":1}`.
  - `document` was missing required-shape `x`/`y`/`width` fields (only `height` was written); `engine_snapshot` was missing `camera` and `chrono_counter`; the file was missing the top-level `version` string.
  - Highlighter identity was invented as a nonexistent `"brush":{"BrushStyle":...}` field on the stroke. Desktop Rnote actually encodes it via the *chrono component's* `layer` field (`"highlighter"` vs `{"user_layer":0}`), which also means BRNA previously mis-detected highlighter strokes on **any** real desktop-authored file (always read as `false`). Both the serializer and parser now read/write highlighter status via that `layer` field.
  - Also fixed a pre-existing brace-mismatch bug in the shape-element writer (`appendShapeElement`) that produced invalid JSON whenever a shape/freehand native element was preserved through a save.
  - **The actual final blocker, found by bisecting real vs. generated files against a working desktop Rnote install:** `stroke_components`/`chrono_components` are backed by a `slotmap`-style collection whose slot 0 is a reserved sentinel — real `.rnote` files always carry a leading `{"value":null,"version":0}` placeholder and start real elements at index 1. Our serializer wrote real elements starting at index 0 with no placeholder, which breaks slot-key reconstruction even though every field name/shape was otherwise correct. Confirmed via controlled `.rnote` files built by hand and opened in the user's real Rnote install: identical stroke content opened fine once embedded at index 1 with a leading null slot, and failed every time at index 0. Fixed by always emitting the leading placeholder in both arrays and offsetting `t`/array position by 1 (`chrono_counter` = element count, matching the real format's convention of counting from the last used `t`, not a "next free slot" counter).
- **Fixed brand-new notes always saving as `.json` instead of `.rnote`.** `MainActivity.saveAsRnote` only ever got flipped to `true` inside `onDocumentLoaded`, when opening an existing native file — a fresh note created in-app had no way to trigger a `.rnote` save at all (there's no separate "Save as .rnote" menu item), so it silently wrote our internal `.json` format instead, which desktop Rnote's file picker won't even list. Default flipped to `true` since interop with desktop Rnote is the app's whole purpose; loading a `.json` file still correctly reverts the flag to `false` for round-tripping.
- Added brush size preset controls (S/M/L) and numerical size adjuster dialog to toolbar
- Fixed highlighter strokes losing translucency when saved as `.rnote`
- Fixed ColorPickerSheet slider always modifying pen width regardless of active tool
- Fixed SVG/PNG exporters using hardcoded 1920×1080 instead of document dimensions
- Fixed thread-unsafe static field in RnoteNativeParser
- Cleaned up unused imports and dead variables across 9 files
- Ported 3-section Page Settings Sheet (Page Format / Document / Display) matching desktop Rnote
- Added isometric grid and dot patterns
- Added measurement unit switching (px/mm/in)
- Added format border and origin indicator toggles
- Full .rnote v0.14 round-trip support (open → edit → save back)
