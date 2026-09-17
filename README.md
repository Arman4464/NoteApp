# NoteApp — Phase 2 (Styling, Images, Checklists, Autosave, High-Quality Export)

A native Android (Kotlin) student note-taking app in the style of
Milanote/Canvio. Everything below runs with **no special permissions** —
image picking uses the system photo picker, and exports are saved to the
app's own storage + shared via the system share sheet.

## What's in this build

**Core (phase 1)**
- Infinite canvas — pinch-zoom, drag-pan, a 20,000×20,000px world.
- Boxes — drag the purple header to move, corner handle to resize.
- Connectors — tap the share-icon FAB to enter "connect mode," then tap two
  boxes to draw an arrow between them (follows the boxes automatically).

**New in this build**
- **Text styling** — tap into any text box and a formatting bar appears
  above the keyboard: font size (A-/A+), text color, highlight/background
  color, font family (cycles sans-serif → serif → monospace → cursive),
  bold, italic.
- **Box colors** — tap the ⋮ menu on any box's header → "Change color" to
  recolor the card itself. New boxes also auto-cycle through a pastel
  palette, like Milanote's colored cards.
- **Image insertion** — long-press the ➕ FAB → "Image" to pick a photo
  from your gallery. It's copied into the app's storage and becomes a
  draggable/resizable image box.
- **Checklists** — long-press the ➕ FAB → "Checklist" for a to-do-style
  box: tap "+ Add item" for a new checkbox row, tap ✕ to remove one,
  checking an item strikes through its text.
- **Delete** — ⋮ menu on any box → "Delete."
- **Autosave** — the whole board (boxes, positions, colors, text, images,
  checklists, connectors) is saved to a local JSON file and restored the
  next time you open the app, so nothing is lost between sessions.
- **High-quality export** — the green save-icon FAB (top-right) exports the
  whole board as a **PNG** or a **PDF**. This is the part built specifically
  around your "quality so tiny bits are visible" requirement: instead of
  taking a screenshot and stretching it, the export re-draws every box's
  actual text/shapes at ~3x scale (capped to avoid huge-file crashes), so
  small handwriting-sized text stays sharp instead of blurring out. Saved
  files land in the app's own folder
  (`Android/data/com.noteapp.student/files/Pictures` or `.../Documents`)
  and a share sheet opens immediately so you can send it to Drive, email,
  WhatsApp, etc.

## Still not in (next candidates)

- Multiple boards/pages (right now it's one continuous canvas).
- Multi-select / group-move of several boxes at once.
- Undo/redo.
- Bundled custom fonts (the "cursive" style currently relies on whatever
  cursive-ish system font the device ships with — looks fine on most
  phones, but isn't a guaranteed specific typeface).

## How to build the APK

1. Install **Android Studio** (Giraffe/Hedgehog or newer) if you don't
   have it.
2. Open this folder (`NoteApp/`) as a project: *File > Open*.
3. Let it sync — first sync may take a few minutes. If it prompts to
   upgrade the Android Gradle Plugin or Gradle wrapper, accepting is fine.
4. Plug in your phone (USB debugging on) or start an emulator, then click
   the green ▶ **Run** button.
5. For a standalone `.apk` to share: **Build > Build Bundle(s)/APK(s) >
   Build APK(s)**. It lands in `app/build/outputs/apk/debug/app-debug.apk`.

If Android Studio complains about a missing Gradle wrapper jar, use
*File > Sync Project with Gradle Files* to regenerate it.

## Project layout

```
app/src/main/java/com/noteapp/student/
  MainActivity.kt                 — toolbar wiring, image picker, export flow, autosave
  canvas/CanvasModel.kt           — NoteBoxData (kind/text/colors/font/checklist), ConnectorData
  canvas/CanvasSerializer.kt      — JSON save/load for the whole board
  canvas/InfiniteCanvasView.kt    — pan/zoom container + box/connector management
  canvas/NoteBoxView.kt           — one box: header/menu, resize, content by kind, styling
  canvas/ConnectorOverlayView.kt  — draws the on-screen arrows between linked boxes
  export/ExportManager.kt         — high-res bitmap render + PNG/PDF save + share
  util/ColorPicker.kt             — small swatch-grid color picker dialog
```

## A note on the "infinite" canvas

True unbounded canvases aren't practical with plain Android views (touch
delivery needs a finite content size), so this uses a very large fixed
world — 20,000 × 20,000 px, hundreds of screens in every direction from
where you start. For real note-taking use this is effectively infinite;
if you ever actually run out of room, the fix is to dynamically grow the
world size, which is a small, isolated change to `InfiniteCanvasView`.
