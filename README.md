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

Usable for handwriting and sketching, and for working on the same notes on a
desktop and a tablet. It's a personal project, not a finished product — a few UI
slots are disabled, grayed-out placeholders for parity with desktop Rnote's layout.

**Working**

- **Brush** with stylus pressure sensitivity, in Rnote's three styles: Solid,
  Marker (translucent) and Textured — dots strewn along the stroke, with Rnote's
  density and its four distributions. The dots come from the stroke's seed through
  a port of the random number generator and samplers Rnote uses (Pcg64, rand,
  rand_distr), so a textured stroke looks the same on both sides, dot for dot.
  Rnote's six pressure curves for Solid — constant, linear, square and cubic root,
  quadratic and cubic. Rnote's path modelling: by default the pen's samples go through
  a port of the stroke modeler Rnote uses (ink-stroke-modeler-rs, with Rnote's
  settings), which smooths out jitter and gives handwriting its curves; "Curved" draws
  Rnote's cubic curves through them, and "Simple" straight lines through the raw
  samples. Three size presets per tool plus a numeric adjuster, and
  four favorite slots that keep a style, color and width together.
- **Shaper**: line, arrow, rectangle, ellipse, and — built from lines, as Rnote
  builds them — 2D, 3D and single-quadrant coordinate systems and a grid. Rnote's
  shapes of several strokes too: polyline and polygon (a corner per stroke; put the
  pen down on the last corner again to finish), quadratic and cubic curves, and the
  ellipse through a point from its two foci. Rnote's constraints — 1:1, 3:2, the
  golden ratio, level and upright — with Ctrl to switch them while drawing. Lines
  and arrows can also snap to 15° steps. Rnote's two styles: Smooth, with its line
  styles — solid, dotted and three kinds of dashed — and straight or round line
  caps; and Rough, sketched as if by hand, with its seven fill styles (solid,
  hachure, zig-zag, zig-zag line, crosshatch, dots, dashed) and hachure angle. The
  wobble comes from the shape's seed through a port of roughr and the random number
  generator it uses (rand's ChaCha12), so a rough shape looks the same on both
  sides. The color picker's fill pad fills shapes.
- **Typewriter**: tap and type straight onto the page, into a new text box or
  an existing one. Bold, italic, underline and strikethrough — from the strip,
  or Ctrl+B / Ctrl+I / Ctrl+U on a keyboard — stored as Rnote stores them, so
  formatting made on either side shows on the other. Left, centred, right or
  justified, as Rnote aligns text.
- **Eraser**: Trash Strokes and Split Strokes modes. Like Rnote's, it erases ink
  and shapes and leaves text and images alone.
- **Selector**, for ink and for desktop text, shapes and images alike, in
  Rnote's four styles: lasso, rectangle, single (tap, tap again to add) and
  intersecting path. Select all / deselect / duplicate / delete, drag-to-move,
  scale and rotate handles with an aspect-ratio lock, copy / cut / paste between
  notes, recoloring the selection — its lines and text, or its shapes' fill — and
  Rnote's Invert Color Brightness, which turns its colors light for dark. Paste also
  takes what another app copied, as Rnote's does: a picture, a PDF (through the import
  dialog), a Xournal++ file, or text as a new text box; and the same can be dragged
  onto the note from another app in split screen, landing where it is let go.
- **Tools**: Rnote's Vertical Space — drag down to open up room, up to close it —
  Offset Camera (drag the page with the pen), Zoom (drag up to zoom in, down to zoom
  out, about where the drag began) and its Laser, a red trail to point with that
  fades away and is never saved.
- **Stylus-aware input**: stylus-only mode by default (finger pans and zooms),
  optional finger drawing, S-Pen Air
  Actions mapped to undo/redo, two-finger pinch-zoom and pan — the zoom blockable, as
  with Rnote's "Block Pinch to Zoom", so a resting hand can't. Every sample the pen
  reports is used, the ones Android batches between frames included, as Rnote uses
  them; the pen's events arrive unbuffered, and motion prediction draws the ink a
  little ahead of the pen, never saved. The finished note is drawn on a layer of its
  own and only as far as it is in view, so a long note writes like a short one.
- **Button shortcuts**, Rnote's, in Settings: the pen's two buttons, a mouse's right
  button, a two-finger long-press and Ctrl+Space each bring out a pen — Temporary,
  Permanent or Toggle, or Disabled — with Rnote's defaults: the S Pen's button erases
  while held, a second pen button selects, the right mouse button draws shapes, a
  two-finger long-press toggles the eraser and Ctrl+Space the Tools. A temporary pen
  lasts while its button is held, and a temporary selector or typewriter until its
  selection is let go or its text box left, as Rnote's does.
- **Pen sounds**, Rnote's own, switched on in the canvas menu as in Rnote: a pencil
  scratching while the brush draws, a squeak at each marker stroke, and a
  typewriter — with its bell for a new line — for the Typewriter.
- **Paper**: six patterns (dots, grid, lines, isometric grid, isometric dots,
  blank), A2–A6 / Letter / Legal / custom / infinite page sizes, four layout
  modes (fixed size, continuous vertical, semi-infinite, infinite), custom
  background and pattern colors, adjustable spacing and DPI,
  portrait/landscape, dark mode. A Fixed Size document has as many pages as Rnote
  gives it: Add Page, Remove Page and Resize to Fit Content in the canvas menu, and
  pages for an imported PDF. The isometric patterns stand on an upright edge,
  as Rnote draws them. Rnote's Snap Positions (menu, or Ctrl+Shift+P): shapes,
  moved and resized selections, new text and vertical space go to the pattern and
  the page edges. The full palette has GTK's Custom row: a color editor
  (saturation and value, hue, opacity, hex) whose colors are kept.
- **Files**: open and save native `.rnote` (gzipped engine-snapshot JSON) and a
  simpler app-native `.json`, including "Open with" from file managers and cloud
  drives. Desktop text, shapes, images and PDF pages are shown and editable, and
  keep the JSON they were read with, so attributes the app doesn't model survive
  a round trip. Desktop brush strokes do too: one that isn't changed here is
  written back byte for byte, and one that is — moved, scaled, recolored, cut
  with the eraser — keeps its curves, since the curve segments of Rnote's
  "Curved" pen path are read, drawn as Rnote draws them, hit by the eraser and the
  selector where the curve runs, and written back as curves. With Rnote's "Respect
  Borders When Pasting", an inserted image stays clear of the next page border. Autosave, crash recovery, a warning before overwriting a file
  that changed elsewhere (save a copy, overwrite, or load the other version) —
  judged by the file's content, so a sync that only touches its time is no
  alarm — a list of recent notes, and a page overview with thumbnails. Undo
  reaches back 100 steps, as in Rnote.
- **Tabs**: several notes open at once, as in Rnote, each with its own undo
  history and view. Opening a note gives it a tab (or shows its tab if it is
  open already); a tab being left is saved first, and one that can't be is kept
  in crash recovery, which brings every unsaved tab back. Ctrl+T / Ctrl+N,
  Ctrl+W, Ctrl+Tab / Ctrl+Shift+Tab.
- **Workspaces**: Rnote's workspace browser as a side panel — folders on the
  device or in Google Drive, each with a name and a color, listed as Rnote lists
  them. Open notes and Xournal++ files, drop PDFs and pictures into the open note, make new notes
  and folders, rename, duplicate and delete. When the open note was saved on
  another device meanwhile, returning to the app or opening the panel loads the
  newer version — or, with changes on both sides, asks what to keep. While the
  app is open it also looks every 30 seconds, and loads a newer version when
  nothing here is unsaved, so the tablet follows along as the laptop saves.
- **Import**: PDF pages, and pictures from the gallery or the camera, written
  the way desktop Rnote writes its own imports. PDFs through Rnote's import dialog:
  start and end page, the page width as a share of the format's, continuous or one
  per document page, or Adjust Document (the format becomes the PDF's page, Fixed
  Size); placed in the view and selected, as Rnote places them, the last choice kept. A Xournal++ `.xopp` opens as a new
  note, as Rnote opens one — its strokes with their pressure, highlighters, text and
  pictures, the pages one below the other — and is saved as an `.rnote` beside it,
  never over it.
- **Export, share and print**: PDF, SVG, PNG, and JPEG, with page-range and
  split options and one page per imported PDF page, and Xournal++ (`.xopp`) for the
  whole note as Rnote writes it: strokes stay strokes, shapes, text and pictures go in
  as pictures, one Xournal++ page for each page with something on it. A Share button sends the
  current page, the selection or the whole note to another app; Print sends
  every page, background and pattern included, to Android's print dialog. Copying
  a selection also puts a picture of it on Android's clipboard, as Rnote puts one on
  the desktop's, to paste into a document or a message.
- **View**: Rnote's canvas menu — zoom out, reset and in, Zoom to Page Width — its
  Focus Mode, which puts the pen picker, the colors and the pen settings away, and
  Fullscreen, which hides Android's bars.
- **Keyboard shortcuts** for a hardware keyboard, Rnote's own: Ctrl+Z / Ctrl+Shift+Z
  (and Ctrl+Y), Ctrl+S / Ctrl+Shift+S, Ctrl+O, Ctrl+N, Ctrl+P, Ctrl+Shift+I,
  Ctrl+L, Ctrl+Shift+O, Ctrl+Shift+P, Ctrl+Shift+A / Ctrl+Shift+R for pages, F11,
  Ctrl+C / X / V / A / D, Delete and Escape for the selection, Ctrl++ / Ctrl+- /
  Ctrl+0 to zoom, Ctrl+1 to Ctrl+6 for the pens.
  They follow the keyboard's layout, so Ctrl+Z is the Z key on a German
  keyboard too.

**Not built**: layers (the stroke list is flat).

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
- **Layers.** The document model is flat today, which makes them a bigger lift.
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
- **Pen sounds**, as Rnote ships them, from [freesound.org](https://freesound.org/):
  "Pencil, Writing, Close, A" by InspectorJ (CC BY 3.0); the marker sounds, and the
  typewriter's by KVProds and knufds, CC0. (Details in
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).)

## License

GPL-3.0. See [LICENSE](LICENSE) for the full text and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the borrowed material this
project includes and the terms it comes under.
