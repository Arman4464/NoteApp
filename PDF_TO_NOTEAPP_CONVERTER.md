# PDF to `.noteapp` Conversion Guide & Tool Architecture

A complete implementation guide and operational manual for converting PDF documents into interactive, spatial `.noteapp` infinite canvas workspaces for **NoteApp on Android**.

---

## 1. Overview & Conceptual Paradigm

PDF documents are inherently linear and bound to strict, sequential pages ($1 \to 2 \to 3$). **NoteApp** operates on an expansive **64,000 × 64,000 pixel 2D infinite workspace**.

Converting a PDF to `.noteapp` transforms rigid documents into non-linear, spatial thinking boards:
- **Presentation Decks & Slides** become spatial grids with directional connector arrows showing flow.
- **Textbooks & Research Papers** become interactive study workspaces with original pages displayed alongside companion note cards, extracted formulas, and sticky notes.
- **Manuals & Technical Docs** become navigable visual reference maps.

```
+-----------------------------------------------------------------------------------+
|                        64,000 x 64,000 Infinite Canvas                            |
|                                                                                   |
|           +-------------------------------------------------------+               |
|           |       Title Card: "Machine Learning Lecture 04"       |               |
|           +-------------------------------------------------------+               |
|                                                                                   |
|    +-------------------+    Arrow    +-------------------+    Arrow               |
|    |   Slide Page 1    | ----------->|   Slide Page 2    | -----------> ...       |
|    |   [Image Card]    |             |   [Image Card]    |                        |
|    +-------------------+             +-------------------+                        |
|              |                                 |                                  |
|              v                                 v                                  |
|    +-------------------+             +-------------------+                        |
|    | Sticky Note:      |             | Table Card:       |                        |
|    | Key definition... |             | Hyperparameters   |                        |
|    +-------------------+             +-------------------+                        |
+-----------------------------------------------------------------------------------+
```

---

## 2. Canvas Layout Paradigms

When converting multi-page PDFs to an infinite canvas, four spatial layouts are recommended depending on the document type:

### 1. Presentation Grid (`grid`) — Recommended for Slide Decks
- **Pattern**: 3 to 4 columns of slides in an orderly matrix.
- **Directional Links**: Symmetrical connector arrows link each slide sequentially ($P_1 \to P_2 \to P_3$).
- **Best For**: PowerPoint / Keynote exports, lecture slides, pitch decks.

### 2. Study & Annotation Workspace (`study`) — Recommended for Textbooks
- **Pattern**: Two-column layout per row.
  - **Left column**: High-resolution rendered PDF page ($480 \text{ px}$ wide).
  - **Right column**: Extracted text summary, student notes, or sticky note ($320 \text{ px}$ wide).
- **Best For**: Academic papers, research articles, study guides, exam preparation.

### 3. Horizontal Filmstrip (`horizontal`)
- **Pattern**: All pages arranged in a single continuous horizontal row along the X-axis.
- **Best For**: Process maps, chronological reports, storyboards.

### 4. Vertical Document Strip (`vertical`)
- **Pattern**: Pages stacked vertically with comfortable margins along the Y-axis.
- **Best For**: Legal documents, vertical reading workflows, single-column reading.

---

## 3. Spatial Math & Centering on the 64k Canvas

To ensure converted boards open with optimal framing, content must be centered around the **canvas origin anchor**:

$$\text{Center}_X = 32000.0, \quad \text{Center}_Y = 32000.0$$

### Grid Layout Coordinate Equations
For $N$ pages arranged into $C$ columns with card width $W$, horizontal gap $G_x$, and vertical gap $G_y$:

1. **Calculate Total Grid Dimensions**:
   $$\text{Total}_W = C \cdot W + (C - 1) \cdot G_x$$
   $$\text{Rows} = \left\lceil \frac{N}{C} \right\rceil$$

2. **Compute Top-Left Grid Origin**:
   $$X_{\text{start}} = 32000.0 - \frac{\text{Total}_W}{2}$$
   $$Y_{\text{start}} = 32000.0 - 200.0$$

3. **Position for Page Index $i \in [0, N-1]$**:
   $$\text{row} = \lfloor i / C \rfloor, \quad \text{col} = i \pmod C$$
   $$H_i = \frac{W}{\text{Aspect}_i}$$
   $$X_i = X_{\text{start}} + \text{col} \cdot (W + G_x)$$
   $$Y_i = Y_{\text{start}} + \text{row} \cdot (H_{\text{avg}} + G_y)$$

---

## 4. Automated Converter: `pdf_to_noteapp.py`

This repository includes a turnkey, production-grade CLI script: [`pdf_to_noteapp.py`](file:///C:/Users/rango/OneDrive/Documents/NoteApp/pdf_to_noteapp.py).

### Prerequisites
The script uses **Python 3.8+** with **PyMuPDF** (`pymupdf`) and **Pillow** (`PIL`):
```powershell
pip install pymupdf pillow
```

### Quickstart Usage Examples

#### 1. Basic Conversion (Slide Deck Grid)
```powershell
python pdf_to_noteapp.py presentation.pdf
```
*Generates `presentation.noteapp` in a 3-column grid with sequential connecting arrows.*

#### 2. Study Mode with Extracted Text Notes
```powershell
python pdf_to_noteapp.py chapter1.pdf --layout study --notes
```
*Renders each page on the left and generates companion sticky notes containing extracted text on the right.*

#### 3. Custom Resolution & Sub-Theme Palette
```powershell
python pdf_to_noteapp.py paper.pdf -o research_board.noteapp --dpi 180 --subtheme emerald_forest --dark
```
*Renders at 180 DPI with the Emerald Forest Dark palette applied.*

#### 4. Horizontal Timeline (Without Connecting Arrows)
```powershell
python pdf_to_noteapp.py storyboard.pdf --layout horizontal --no-arrows
```

---

### Command-Line Arguments Reference

| Argument | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `pdf` | `String` | *(Required)* | Path to the source PDF document |
| `-o`, `--output` | `String` | `<pdf_name>.noteapp` | Destination output file path |
| `--layout` | `Choice` | `grid` | Spatial layout: `grid`, `study`, `horizontal`, `vertical` |
| `--dpi` | `Integer` | `144` | Rasterization resolution (72 = fast draft, 144 = crisp retina, 200 = ultra fine) |
| `--notes` | `Flag` | `False` | Extract text from each page into companion note/sticky-note cards |
| `--no-arrows` | `Flag` | `False` | Omit directional connectors between consecutive pages |
| `--subtheme` | `Choice` | `oceanic_blue` | Board palette: `oceanic_blue`, `emerald_forest`, `cyberpunk_neon`, `vintage_parchment`, `sunset_amber`, `royal_purple`, `rose_gold`, `obsidian_stealth` |
| `--dark` | `Flag` | `False` | Enable dark-mode variant for the chosen subtheme |
| `--max-pages` | `Integer` | `None` | Limit the conversion to the first $N$ pages |

---

## 5. Manual Conversion Recipe (Python Script from Scratch)

If you wish to integrate `.noteapp` generation directly into your own backend or pipeline without relying on the CLI script, use this minimal standalone template:

```python
import uuid
import json
import zipfile
import pymupdf  # PyMuPDF

def minimal_pdf_to_noteapp(pdf_path: str, output_path: str):
    doc = pymupdf.open(pdf_path)
    boxes = []
    connectors = []
    images = {}

    start_x = 32000.0 - (len(doc) * 520.0 / 2.0)
    start_y = 32000.0

    prev_box_id = None

    for i, page in enumerate(doc):
        # 1. Render page to PNG bytes
        pix = page.get_pixmap(dpi=144)
        img_bytes = pix.tobytes("png")
        img_entry = f"images/page_{i+1}.png"
        images[img_entry] = img_bytes

        # 2. Compute geometry
        card_w = 460.0
        card_h = card_w / (page.rect.width / page.rect.height)
        card_x = start_x + (i * 520.0)
        card_y = start_y - (card_h / 2.0)

        box_id = str(uuid.uuid4())
        boxes.append({
            "id": box_id,
            "x": card_x,
            "y": card_y,
            "width": card_w,
            "height": card_h,
            "kind": "IMAGE",
            "shapeType": "ROUNDED_RECT",
            "text": f"Page {i+1}",
            "textColor": -15658735,
            "boxColor": -1,
            "imagePath": img_entry,
            "isLocked": False
        })

        # 3. Add directional connecting arrow
        if prev_box_id:
            connectors.append({
                "id": str(uuid.uuid4()),
                "fromId": prev_box_id,
                "toId": box_id,
                "color": -10262799,  # Indigo #6366F1
                "strokeWidth": 5.0,
                "headStyle": "triangle",
                "tailStyle": "none"
            })
        prev_box_id = box_id

    doc.close()

    # 4. Construct board.json
    board_data = {
        "meta": {
            "id": str(uuid.uuid4()),
            "name": "Converted PDF Board",
            "createdAt": 1727376000000,
            "updatedAt": 1727376000000,
            "subThemeId": "oceanic_blue"
        },
        "panX": 0.0,
        "panY": 0.0,
        "scale": 0.9,
        "boxes": boxes,
        "connectors": connectors,
        "strokes": [],
        "fgStrokes": []
    }

    # 5. Package into ZIP (.noteapp)
    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("board.json", json.dumps(board_data, indent=2))
        for entry, data in images.items():
            zf.writestr(entry, data)

    print(f"Generated {output_path}")

# Run
minimal_pdf_to_noteapp("input.pdf", "output.noteapp")
```

---

## 6. How to Open Converted `.noteapp` Files on Android

Once you generate `<filename>.noteapp`, you can transfer and open it immediately on your Android device running NoteApp:

### Method A: Direct File Manager Tap
1. Copy `<filename>.noteapp` to your Android device (via USB, Downloads, or SD Card).
2. Open your device's **Files** app (Google Files, Samsung My Files, or Solid Explorer).
3. Tap on the `.noteapp` file.
4. Android will display **Open with NoteApp**.
5. NoteApp will launch, unpack images, register the board in `manifest.json`, and open the canvas centered on your content.

### Method B: Messaging / Cloud Share Sheet
1. Send the `.noteapp` file via **WhatsApp**, **Telegram**, **Google Drive**, or **Gmail**.
2. Tap the attachment.
3. Choose **NoteApp** from the Android system share sheet.
4. NoteApp handles inbound streams via `ACTION_SEND` / `ACTION_VIEW` and loads the project.

---

## 7. Advanced Enhancements

### 1. AI Summaries & Smart Sticky Notes
By connecting an LLM (such as Gemini 1.5 / 2.0 Flash) to your conversion pipeline:
1. Extract text from each slide or page.
2. Prompt Gemini: *"Summarize this slide into 3 concise bullet points and 1 key takeaway."*
3. Inject the result into a companion `SHAPE` card with `shapeType: "STICKY_NOTE"`, `fontFamily: "caveat"`, and `boxColor: -69494` (`#FEF08A`).
4. Result: A pre-analyzed, annotated visual study board created in seconds.

### 2. Native Table Parsing
For documents containing financial reports or tabular data:
1. Use `pdfplumber` to detect tables on each page:
   ```python
   import pdfplumber
   with pdfplumber.open("data.pdf") as pdf:
       table = pdf.pages[0].extract_table()
   ```
2. Convert the 2D array into a native NoteApp `TABLE` card:
   ```json
   {
     "kind": "TABLE",
     "tableData": {
       "rows": len(table),
       "cols": len(table[0]),
       "cells": table
     }
   }
   ```
3. The table opens in NoteApp as a fully editable, resizable spreadsheet grid on the canvas!
