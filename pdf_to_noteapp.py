#!/usr/bin/env python3
"""
pdf_to_noteapp.py — Automated PDF to .noteapp Infinite Canvas Project Converter

Converts any PDF document into a fully formatted, portable .noteapp workspace file
compatible with NoteApp for Android.

Features:
- High-fidelity rasterization of PDF pages to PNG image cards
- Smart text extraction and automatic companion note/sticky-note generation
- Directional arrow connectors between sequential pages/slides
- Automatic centering in the 64,000 x 64,000 px NoteApp canvas universe
- Multiple spatial layout modes: 'grid', 'horizontal', 'vertical', 'study'
- Zero external build dependencies outside PyMuPDF (fitz) and Pillow

Usage:
    python pdf_to_noteapp.py input.pdf
    python pdf_to_noteapp.py input.pdf -o output.noteapp --layout grid --dpi 150 --arrows
    python pdf_to_noteapp.py lecture.pdf --layout study --notes --subtheme oceanic_blue
"""

import argparse
import io
import json
import os
import sys
import uuid
import zipfile
from typing import Dict, List, Optional, Tuple

try:
    import pymupdf as fitz
except ImportError:
    try:
        import fitz
    except ImportError:
        print("Error: PyMuPDF is required. Install it using: pip install pymupdf", file=sys.stderr)
        sys.exit(1)

try:
    from PIL import Image
except ImportError:
    print("Error: Pillow is required. Install it using: pip install pillow", file=sys.stderr)
    sys.exit(1)


# Canvas Universe Constants
WORLD_SIZE = 64000.0
CANVAS_CENTER_X = 32000.0
CANVAS_CENTER_Y = 32000.0


def hex_to_argb_int(hex_str: str) -> int:
    """Convert hex color (#RRGGBB or #AARRGGBB) to Android 32-bit signed ARGB integer."""
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


# Standard NoteApp Palette Colors
COLOR_TEXT_DARK = hex_to_argb_int("#111827")
COLOR_TEXT_MUTED = hex_to_argb_int("#475569")
COLOR_CARD_BG = hex_to_argb_int("#FFFFFF")
COLOR_CARD_BORDER = hex_to_argb_int("#C7C9F2")
COLOR_STICKY_BG = hex_to_argb_int("#FEF08A")
COLOR_ARROW_INDIGO = hex_to_argb_int("#6366F1")
COLOR_TRANSPARENT = 0


def create_note_box_data(
    x: float,
    y: float,
    width: float,
    height: float,
    kind: str = "TEXT",
    shape_type: str = "ROUNDED_RECT",
    text: str = "",
    image_path: Optional[str] = None,
    box_color: int = COLOR_CARD_BG,
    text_color: int = COLOR_TEXT_DARK,
    font_family: str = "outfit",
    font_size_sp: float = 14.0,
    is_locked: bool = False,
    checklist: Optional[List[Dict]] = None,
    table_data: Optional[Dict] = None
) -> Dict:
    """Constructs a single NoteBoxData JSON dictionary compliant with NoteApp specification."""
    box = {
        "id": str(uuid.uuid4()),
        "x": float(x),
        "y": float(y),
        "width": float(width),
        "height": float(height),
        "kind": kind,
        "shapeType": shape_type,
        "targetBoardId": None,
        "targetBoardName": None,
        "text": text,
        "textColor": text_color,
        "textBgColor": COLOR_TRANSPARENT,
        "boxColor": box_color,
        "strokeColor": COLOR_CARD_BORDER,
        "strokeWidth": 3.0,
        "fontSizeSp": float(font_size_sp),
        "fontFamily": font_family,
        "bold": False,
        "italic": False,
        "imagePath": image_path,
        "zIndex": 0,
        "isLocked": is_locked,
        "checklist": checklist if checklist is not None else []
    }
    if table_data is not None:
        box["tableData"] = table_data
    return box


def create_connector_data(
    from_id: str,
    to_id: str,
    start_x: float,
    start_y: float,
    end_x: float,
    end_y: float,
    color: int = COLOR_ARROW_INDIGO,
    head_style: str = "triangle",
    tail_style: str = "none",
    is_foreground: bool = False
) -> Dict:
    """Constructs a ConnectorData JSON dictionary connecting two cards."""
    return {
        "id": str(uuid.uuid4()),
        "fromId": from_id,
        "toId": to_id,
        "startX": float(start_x),
        "startY": float(start_y),
        "endX": float(end_x),
        "endY": float(end_y),
        "color": color,
        "strokeWidth": 5.0,
        "style": "arrow",
        "headStyle": head_style,
        "tailStyle": tail_style,
        "isForeground": is_foreground
    }


def convert_pdf_to_noteapp(
    pdf_path: str,
    output_path: Optional[str] = None,
    layout: str = "grid",
    dpi: int = 144,
    include_notes: bool = False,
    include_arrows: bool = True,
    subtheme_id: Optional[str] = "oceanic_blue",
    subtheme_dark: bool = False,
    max_pages: Optional[int] = None
) -> str:
    """
    Renders PDF pages, extracts textual metadata, generates spatial canvas layout,
    and packages everything into a validated .noteapp ZIP archive.
    """
    if not os.path.exists(pdf_path):
        raise FileNotFoundError(f"PDF file not found: {pdf_path}")

    base_name = os.path.splitext(os.path.basename(pdf_path))[0]
    if output_path is None:
        output_path = f"{base_name}.noteapp"

    doc = fitz.open(pdf_path)
    total_pages = len(doc)
    pages_to_process = min(total_pages, max_pages) if max_pages else total_pages

    print(f"[*] Processing '{pdf_path}' ({pages_to_process} of {total_pages} pages) at {dpi} DPI...")

    page_images = []
    page_texts = []
    page_aspects = []

    zoom = dpi / 72.0
    mat = fitz.Matrix(zoom, zoom)

    for i in range(pages_to_process):
        page = doc[i]
        pix = page.get_pixmap(matrix=mat, alpha=False)
        img_bytes = pix.tobytes("png")
        page_images.append(img_bytes)

        # Extract text for companion notes
        text = page.get_text("text").strip()
        page_texts.append(text)

        aspect = page.rect.width / max(1.0, page.rect.height)
        page_aspects.append(aspect)

    doc.close()

    # Determine Card Sizing
    # Standard note width on canvas
    CARD_W = 480.0
    CARD_MARGIN_X = 60.0
    CARD_MARGIN_Y = 80.0
    NOTE_W = 320.0
    NOTE_GAP = 24.0

    boxes = []
    connectors = []
    image_entries = {}

    # Calculate spatial layout positions
    page_coords = []  # List of (card_x, card_y, card_w, card_h)

    if layout == "horizontal":
        total_w = sum(CARD_W + CARD_MARGIN_X for _ in range(pages_to_process)) - CARD_MARGIN_X
        cur_x = CANVAS_CENTER_X - (total_w / 2.0)
        for i in range(pages_to_process):
            aspect = page_aspects[i]
            card_h = CARD_W / aspect
            cur_y = CANVAS_CENTER_Y - (card_h / 2.0)
            page_coords.append((cur_x, cur_y, CARD_W, card_h))
            cur_x += CARD_W + CARD_MARGIN_X

    elif layout == "vertical":
        total_h = sum((CARD_W / page_aspects[i]) + CARD_MARGIN_Y for i in range(pages_to_process)) - CARD_MARGIN_Y
        cur_y = CANVAS_CENTER_Y - (total_h / 2.0)
        for i in range(pages_to_process):
            aspect = page_aspects[i]
            card_h = CARD_W / aspect
            cur_x = CANVAS_CENTER_X - (CARD_W / 2.0)
            page_coords.append((cur_x, cur_y, CARD_W, card_h))
            cur_y += card_h + CARD_MARGIN_Y

    elif layout == "study":
        # Two-column study layout: PDF page on left, text notes on right
        row_w = CARD_W + NOTE_GAP + NOTE_W
        cur_y = CANVAS_CENTER_Y - 400.0
        cur_x = CANVAS_CENTER_X - (row_w / 2.0)
        for i in range(pages_to_process):
            aspect = page_aspects[i]
            card_h = CARD_W / aspect
            page_coords.append((cur_x, cur_y, CARD_W, card_h))
            cur_y += card_h + CARD_MARGIN_Y

    else:  # 'grid' default (ideal for presentation decks & documents)
        cols = 3 if pages_to_process >= 3 else pages_to_process
        if pages_to_process > 8:
            cols = 4
        rows = (pages_to_process + cols - 1) // cols

        col_w = CARD_W + CARD_MARGIN_X
        if include_notes:
            col_w += NOTE_GAP + NOTE_W

        grid_total_w = cols * col_w - CARD_MARGIN_X
        start_x = CANVAS_CENTER_X - (grid_total_w / 2.0)
        start_y = CANVAS_CENTER_Y - 300.0

        for idx in range(pages_to_process):
            r = idx // cols
            c = idx % cols
            aspect = page_aspects[idx]
            card_h = CARD_W / aspect
            pos_x = start_x + (c * col_w)
            pos_y = start_y + (r * (CARD_W * 1.35 + CARD_MARGIN_Y))
            page_coords.append((pos_x, pos_y, CARD_W, card_h))

    # Create Title Header Card
    header_box = create_note_box_data(
        x=CANVAS_CENTER_X - 300.0,
        y=page_coords[0][1] - 180.0 if page_coords else CANVAS_CENTER_Y - 200.0,
        width=600.0,
        height=120.0,
        kind="TEXT",
        shape_type="ROUNDED_RECT",
        text=f"📚 {base_name.replace('_', ' ').title()}\nPDF Document Board • {pages_to_process} Pages",
        box_color=hex_to_argb_int("#EFF6FF"),
        font_family="outfit",
        font_size_sp=18.0
    )
    header_box["bold"] = True
    boxes.append(header_box)

    # Build Page Cards & Companion Notes
    created_page_boxes = []

    for idx in range(pages_to_process):
        img_bytes = page_images[idx]
        entry_name = f"images/page_{idx + 1}.png"
        image_entries[entry_name] = img_bytes

        px, py, pw, ph = page_coords[idx]

        # 1. Page Image Card
        page_box = create_note_box_data(
            x=px,
            y=py,
            width=pw,
            height=ph,
            kind="IMAGE",
            shape_type="ROUNDED_RECT",
            text=f"Page {idx + 1}",
            image_path=entry_name,
            box_color=COLOR_CARD_BG
        )
        boxes.append(page_box)
        created_page_boxes.append(page_box)

        # 2. Companion Sticky / Note Card (if requested or in study layout)
        if include_notes or layout == "study":
            note_x = px + pw + NOTE_GAP
            note_y = py
            raw_text = page_texts[idx]
            snippet = raw_text[:380] + ("..." if len(raw_text) > 380 else "")
            if not snippet:
                snippet = f"Notes for Slide {idx + 1}...\n\nDouble-tap to add your observations, formulas, or summaries."

            note_box = create_note_box_data(
                x=note_x,
                y=note_y,
                width=NOTE_W,
                height=min(ph, 300.0),
                kind="SHAPE" if (idx % 2 == 1) else "TEXT",
                shape_type="STICKY_NOTE" if (idx % 2 == 1) else "ROUNDED_RECT",
                text=f"📝 Page {idx + 1} Notes\n\n{snippet}",
                box_color=COLOR_STICKY_BG if (idx % 2 == 1) else hex_to_argb_int("#F8FAFC"),
                font_family="caveat" if (idx % 2 == 1) else "outfit",
                font_size_sp=16.0 if (idx % 2 == 1) else 13.0
            )
            boxes.append(note_box)

    # 3. Add Sequential Connectors (Arrows between pages)
    if include_arrows and len(created_page_boxes) > 1:
        for idx in range(len(created_page_boxes) - 1):
            b1 = created_page_boxes[idx]
            b2 = created_page_boxes[idx + 1]
            start_x = b1["x"] + b1["width"]
            start_y = b1["y"] + (b1["height"] / 2.0)
            end_x = b2["x"]
            end_y = b2["y"] + (b2["height"] / 2.0)

            conn = create_connector_data(
                from_id=b1["id"],
                to_id=b2["id"],
                start_x=start_x,
                start_y=start_y,
                end_x=end_x,
                end_y=end_y,
                color=COLOR_ARROW_INDIGO,
                head_style="triangle",
                tail_style="none",
                is_foreground=False
            )
            connectors.append(conn)

    # Assemble Board Data Structure
    board_data = {
        "meta": {
            "id": str(uuid.uuid4()),
            "name": base_name.replace("_", " ").title(),
            "createdAt": 1727376000000,
            "updatedAt": 1727376000000,
            "parentId": None,
            "subThemeId": subtheme_id,
            "subThemeIsDark": subtheme_dark
        },
        "panX": float(360.0),
        "panY": float(360.0),
        "scale": 0.85,
        "boxes": boxes,
        "connectors": connectors,
        "strokes": [],
        "fgStrokes": []
    }

    # Write PKZip (.noteapp) Archive
    print(f"[*] Packaging {len(boxes)} cards and {len(image_entries)} images into '{output_path}'...")
    with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        # 1. Write board.json
        json_bytes = json.dumps(board_data, indent=2, ensure_ascii=False).encode("utf-8")
        zf.writestr("board.json", json_bytes)

        # 2. Write embedded images
        for entry_name, img_data in image_entries.items():
            zf.writestr(entry_name, img_data)

    print(f"[+] Successfully generated: {output_path} ({os.path.getsize(output_path) / 1024:.1f} KB)")
    return output_path


def main():
    parser = argparse.ArgumentParser(
        description="Convert PDF documents into NoteApp (.noteapp) infinite canvas project archives."
    )
    parser.add_argument("pdf", help="Path to source PDF file")
    parser.add_argument("-o", "--output", help="Output .noteapp file path (default: <pdf_name>.noteapp)")
    parser.add_argument("--layout", choices=["grid", "horizontal", "vertical", "study"], default="grid",
                        help="Canvas layout arrangement (default: grid)")
    parser.add_argument("--dpi", type=int, default=144, help="Rasterization DPI resolution (default: 144)")
    parser.add_argument("--notes", action="store_true", help="Extract page text and create companion note cards")
    parser.add_argument("--no-arrows", action="store_true", help="Disable sequential connector arrows between pages")
    parser.add_argument("--subtheme", default="oceanic_blue",
                        choices=["oceanic_blue", "emerald_forest", "cyberpunk_neon", "vintage_parchment",
                                 "sunset_amber", "royal_purple", "rose_gold", "obsidian_stealth"],
                        help="Board subtheme palette (default: oceanic_blue)")
    parser.add_argument("--dark", action="store_true", help="Enable dark mode variant for subtheme")
    parser.add_argument("--max-pages", type=int, default=None, help="Limit number of pages converted")

    args = parser.parse_args()

    try:
        convert_pdf_to_noteapp(
            pdf_path=args.pdf,
            output_path=args.output,
            layout=args.layout,
            dpi=args.dpi,
            include_notes=args.notes,
            include_arrows=not args.no_arrows,
            subtheme_id=args.subtheme,
            subtheme_dark=args.dark,
            max_pages=args.max_pages
        )
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
