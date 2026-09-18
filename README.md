# Basic Rnote Editor for Android

An Android note-taking app, built in Kotlin and Jetpack Compose, that reads and
writes `.rnote` files produced by [the open-source desktop application Rnote](https://github.com/flxzt/rnote).

This app does not currently embed Rnote's Rust engine — it reimplements the parts
that matter for interoperability (the v0.14 document schema, stroke geometry,
paper patterns, page layout) in pure Kotlin, so that a file drawn on the desktop
opens on a tablet and a file drawn on the tablet opens back on the desktop.

*Currently* is the operative word: the goal is to eventually run Rnote's real
Rust engine underneath this app instead of a Kotlin reimplementation of it. See
[Where this is heading](#where-this-is-heading).

> **Unofficial and unaffiliated.** This project is not endorsed by, affiliated with, or maintained by the Rnote project or its authors. The name and the
> launcher artwork both borrow from Rnote — see
> [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for exactly what is borrowed
> and under what terms. Please direct bugs here, not to them!

## Status

Usable for handwriting and sketching. It's a personal project, not a finished
product — several tools in the UI are disabled, grayed-out placeholders for parity with
desktop Rnote's layout.

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

## Screenshots

<img width="1600" height="2391" alt="Screenshot_20260917_170121_Basic Rnote" src="https://github.com/user-attachments/assets/78b9121f-6cd6-4f9b-aaf0-10cdb5bc41fe" />
<img width="1600" height="2375" alt="Screenshot_20260917_170142_Basic Rnote" src="https://github.com/user-attachments/assets/2162273e-0e09-4813-9edd-806a91ec21e4" />

## Where this is heading

The long-term plan is to implement Rnote's native Rust engine in the app's backend.

We believe this to be possible because Rnote's engine is already split into `rnote-compose`
(geometry and math) and `rnote-engine` (documents, strokes, rendering), and
neither crate depends on GTK — only the `rnote-ui` layer does. GTK4 is the
reason the desktop app can't run on Android; the engine underneath it has no
such problem, and should cross-compile for Android via `cargo-ndk`. See
[rnote#390](https://github.com/flxzt/rnote/issues/390) for thorough discussion
of Android support.

Note that there is no promise about timing here. This is a spare-time project and
the Kotlin backend is good enough for many use cases. If cross-compiling the engine is the sort of thing you enjoy,
this is the most ambitious thing on the roadmap — see
[Contributing](#contributing).

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

## Contributing

Contributions are highly encouraged — issues, pull requests, or just a note that
something is broken. This is a personal project built in spare time, so replies
may not be quick, but nothing here is closed off and no contribution is too
small.

Especially useful:

- **Format compatibility bugs.** If a file drawn in desktop Rnote opens wrong
  here — or a file saved here opens wrong there — that's the most valuable kind
  of report. Please attach the `.rnote` file; the format work is essentially
  reverse-engineered, so a real file that breaks it is worth more than a
  description of the breakage.
- **Testing on other hardware.** Development happens on a single Samsung tablet
  with an S-Pen. Stylus behaviour varies a lot between vendors, and pressure,
  hover, and barrel-button handling are all places where "works here" proves
  very little. Reports from other devices are useful even when everything works.
- **The disabled tools.** Shaper, Typewriter, Tools, the Textured brush style,
  the Split Strokes eraser, and selection scale/rotate handles all have UI slots
  wired up and waiting for an implementation. Layers are a bigger lift — the
  document model is flat today.
- **Cross-compiling Rnote's engine for Android.** The most ambitious item on the
  list, described under [Where this is heading](#where-this-is-heading). If you
  know your way around `cargo-ndk` and JNI, I'd love the help — or just the
  advice on whether the approach holds up.

A few practical notes: `./gradlew test` should pass before you open a PR —
the tests are JVM-only, no device or emulator needed. If you're changing how
`.rnote` files are read or written, please add a test backed by a real file
from desktop Rnote (the `tests/` directory holds the ones the existing tests
read) — that layer is where mistakes are quietest. And by contributing you
agree your work is licensed GPL-3.0, like the rest of the project.

If you're unsure whether an idea fits, open an issue and ask first. That's
always welcome.

## Credits

- **[Rnote](https://github.com/flxzt/rnote)** by flxzt and its contributors — the
  application this one aims to be compatible with, and the source of the file
  format, the icon set, and the launcher artwork. Uses GPL-3.0.
- **[rnoteviewer-android](https://github.com/Intranox/rnoteviewer-android)** by
  Intranox — this project's `.rnote` parser was ported and adapted from theirs.
  Working out how to stream the format is the hard part of reading it, and that
  groundwork was theirs — done and shared freely with an invitation to adapt it.
  (See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the details.)

## License

GPL-3.0. See [LICENSE](LICENSE) for the full text and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the borrowed material this
project includes and the terms it comes under.
