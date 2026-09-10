# Baby Rnote

An Android note-taking app, built in Kotlin and Jetpack Compose, that reads and
writes the `.rnote` files produced by [desktop Rnote](https://github.com/flxzt/rnote).

Rnote is a GTK4/Rust vector drawing app for Linux, and its UI cannot run on
Android. Baby Rnote does not embed the Rust engine — it reimplements the parts
that matter for interoperability (the v0.14 document schema, stroke geometry,
paper patterns, page layout) in pure Kotlin, so a file drawn on the desktop
opens on a tablet and a file drawn on the tablet opens back on the desktop.

> **Unofficial and unaffiliated.** This project is not endorsed by, connected
> to, or maintained by the Rnote project or its authors. The package name
> (`com.rnote.baby`), the app label, and the launcher artwork all borrow from
> Rnote — see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for exactly
> what is borrowed and under what terms. Please direct bugs here, not upstream.

## Status

Usable for handwriting and sketching. It's a personal project, not a finished
product — several tools in the UI are deliberate placeholders for parity with
desktop Rnote's layout, and are disabled rather than half-working.

**Working**

- **Brush** with stylus pressure sensitivity, in Solid and Marker (translucent)
  styles. Three size presets per tool plus a numeric adjuster.
- **Eraser** (whole-stroke), **Selector** (lasso), with select all / deselect /
  duplicate / delete, drag-to-move, and a bounding-box overlay.
- **Stylus-aware input**: stylus-only mode by default (finger pans and zooms),
  optional finger drawing, S-Pen barrel button as a momentary eraser, S-Pen Air
  Actions mapped to undo/redo, two-finger pinch-zoom and pan.
- **Paper**: six patterns (dots, grid, lines, isometric grid, isometric dots,
  blank), A2–A6 / Letter / Legal / custom / infinite page sizes, three layout
  modes (fixed page, continuous vertical, infinite 2D), custom background and
  pattern colors, adjustable spacing and DPI, portrait/landscape, dark mode.
- **Files**: open and save native `.rnote` (gzipped engine-snapshot JSON) and a
  simpler app-native `.json`. Format is auto-detected on load. Elements the app
  can't yet edit — text, shapes, bitmaps — are preserved as passthrough rather
  than dropped, so round-tripping a desktop file doesn't lose work.
- **Export**: PDF, SVG, PNG, and JPEG, with page-range and split options.

**UI slots with no implementation behind them** — visible but disabled: Shaper,
Typewriter, and Tools pens; the Textured brush style; the Split Strokes eraser
mode; the three non-polygon selector modes; separate fill color.

**Not built**: layers (the stroke list is flat), selection scale/rotate handles,
clipboard, document tabs.

## Building

Requires JDK 17 and the Android SDK (compileSdk 35). The app targets Android 8.0
(API 26) and up.

```bash
./gradlew assembleDebug
./gradlew installDebug   # to a connected device
```

`JAVA_HOME` must point at a JDK 17 or newer; Gradle will not find one on its own
if Java isn't on your `PATH`. Android Studio ships a suitable JDK:

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

```bash
# macOS / Linux, if you have Android Studio installed
export JAVA_HOME=/path/to/Android\ Studio/jbr
```

## Tests

```bash
./gradlew test
```

JVM unit tests only — no device or emulator needed. They cover the layers where
a mistake is a wrong *number* rather than a wrong pixel: stroke outline
geometry, pressure curves, eraser hit-testing, viewport math, and the `.rnote`
reader/writer.

The `tests/` directory holds real `.rnote` files written by desktop Rnote across
a range of page sizes and layouts. `RnoteFixtureTest` parses `tests/test.rnote`
directly from disk — round-trip tests only prove the reader and writer agree
with *each other*, so this is the one that proves they agree with the real
application. The other fixtures are there for manual import checks through the
app's Open menu.

Anything needing a live Compose runtime or `android.graphics` — canvas input
handling, background rendering, raster export — isn't covered and needs a
device.

## License

GPL-3.0. See [LICENSE](LICENSE) for the full text and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the Rnote-derived material
this project includes.
