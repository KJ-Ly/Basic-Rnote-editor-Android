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
│   ├── ToolConfig.kt             # Active tool, brush sizes, BrushSizePreset enum
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
│   ├── components/
│   │   ├── FloatingToolBar.kt    # Bottom toolbar: tools, brush size presets, colors
│   │   ├── ColorPickerSheet.kt   # Full color palette + stroke width slider
│   │   ├── PageSettingsSheet.kt  # 3-section settings (Page Format / Document / Display)
│   │   └── RnoteTopBar.kt        # Top bar: title, undo/redo, zoom, menu
│   └── theme/
│       └── Theme.kt              # Material3 dark theme
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
- **Pen tool** with pressure-sensitive stroke width (S-Pen / stylus)
- **Highlighter tool** with translucent blending
- **Eraser tool** (proximity-based stroke deletion)
- **Lasso selection tool** (ray-casting polygon containment, 25% threshold)
  - Selected strokes can be dragged/translated
  - Bounding box overlay with dashed border
  - Delete selection button in toolbar
- **Brush size controls**: 3 presets (S/M/L) per tool + numerical adjuster dialog with slider and direct input
- **Ink smoothing**: Quadratic Bézier midpoint interpolation (InkSmoother.kt)
- **Hover cursor**: Stylus hover preview (pen circle or eraser ring)

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
1. **Selection transformation handles** — Corner scaling, rotation handle, color picker for selected strokes, duplicate button. The `SelectionManager` already has `scaleStrokes()` but no UI handles.
2. **Shape tools** — Lines, arrows, rectangles, ellipses, freehand auto-shape recognition. The native document model (`RnoteNativeDocument.kt`) already defines `NativeShapeElement` variants.
3. **Multi-layer support** — Desktop Rnote has layers with visibility/lock/reorder. Our model is flat (single stroke list).
4. **Text tool** — `NativeTextElement` is modeled but not editable.

### Medium Priority
5. **Brush path models** — Desktop Rnote has 3 stroke renderers: Simple Polyline, Curved Bézier, Modeled 2D Outline Mesh. We currently use quadratic Bézier midpoint interpolation only.
6. **Undo/redo for selection transforms** — Currently only stroke add/erase/clear are undoable.
7. **Palm rejection** — No explicit palm rejection beyond stylus-only mode.
8. **Zoom-to-fit / Zoom-to-selection**

### Lower Priority / Aspirational
9. **Rust engine integration via NDK** — `RnoteNativeBridge.kt` has JNI stubs for `librnote_engine_android.so`. Desktop Rnote's engine is split into `rnote-compose` (math) and `rnote-engine` (document/strokes), which are GTK-free Rust crates. These could theoretically be cross-compiled for Android via `cargo-ndk`. See [rnote#390](https://github.com/flxzt/rnote/issues/390) for context — GTK4 UI cannot run on Android, but the engine crates have no GTK dependency.
10. **PDF import/export**
11. **Clipboard support** (copy/paste strokes)
12. **Document tabs / file browser**

---

## Known Quirks & Design Decisions

- **`PageSize.INFINITE` vs `LayoutMode.INFINITE`**: Legacy overlap. `PageSize.INFINITE` was the original way to represent infinite canvas before `LayoutMode` was added. `PaperBackgroundRenderer` checks `pageSize.isInfinite` to decide between paged and infinite rendering. These should eventually be unified.

- **`Stroke.toolType` vs `Stroke.isHighlighter`**: Redundant boolean. Both exist because `isHighlighter` was added before `toolType`. They're kept in sync at creation time but could theoretically diverge.

- **`StrokePoint.timestamp` returns hardcoded 0L**: Points don't carry timestamps. The field exists as a compatibility shim.

- **Export dimensions**: SVG and PNG exporters now use `effectivePageWidthPx`/`effectivePageHeightPx` from the document's paper style, with a floor of 1920×1080 to avoid tiny exports on infinite canvas mode.

- **Color picker sheet**: The full `ColorPickerSheet` is a legacy component from before the brush size presets were added to the toolbar. It still works but overlaps with the toolbar's inline controls.

---

## Build & Run

```bash
# Set JAVA_HOME to Android Studio's bundled JBR
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

# Compile check
.\gradlew.bat compileDebugKotlin

# Build debug APK
.\gradlew.bat assembleDebug

# Install to connected device
.\gradlew.bat installDebug
```

**Min SDK**: Check `app/build.gradle.kts` for `minSdk` value.
**Target**: Jetpack Compose with Material3.
**No native libraries required** — pure Kotlin/Compose (the JNI bridge is stubbed).

---

## Test File

`test.rnote` in the project root is a v0.14 format file from desktop Rnote that can be used to verify import compatibility. Open it via the app's "Open" menu item.

---

## Recent Changes (August 2026)

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
