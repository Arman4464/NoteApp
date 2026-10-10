# The `.noteapp` File Format Specification & Architecture Guide

A comprehensive technical reference for the `.noteapp` project archive format used by **NoteApp for Android**. This document covers container architecture, internal schemas, coordinate spaces, color representation, asset bundling, and serialization lifecycles.

---

## 1. Overview & Design Philosophy

The `.noteapp` file format is an open, portable, self-contained project archive designed to encapsulate an entire infinite canvas workspace—including notes, shapes, tables, checklists, images, directional connector arrows, freehand ink vectors, camera states, and palette metadata.

### Core Principles
1. **Self-Contained & Zero Dependency**: All external assets (imported images, page renders, photos) are bundled directly inside the archive. A `.noteapp` file can be transferred via WhatsApp, Google Drive, email, or USB without breaking asset links.
2. **Human-Readable Schema**: Canvas metadata and layout geometries are serialized as standard JSON (`board.json`), making programmatic generation, transformation, and inspection trivial in any programming language (Python, Node.js, Go, Rust, Kotlin).
3. **High-Performance Spatial Geometry**: Geometries operate in an expansive **64,000 × 64,000 pixel** continuous coordinate universe with native hardware-accelerated viewport rendering.
4. **Lossless Undo & State Preservation**: Preserves element Z-indexes, position locks (`isLocked`), font typography, table cell dimensions, connector arrowhead/tail topologies, and dual-layer ink order.

---

## 2. Container & Archive Structure

A `.noteapp` file is physically a standard **PKZip (ZIP) archive** utilizing DEFLATE compression.

```
my_board.noteapp (ZIP Archive, Magic Bytes: 0x50, 0x4B)
├── board.json                 # Primary canvas state, metadata, and geometry
└── images/                    # Bundled raster image assets
    ├── img_1.png              # Referenced by box.imagePath
    ├── img_2.jpg
    └── page_1.png
```

### Magic Bytes & MIME Identification
- **Zip Magic Bytes**: `0x50 0x4B 0x03 0x04` (`PK\x03\x04`)
- **File Extension**: `.noteapp`
- **MIME Types Handled**:
  - `application/x-noteapp`
  - `application/zip`
  - `application/x-zip-compressed`
  - `application/octet-stream`

> **Legacy Fallback**: If NoteApp encounters a `.noteapp` file whose first two bytes are *not* `PK`, it transparently parses the file as raw UTF-8 JSON. However, all modern exports produce the compressed ZIP container to support embedded images.

---

## 3. The 64,000 × 64,000 Pixel Coordinate System

NoteApp utilizes a fixed, bounded **64,000 × 64,000 px world coordinate space**:
- **Coordinate Range**: `X ∈ [0.0, 64000.0]`, `Y ∈ [0.0, 64000.0]`
- **Workspace Center Anchor**: `(X = 32000.0, Y = 32000.0)`
- **Boundary Border**: The perimeter of the active canvas is outlined by a 3.5dp high-contrast border. Area outside `[0, 64000]` is rendered as a dark/slate deep-space void.
- **Card Placement Best Practice**: Programmatically generated cards and imports should be centered around `(32000, 32000)` so that initial camera views immediately frame the content.

### Viewport Transformation Formula
Screen coordinates $(S_x, S_y)$ relate to canvas world coordinates $(W_x, W_y)$ through camera translation $(T_x, T_y)$ and zoom scale $s$:

$$S_x = T_x + W_x \cdot s$$
$$S_y = T_y + W_y \cdot s$$

Conversely:

$$W_x = \frac{S_x - T_x}{s}, \quad W_y = \frac{S_y - T_y}{s}$$

---

## 4. Color Representation (Signed 32-bit ARGB)

Colors in `.noteapp` are stored as **32-bit signed two's-complement ARGB integers** (matching standard Android `Color.parseColor` and `Color.argb`).

### Converting Hex to ARGB Integer
Given an 8-character hex `#AARRGGBB` (or 6-character `#RRGGBB` with alpha `FF`):

$$\text{unsigned} = (\text{Alpha} \ll 24) \mid (\text{Red} \ll 16) \mid (\text{Green} \ll 8) \mid \text{Blue}$$
$$\text{signed} = \begin{cases} \text{unsigned} - 2^{32} & \text{if } \text{unsigned} \ge 2^{31} \\ \text{unsigned} & \text{otherwise} \end{cases}$$

#### Python Conversion Function
```python
def hex_to_argb_int(hex_str: str) -> int:
    clean = hex_str.lstrip('#')
    if len(clean) == 6:
        val = int('FF' + clean, 16)
    elif len(clean) == 8:
        val = int(clean, 16)
    else:
        val = 0xFFFFFFFF
    if val >= 0x80000000:
        val -= 0x100000000
    return val
```

#### Color Reference Cheat-Sheet
| Appearance | Hex Color | Signed Integer | Usage |
| :--- | :--- | :--- | :--- |
| **Pure White** | `#FFFFFFFF` | `-1` | Default card background |
| **Dark Slate** | `#FF111827` | `-15658735` | Default dark text |
| **Muted Slate** | `#FF64748B` | `-10193781` | Secondary captions |
| **Indigo Accent** | `#FF6366F1` | `-10262799` | Connectors & arrows |
| **Sticky Yellow** | `#FFFEF08A` | `-69494` | Sticky note background |
| **Soft Blue** | `#FFDBEAFE` | `-2364674` | Concept cards |
| **Soft Green** | `#FFDCFCE7` | `-2302745` | Checklist cards |
| **Border Slate** | `#FFC7C9F2` | `-3683854` | Default card stroke |
| **Amber Warning** | `#FFF59E0B` | `-680437` | Locked card indicator |
| **Transparent** | `#00000000` | `0` | Text background default |

---

## 5. `board.json` Complete Schema Specification

The `board.json` file at the root of the archive describes all canvas elements and configuration.

### Root Object

| Property | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `meta` | `Object` | Yes | Board metadata, timestamps, and sub-theme |
| `panX` | `Float` | Yes | Camera translation X in screen pixels |
| `panY` | `Float` | Yes | Camera translation Y in screen pixels |
| `scale` | `Float` | Yes | Camera zoom level (`0.15` to `4.0`, default `1.0`) |
| `boxes` | `Array<NoteBoxData>` | Yes | Array of card elements |
| `connectors` | `Array<ConnectorData>` | Yes | Array of directional connectors & arrows |
| `strokes` | `Array<DrawingStrokeData>` | Yes | Background freehand vector ink strokes |
| `fgStrokes` | `Array<DrawingStrokeData>` | Yes | Foreground freehand vector ink strokes |

---

### `meta` Object

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Machine Learning Architecture",
  "createdAt": 1727376000000,
  "updatedAt": 1727376000000,
  "parentId": null,
  "subThemeId": "oceanic_blue",
  "subThemeIsDark": false
}
```

- `id` (`String`): UUID identifying the board.
- `name` (`String`): User-visible board title.
- `createdAt` / `updatedAt` (`Long`): Epoch timestamps in milliseconds.
- `parentId` (`String?`): ID of parent board if this board is nested as a sub-board.
- `subThemeId` (`String?`): Individual board sub-theme palette. Supported values:
  - `"oceanic_blue"`, `"emerald_forest"`, `"cyberpunk_neon"`, `"vintage_parchment"`
  - `"sunset_amber"`, `"royal_purple"`, `"rose_gold"`, `"obsidian_stealth"`
- `subThemeIsDark` (`Boolean?`): `true` for dark variant, `false` for light variant, `null` for global theme inheritance.

---

### `boxes` Array (`NoteBoxData`)

Represents interactive cards on the canvas.

```json
{
  "id": "e4b9d7c0-1234-5678-9abc-def012345678",
  "x": 31750.0,
  "y": 31800.0,
  "width": 300.0,
  "height": 200.0,
  "kind": "TEXT",
  "shapeType": "ROUNDED_RECT",
  "targetBoardId": null,
  "targetBoardName": null,
  "text": "Core Principles\n\nGradient descent minimizes loss function.",
  "textColor": -15658735,
  "textBgColor": 0,
  "boxColor": -1,
  "strokeColor": -3683854,
  "strokeWidth": 3.0,
  "fontSizeSp": 15.0,
  "fontFamily": "outfit",
  "bold": false,
  "italic": false,
  "imagePath": null,
  "zIndex": 0,
  "isLocked": false,
  "checklist": [],
  "tableData": null
}
```

#### Field Details

- `kind` (`String`): The card classification.
  - `"TEXT"`: Standard note card with typography formatting.
  - `"IMAGE"`: Picture card displaying the image at `imagePath`.
  - `"CHECKLIST"`: Interactive to-do task list with checkable items.
  - `"TABLE"`: Multi-column, multi-row spreadsheet table grid.
  - `"SHAPE"`: Geometric containers, sticky notes, and badges.
  - `"BOARD"`: Sub-board card linking to child board specified by `targetBoardId`.
  - `"LINK"`: Web bookmark URL card.
- `shapeType` (`String`): Shape geometry used when `kind == "SHAPE"`.
  - `"ROUNDED_RECT"` (Default container)
  - `"RECTANGLE"` (Sharp corners)
  - `"STICKY_NOTE"` (Tactile sticky note with bottom-right corner fold)
  - `"CIRCLE"` (Bubble/decision node)
  - `"DIAMOND"` (Flowchart decision node)
  - `"STAR"` (Milestone highlight badge)
  - `"CLOUD"` (Thought / brainstorming bubble)
  - `"TRIANGLE"` (Warning / indicator shape)
- `fontFamily` (`String`): Bundled custom typography:
  - `"outfit"`: Modern geometric sans-serif (Clean & neutral)
  - `"caveat"`: Natural handwritten script (Ideal for sticky notes)
  - `"kalam"`: Calligraphic ink script
  - `"lora"`: Literary editorial serif
  - `"playfair"`: High-contrast elegant display serif
  - `"spacemono"`: Tech monospace (Code & data)
  - `"sans-serif"`, `"serif"`, `"monospace"`: System fallbacks
- `imagePath` (`String?`): Relative archive path (`"images/filename.png"`). During import, NoteApp automatically extracts the asset to internal app storage and remaps this path to an absolute path.
- `isLocked` (`Boolean`): When `true`, card position is locked. It displays an amber indicator border (`#F59E0B`), suppresses transform handles, and cannot be moved or resized.

---

### Nested `checklist` Array

Used when `kind == "CHECKLIST"`.

```json
[
  {
    "id": "c1a2b3c4-0001",
    "text": "Review architecture proposal",
    "checked": true
  },
  {
    "id": "c1a2b3c4-0002",
    "text": "Deploy release APK to testing track",
    "checked": false
  }
]
```

---

### Nested `tableData` Object

Used when `kind == "TABLE"`. Supports proportional column/row expansion, multiline wrapping, and custom indices.

```json
{
  "rows": 2,
  "cols": 3,
  "rowHeaders": "NUMBERS",
  "colHeaders": "LETTERS",
  "customColLabels": ["Parameter", "Value", "Unit"],
  "customRowLabels": ["1", "2"],
  "cells": [
    ["Learning Rate", "0.001", "Dimensionless"],
    ["Batch Size", "64", "Samples"]
  ]
}
```

- `rowHeaders` / `colHeaders` (`String`):
  - `"NUMBERS"`: 1, 2, 3...
  - `"LETTERS"`: A, B, C...
  - `"ROMAN"`: I, II, III...
  - `"NONE"`: Headers hidden
- `cells` (`Array<Array<String>>`): Outer array represents rows, inner array represents column cells.

---

### `connectors` Array (`ConnectorData`)

Represents directional links between cards or free-floating canvas arrows.

```json
{
  "id": "conn-9876-5432-10fe",
  "fromId": "box-uuid-1",
  "toId": "box-uuid-2",
  "startX": 32100.0,
  "startY": 32000.0,
  "endX": 32400.0,
  "endY": 32000.0,
  "color": -10262799,
  "strokeWidth": 5.0,
  "style": "arrow",
  "headStyle": "triangle",
  "tailStyle": "none",
  "isForeground": false
}
```

- `fromId` / `toId` (`String`):
  - If both IDs match existing boxes: A dynamic curved card-to-card link that tracks box movement and anchors to card edges.
  - If either ID is empty (`""`): A free-floating vector arrow drawn between `(startX, startY)` and `(endX, endY)`.
- `headStyle` & `tailStyle` (`String`): Symmetrical endpoint styling.
  - `"none"`: Plain line termination (`—`)
  - `"triangle"`: Filled directional triangle (`▶` / `◀`)
  - `"open"`: Open chevron barb (`>` / `<`)
  - `"dot"`: Terminal circle bead (`●`)
  - `"diamond"`: Flowchart decision diamond (`◆`)
  - `"bar"`: Orthogonal stop bar (`|`)
- `isForeground` (`Boolean`):
  - `false`: Rendered in Background Layer (under cards and text notes).
  - `true`: Rendered in Foreground Layer (over cards and photos).

---

### `strokes` and `fgStrokes` Arrays (`DrawingStrokeData`)

Contains vector freehand pen and highlighter paths. `strokes` renders underneath cards; `fgStrokes` renders on top of cards.

```json
{
  "id": "stroke-001",
  "color": -680437,
  "width": 14.0,
  "isHighlighter": true,
  "points": [
    [31800.0, 31920.0],
    [31850.0, 31922.0],
    [31920.0, 31918.0],
    [32010.0, 31925.0]
  ]
}
```

- `points` (`Array<[Float, Float]>`): Chronological stream of world-space coordinates smoothed via quadratic Bezier interpolation.
- `isHighlighter` (`Boolean`): When `true`, renders with translucent alpha (110/255) and square bevel caps.

---

## 6. Complete Annotated `board.json` Example

Here is a complete, valid `board.json` demonstrating all primary element types:

```json
{
  "meta": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Project Roadmap & Architecture",
    "createdAt": 1727376000000,
    "updatedAt": 1727376000000,
    "parentId": null,
    "subThemeId": "oceanic_blue",
    "subThemeIsDark": false
  },
  "panX": 420.0,
  "panY": 280.0,
  "scale": 1.0,
  "boxes": [
    {
      "id": "card-header",
      "x": 31700.0,
      "y": 31600.0,
      "width": 600.0,
      "height": 110.0,
      "kind": "TEXT",
      "shapeType": "ROUNDED_RECT",
      "targetBoardId": null,
      "targetBoardName": null,
      "text": "🚀 Q4 Delivery Architecture\nCore Milestone Roadmap & Tasks",
      "textColor": -15658735,
      "textBgColor": 0,
      "boxColor": -2364674,
      "strokeColor": -10262799,
      "strokeWidth": 3.0,
      "fontSizeSp": 18.0,
      "fontFamily": "outfit",
      "bold": true,
      "italic": false,
      "imagePath": null,
      "zIndex": 0,
      "isLocked": true,
      "checklist": []
    },
    {
      "id": "card-sticky",
      "x": 31700.0,
      "y": 31750.0,
      "width": 260.0,
      "height": 220.0,
      "kind": "SHAPE",
      "shapeType": "STICKY_NOTE",
      "targetBoardId": null,
      "targetBoardName": null,
      "text": "💡 Key Insight\nRemember to test SAF URI streaming before release!",
      "textColor": -15658735,
      "textBgColor": 0,
      "boxColor": -69494,
      "strokeColor": -3683854,
      "strokeWidth": 3.0,
      "fontSizeSp": 16.0,
      "fontFamily": "caveat",
      "bold": false,
      "italic": false,
      "imagePath": null,
      "zIndex": 1,
      "isLocked": false,
      "checklist": []
    },
    {
      "id": "card-tasks",
      "x": 32020.0,
      "y": 31750.0,
      "width": 280.0,
      "height": 220.0,
      "kind": "CHECKLIST",
      "shapeType": "ROUNDED_RECT",
      "targetBoardId": null,
      "targetBoardName": null,
      "text": "Sprint Checklist",
      "textColor": -15658735,
      "textBgColor": 0,
      "boxColor": -1,
      "strokeColor": -3683854,
      "strokeWidth": 3.0,
      "fontSizeSp": 14.0,
      "fontFamily": "outfit",
      "bold": false,
      "italic": false,
      "imagePath": null,
      "zIndex": 2,
      "isLocked": false,
      "checklist": [
        { "id": "t1", "text": "Implement 64k canvas expansion", "checked": true },
        { "id": "t2", "text": "Verify direct geometric touch routing", "checked": true },
        { "id": "t3", "text": "Compile release APK", "checked": false }
      ]
    }
  ],
  "connectors": [
    {
      "id": "conn-1",
      "fromId": "card-sticky",
      "toId": "card-tasks",
      "startX": 31960.0,
      "startY": 31860.0,
      "endX": 32020.0,
      "endY": 31860.0,
      "color": -10262799,
      "strokeWidth": 5.0,
      "style": "arrow",
      "headStyle": "triangle",
      "tailStyle": "dot",
      "isForeground": false
    }
  ],
  "strokes": [],
  "fgStrokes": []
}
```

---

## 7. Android System Integration Lifecycle

### Export Lifecycle (`ExportManager.kt`)
1. User requests **Export Project Archive (`.noteapp`)**.
2. NoteApp scans `board.boxes` for any non-null `imagePath` pointing to internal storage (`/data/data/.../files/images/xyz.png`).
3. Each physical image file is written into the zip stream under entry name `images/img_{index}.{ext}`.
4. Box data is cloned with relative paths (`images/img_1.png`).
5. `board.json` is serialized via `CanvasSerializer.serializeBoard(mappedBoard)` and written to the zip stream.
6. The zip stream is flushed and outputted via Android Storage Access Framework (SAF).

### Import Lifecycle (`MainActivity.kt`)
1. Inbound `.noteapp` file is received via:
   - System File Manager tap (`ACTION_VIEW` intent with content URI).
   - Share Sheet from WhatsApp, Drive, or Gmail (`ACTION_SEND` intent with `EXTRA_STREAM`).
   - In-app "Import Board" button.
2. NoteApp reads stream bytes and verifies the PKZip header (`0x50, 0x4B`).
3. Each entry in `images/` is extracted to internal storage:
   `/data/user/0/com.noteapp.student/files/images/imported_{timestamp}_{uuid}.png`.
4. `board.json` is read and parsed via `CanvasSerializer.deserializeBoard()`.
5. Box `imagePath` strings are mapped from relative names (`images/img_1.png`) to the newly extracted local file paths.
6. Board metadata is stored in `manifest.json` and loaded into `InfiniteCanvasView`.
7. Camera smoothly centers on the imported content via `zoomToFit()`.
