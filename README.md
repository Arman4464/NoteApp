# NoteApp — Infinite Creative Workspace for Students & Visual Thinkers

A native Android (Kotlin) visual thinking and note-taking app inspired by Milanote, Miro, and Canvio. Designed for organizing ideas, research, mind-mapping, and study notes on an expansive, lag-free infinite canvas.

Built entirely with standard Android SDK components, hardware-accelerated vector graphics, and Android Storage Access Framework (SAF) — **zero invasive permissions required**.

---

## Key Features

### 1. Infinite Canvas & High-Performance Engine
- **24,000 × 24,000 px Workspace**: Seamless, borderless creative surface.
- **Smooth Pinch-Zoom & Pan**: Zoom from 15% to 400% with hardware-accelerated matrix transforms.
- **Batched GPU Grid Rendering**: Dot and line grids batched into single-pass GPU draw calls (`drawPoints`/`drawLines`), rendering thousands of grid points at a steady 60–120 FPS.
- **Zero-Lag Inking & Card Manipulation**: Cached vector ink paths, selective connector invalidation, and debounced touch dispatch ensure lag-free performance even on dense boards with hundreds of elements.
- **Zoom HUD & Fit-to-Screen**: Real-time zoom level HUD with single-tap Zoom-to-Fit for instant framing of all board content.
- **Customizable Canvas Backgrounds**: Switch between Modern Dot Grid, Standard Graph Grid, Isometric Grid, or Blank Canvas.

### 2. Flexible Cards & Content System
- **Text Notes**: Rich typography with bundled fonts (*Outfit, Caveat, Inter, JetBrains Mono, Playfair Display*), font size scaling (A-/A+), bold, italic, text color, and highlight swatches.
- **Sticky Notes**: Tactile colored sticky notes in warm pastel palettes with handwritten Caveat script.
- **Interactive Checklists**: To-do list cards with checkbox strike-through and progress calculation.
- **Responsive Dynamic Tables**: 
  - Dynamic cell scaling that stretches rows and columns proportionally to card bounds without dead space.
  - Multi-line text wrapping with auto-expanding row heights (zero text clipping).
  - Auto-expanding card bounds when adding rows/columns to guarantee visibility, plus quick +/- row & column controls.
- **Image Cards**: Import photos directly via the Android Photo Picker with aspect-ratio preservation and scaling.
- **Sub-Boards**: Embed child boards inside cards for hierarchical multi-level workspaces.

### 3. Connectors & Directional Arrows
- **Card-to-Card Dynamic Connectors**: Smart quadratic bezier curves that dynamically anchor to card boundaries and track movement.
- **Free-Floating Canvas Arrows**: Draw straight arrows anywhere on the canvas.
- **6 Symmetrical Head & Tail Styles**:
  - Tail: None (`—`), Arrow (`◀`), Open Barb (`<`), Dot (`●`), Diamond (`◆`), Bar (`|`)
  - Head: Arrow (`▶`), Open Barb (`>`), Dot (`●`), Diamond (`◆`), Bar (`|`), None (`—`)
- **Dual Z-Order Arrow Layers**: Place connectors either behind cards (Background) or over cards (Foreground).

### 4. Vector Drawing & Smart Eraser
- **Dual-Layer Vector Inking**: Sketch either behind cards (Background) or directly on top of cards, notes, and photos (Foreground) using a zero-elevation hardware-safe rendering pipeline.
- **Multiple Stroke Sizes & Custom Palette**: Fine (3px), Medium (7px), Bold (14px), and Highlighter (28px with opacity).
- **Multi-Target Smart Eraser**:
  - **Pen Ink Only**: Erases ink strokes without touching connectors or cards.
  - **Arrows Only**: Erases free-floating arrows and card connectors with precise geometric hit-testing.
  - **All**: Erases both ink and arrows simultaneously.
- **One-Tap Cleanups**: Clear all ink drawings or clear all arrows with safe confirmation dialogs.

### 5. Multi-Select & Selection Transform Overlay
- **Single-Tap Inspection**: Single-tap any card to select it with an 8-handle transform frame without accidentally summoning the virtual keyboard.
- **Double-Tap Editing**: Double-tap into any card to activate text editing and the formatting toolbar.
- **Lasso Marquee Selection**: Drag across the empty canvas in Select mode to select multiple cards at once for batch moving, recoloring, duplicating, or deletion.
- **Alignment & Distribution**: Align selected cards Left, Top, or distribute them evenly horizontally.

### 6. Living Radar Minimap & Ceiling Search
- **Live Minimap Radar**: Top-corner radar preview displaying card positions and live viewport rectangle. Tap anywhere on the radar to teleport instantly.
- **Ceiling Search & Jump**: Fast text indexing across notes, checklists, table cells, and board links with automatic camera jump and pulse highlight.

### 7. Theming Engine & Board Sub-Themes
- **4 Global App Themes**:
  - Modern Clean (Light)
  - Milanote Dark
  - Warm Parchment
  - Cyberpunk Neon
- **Dynamic Launcher Icons**: Automatically switches the app's home screen icon to match the selected global theme via Android activity-aliases.
- **8 Dual-Mode Board Sub-Themes**: Every board can have its own palette (Light and Dark variants):
  - Oceanic Blue, Emerald Forest, Cyberpunk Neon, Vintage Parchment, Sunset Amber, Royal Purple, Rose Gold, Obsidian Stealth.
- **Themed Home Cards**: Board preview cards on the home screen dynamically reflect each board's own individual subtheme.

### 8. Gamified Interactive Onboarding
- **13 Interactive Training Quests**: Guided walkthrough covering canvas navigation, notes, formatting, elements, inking, eraser modes, connectors, lasso selection, minimap teleportation, ceiling search, subthemes, and pro exporting.
- **Tactile Progression**: Pulsing target highlights, lore explanations (*"What It Does"*), mission objectives (*"Your Mission"*), animated completion banners, and minimization support.

### 9. Pro-Grade Export & Project Sharing
- **High-Resolution 3x Oversampled PNG**: Renders the complete canvas (background connectors, background ink, notes, foreground connectors, foreground ink) at high DPI with bicubic filtering.
- **Vector PDF Documents**: Exports multi-element boards to crisp PDF documents.
- **Portable Project Archives (`.noteapp`)**: Full lossless zip archive preserving metadata, JSON board state, subthemes, and embedded images.
- **Import Support**: Opens `.noteapp` files directly via file managers, downloads, or Android system share sheets (`ACTION_VIEW` and `ACTION_SEND`).
- **Storage Access Framework (SAF)**: Pick custom destination folders and filenames for downloads.

---

## Building & Running

### Requirements
- Android Studio Ladybug / Hedgehog (or newer)
- Android SDK 34+ (Build Tools 34.0.0+)
- JDK 17
- Gradle 8.2+

### Quick Build

```powershell
# Build Release APK (Production, unsigned or release keystore)
.\gradlew.bat assembleRelease

# The output APK will be generated at:
# app/build/outputs/apk/release/app-release.apk
```

---

## Architecture Overview

```
app/src/main/java/com/noteapp/student/
├── MainActivity.kt                      # Main activity, screen routing, toolbar coordination, SAF & share intents
├── canvas/
│   ├── InfiniteCanvasView.kt            # Core infinite canvas: 24,000px contentLayer, gestures, tool dispatching
│   ├── CanvasModel.kt                   # NoteBoxData, ConnectorData, BoardData, BoardMeta, TableData
│   ├── CanvasSerializer.kt              # JSON serialization and migration
│   ├── BoardManager.kt                  # Multi-board storage, manifest persistence, playground initialization
│   ├── NoteBoxView.kt                   # Individual card view: text, checklist, table, image, shapes, handles
│   ├── ConnectorOverlayView.kt          # On-screen & export renderer for connectors, arrows, and endpoint styles
│   ├── DrawingOverlayView.kt            # Vector pen inking and eraser overlay
│   ├── MinimapView.kt                   # Interactive radar minimap overlay
│   ├── MarqueeOverlayView.kt            # Lasso multi-select rectangle overlay
│   └── SelectionTransformOverlayView.kt # MS Paint-style 8-handle bounding frame
├── export/
│   └── ExportManager.kt                 # 3x oversampled PNG, PDF, and .noteapp zip archive export & import
├── home/
│   ├── BoardGridAdapter.kt              # Home screen board grid adapter with subtheme card rendering
│   └── BoardThumbnailMinimapView.kt     # Live card thumbnail minimap for home cards
├── settings/
│   ├── AppSettings.kt                   # Global preferences, autosave, theme settings
│   ├── AppIconManager.kt                # Activity-alias launcher icon switcher
│   ├── BoardSubTheme.kt                 # 8 dual-mode board color palettes
│   └── ThemeColors.kt                   # Color definitions and palette resolvers
├── tutorial/
│   ├── GameTutorialManager.kt           # 13-quest state machine, action hooks, completion locking
│   └── GameTutorialHudView.kt           # Floating interactive quest HUD with minimize & victory states
└── util/
    ├── ColorPicker.kt                   # Palette swatch picker dialog
    └── ThemedDialog.kt                  # Standardized dialog system respecting active themes
```
