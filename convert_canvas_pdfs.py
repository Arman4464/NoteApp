"""
High-fidelity Infinite Canvas PDF to NoteApp Converter.

Reconstructs complex single-page spatial infinite boards (such as mindmaps,
slide collections, concept diagrams, tables, and handwritten annotations)
into full .noteapp projects for Android NoteApp.
"""

import os
import sys
import uuid
import time
import json
import zipfile
from typing import List, Dict, Any, Tuple
import fitz  # PyMuPDF


def convert_canvas_pdf(pdf_path: str, output_path: str, document_title: str = None) -> Dict[str, Any]:
    print(f"\n=======================================================")
    print(f"Converting: {os.path.basename(pdf_path)}")
    print(f"=======================================================")

    doc = fitz.open(pdf_path)
    if len(doc) == 0:
        raise ValueError(f"Empty PDF: {pdf_path}")

    page = doc[0]
    page_rect = page.rect
    print(f"PDF Page Rect: {page_rect.width:.1f} x {page_rect.height:.1f} pt")

    # 1. Retrieve all embedded slide images
    raw_images = page.get_images()
    raw_img_infos = page.get_image_info()

    slide_images = []
    scales = []

    for i, (img_tuple, info) in enumerate(zip(raw_images, raw_img_infos)):
        xref = img_tuple[0]
        bbox = fitz.Rect(info["bbox"])
        w_px = info["width"]
        h_px = info["height"]
        bw_pt = bbox.width
        bh_pt = bbox.height

        if bw_pt > 1 and bh_pt > 1:
            ratio = w_px / bw_pt
            scales.append(ratio)

        slide_images.append({
            "index": i + 1,
            "xref": xref,
            "bbox": bbox,
            "width_px": w_px,
            "height_px": h_px,
        })

    if not scales:
        scale_factor = 28.0
    else:
        scales.sort()
        scale_factor = scales[len(scales) // 2]

    print(f"Extracted {len(slide_images)} slide images. Scale factor: {scale_factor:.3f} px/pt")

    # 2. Extract non-image content (text blocks, tables, headings, annotations)
    img_rects = [s["bbox"] for s in slide_images]
    text_blocks = page.get_text("blocks")
    drawings = page.get_drawings()

    # Filter out page background and full-page grid lines
    non_grid_drawings = [
        d for d in drawings
        if not (
            (d["rect"].width == 0 and abs(d["rect"].height - page_rect.height) < 1)
            or (d["rect"].height == 0 and abs(d["rect"].width - page_rect.width) < 1)
            or (abs(d["rect"].width - page_rect.width) < 1 and abs(d["rect"].height - page_rect.height) < 1)
        )
    ]

    # Collect elements not covered by slide images
    uncovered_elements = []
    for b in text_blocks:
        r = fitz.Rect(b[:4])
        # If not primarily inside a slide image
        if not any(ir.intersects(r) and ir.intersect(r).get_area() > 0.5 * r.get_area() for ir in img_rects):
            uncovered_elements.append({
                "kind": "text",
                "rect": r,
                "text": b[4].strip()
            })

    for d in non_grid_drawings:
        r = d["rect"]
        if not any(ir.intersects(r) and ir.intersect(r).get_area() > 0.5 * r.get_area() for ir in img_rects):
            uncovered_elements.append({
                "kind": "draw",
                "rect": r,
                "text": ""
            })

    print(f"Uncovered elements: {len(uncovered_elements)} ({sum(1 for e in uncovered_elements if e['kind'] == 'text')} text, {sum(1 for e in uncovered_elements if e['kind'] == 'draw')} drawings)")

    # 3. Spatial clustering of non-image elements into coherent cards / tables
    thresh = 3.0  # proximity threshold in points
    clusters = []
    for elem in uncovered_elements:
        clusters.append({
            "rect": fitz.Rect(elem["rect"]),
            "texts": [elem["text"]] if elem["text"] else []
        })

    changed = True
    while changed:
        changed = False
        new_clusters = []
        skip = set()
        for i in range(len(clusters)):
            if i in skip:
                continue
            ci = clusters[i]
            for j in range(i + 1, len(clusters)):
                if j in skip:
                    continue
                cj = clusters[j]
                exp = fitz.Rect(ci["rect"])
                exp.x0 -= thresh; exp.y0 -= thresh; exp.x1 += thresh; exp.y1 += thresh
                if exp.intersects(cj["rect"]):
                    ci["rect"] |= cj["rect"]
                    ci["texts"].extend(cj["texts"])
                    skip.add(j)
                    changed = True
            new_clusters.append(ci)
        clusters = new_clusters

    print(f"Merged into {len(clusters)} standalone cards/tables.")

    # 4. Overall bounding box calculation and centering
    all_rects = [s["bbox"] for s in slide_images] + [c["rect"] for c in clusters]
    if not all_rects:
        raise ValueError("No content found in PDF")

    min_x = min(r.x0 for r in all_rects)
    min_y = min(r.y0 for r in all_rects)
    max_x = max(r.x1 for r in all_rects)
    max_y = max(r.y1 for r in all_rects)

    content_w_pt = max_x - min_x
    content_h_pt = max_y - min_y
    center_x_pt = (min_x + max_x) / 2.0
    center_y_pt = (min_y + max_y) / 2.0

    content_w_px = content_w_pt * scale_factor
    content_h_px = content_h_pt * scale_factor
    print(f"Total content bounds: {content_w_px:.0f} x {content_h_px:.0f} px on canvas (centered at 32000, 32000)")

    # Coordinate mapping function
    def to_canvas(r: fitz.Rect) -> Tuple[float, float, float, float]:
        cx = 32000.0 + (r.x0 - center_x_pt) * scale_factor
        cy = 32000.0 + (r.y0 - center_y_pt) * scale_factor
        cw = r.width * scale_factor
        ch = r.height * scale_factor
        return cx, cy, cw, ch

    # 5. Build NoteBoxData structures and prepare images
    boxes = []
    zip_entries: Dict[str, bytes] = {}

    # Process slide images
    for slide in slide_images:
        idx = slide["index"]
        xref = slide["xref"]
        bbox = slide["bbox"]

        # Extract lossless image from PDF
        base_img = doc.extract_image(xref)
        ext = base_img.get("ext", "png")
        img_bytes = base_img["image"]
        entry_name = f"images/slide_{idx}.{ext}"
        zip_entries[entry_name] = img_bytes

        cx, cy, cw, ch = to_canvas(bbox)

        box_id = str(uuid.uuid4())
        boxes.append({
            "id": box_id,
            "x": round(cx, 1),
            "y": round(cy, 1),
            "width": max(140.0, round(cw, 1)),
            "height": max(100.0, round(ch, 1)),
            "kind": "IMAGE",
            "shapeType": "ROUNDED_RECT",
            "targetBoardId": None,
            "targetBoardName": None,
            "text": "",
            "textColor": -15658735,
            "textBgColor": 0,
            "boxColor": -1,  # White
            "strokeColor": -3683854,  # Subtle crisp border #C7C9F2
            "strokeWidth": 2.0,
            "fontSizeSp": 15.0,
            "fontFamily": "sans-serif",
            "bold": False,
            "italic": False,
            "imagePath": entry_name,
            "zIndex": 1,
            "isLocked": False,
            "checklist": []
        })

    # Process non-image cards / tables / headings
    for j, cluster in enumerate(clusters):
        c_rect = cluster["rect"]
        # Add 0.8 pt padding around cluster for clean borders
        pad = 0.8
        padded_rect = fitz.Rect(
            max(0.0, c_rect.x0 - pad),
            max(0.0, c_rect.y0 - pad),
            min(page_rect.width, c_rect.x1 + pad),
            min(page_rect.height, c_rect.y1 + pad)
        )

        # Render vector clip at native scale
        pix = page.get_pixmap(clip=padded_rect, matrix=fitz.Matrix(scale_factor, scale_factor), alpha=False)
        img_bytes = pix.tobytes("png")

        entry_name = f"images/card_{j + 1}.png"
        zip_entries[entry_name] = img_bytes

        cx, cy, cw, ch = to_canvas(padded_rect)
        card_text = " ".join(cluster["texts"])

        box_id = str(uuid.uuid4())
        boxes.append({
            "id": box_id,
            "x": round(cx, 1),
            "y": round(cy, 1),
            "width": max(140.0, round(cw, 1)),
            "height": max(80.0, round(ch, 1)),
            "kind": "IMAGE",
            "shapeType": "ROUNDED_RECT",
            "targetBoardId": None,
            "targetBoardName": None,
            "text": card_text,
            "textColor": -15658735,
            "textBgColor": 0,
            "boxColor": 0,  # Transparent container
            "strokeColor": 0,
            "strokeWidth": 0.0,
            "fontSizeSp": 15.0,
            "fontFamily": "sans-serif",
            "bold": False,
            "italic": False,
            "imagePath": entry_name,
            "zIndex": 2,
            "isLocked": False,
            "checklist": []
        })

    # 6. Build board.json
    now_ms = int(time.time() * 1000)
    doc_name = document_title or os.path.splitext(os.path.basename(pdf_path))[0].replace("_", " ")

    board_json_obj = {
        "meta": {
            "id": str(uuid.uuid4()),
            "name": doc_name,
            "createdAt": now_ms,
            "updatedAt": now_ms,
            "parentId": None,
            "subThemeId": "sapphire",
            "subThemeIsDark": False
        },
        "panX": 0.0,
        "panY": 0.0,
        "scale": 1.0,
        "boxes": boxes,
        "connectors": [],
        "strokes": [],
        "fgStrokes": []
    }

    board_json_bytes = json.dumps(board_json_obj, indent=2, ensure_ascii=False).encode("utf-8")

    # 7. Package into ZIP .noteapp archive
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("board.json", board_json_bytes)
        for name, data in zip_entries.items():
            archive.writestr(name, data)

    final_size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"SUCCESS -> Created: {output_path}")
    print(f"Total canvas boxes: {len(boxes)} ({len(slide_images)} slides + {len(clusters)} cards/tables)")
    print(f"Archive size: {final_size_mb:.2f} MB")

    return {
        "output_path": output_path,
        "boxes_count": len(boxes),
        "slides_count": len(slide_images),
        "cards_count": len(clusters),
        "size_mb": final_size_mb
    }


def main():
    folder = r"C:\Users\rango\Files\New folder (2)"
    files = [
        ("ACC1_notes.pdf", "ACC1 Notes"),
        ("Comm1_notes.pdf", "Communication Notes"),
        ("Econ1_notes.pdf", "Economics Notes")
    ]

    print("Starting high-accuracy conversion for 3 PDF infinite boards...")
    results = []

    for fname, title in files:
        pdf_path = os.path.join(folder, fname)
        out_name = os.path.splitext(fname)[0] + ".noteapp"
        out_path = os.path.join(folder, out_name)

        if os.path.exists(pdf_path):
            res = convert_canvas_pdf(pdf_path, out_path, title)
            results.append(res)
        else:
            print(f"File not found: {pdf_path}")

    print("\n=======================================================")
    print("All conversions completed successfully!")
    print("=======================================================")
    for r in results:
        print(f"• {os.path.basename(r['output_path'])}: {r['boxes_count']} elements ({r['slides_count']} slides + {r['cards_count']} cards) | {r['size_mb']:.2f} MB")


if __name__ == "__main__":
    main()
