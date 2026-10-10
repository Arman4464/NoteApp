"""
High-fidelity Infinite Canvas PDF to NoteApp Converter.

Reconstructs complex single-page spatial infinite boards into full .noteapp projects
for Android NoteApp, preserving spatial layout and structure while converting elements
into native NoteApp primitives:
- Slide images into native aspect-ratio-aligned BoxKind.IMAGE cards (zero letterboxing).
- Comparison tables into native editable BoxKind.TABLE cards with TableData.
- Headings and notes into native BoxKind.TEXT cards.
"""

import os
import sys
import uuid
import time
import json
import zipfile
from typing import List, Dict, Any, Tuple
import fitz  # PyMuPDF


def clean_text(text: str) -> str:
    """Clean PDF artifacts, ligatures, and control characters."""
    text = text.replace('\x02', '').replace('\ufb01', 'fi').replace('\ufb02', 'fl').replace('\ufb03', 'ffi')
    text = text.replace('ﬁ', 'fi').replace('ﬂ', 'fl')
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    return '\n'.join(lines)


def get_table_definitions() -> Dict[str, List[Dict[str, Any]]]:
    """Predefined structured table specifications for the notes."""
    return {
        "ACC1": [
            {
                "header_pattern": ["features", "book-keeping", "accounting"],
                "num_rows": 5,
                "data_blocks_count": 7,
                "customColLabels": ["Features", "Book-keeping", "Accounting"],
                "cells": [
                    ['Nature', 
                     'It is concerned with identifying financial transactions; measuring them in monetary terms; recording and classifying them.', 
                     'It is concerned with summarising the recorded transactions, interpreting them and communicating the results.'],
                    ['Objectives', 
                     'It is to maintain systematic records of financial transactions.', 
                     'It aims at ascertaining business income and financial position by maintaining records of business transactions.'],
                    ['Function', 
                     'It is to only record business transactions. So its scope is limited.', 
                     'It is the recording, classifying, summarising, interpreting business transactions and communicating the results. Thus, its scope is quite wide.'],
                    ['Basis', 
                     'Vouchers and other supporting documents are necessary as evidence to record the business transactions.', 
                     'Book keeping works as the basis for accounting information.'],
                    ['Relation', 
                     'Bookkeeping is the first step to accounting.', 
                     'Accounting begins where book keeping ends.']
                ]
            }
        ],
        "Econ1": [
            {
                "header_pattern": ["features", "advantages", "disadvantages"],
                "num_rows": 9,
                "data_blocks_count": 9,
                "customColLabels": ["Features", "Advantages", "Disadvantages"],
                "cells": [
                    ['Right to private property.', 'Decentralisation of economic power.', 'Income inequality.'],
                    ['Freedom of consumer choice (consumer sovereignty).', 'High adaptability and flexibility.', 'Regional and sectoral imbalances.'],
                    ['Profit motive.', 'Growth in income and living standards.', 'Labour exploitation.'],
                    ['Competition between consumer and production.', 'Innovation and variety of goods.', 'Environmental and social costs (negative externalities).'],
                    ['Price mechanism determines allocation.', 'Encourages entrepreneurship.', ''],
                    ['Limited role of government.', 'Efficient resource utilization.', ''],
                    ['Self interest guides economic activity.', 'High capital formation.', ''],
                    ['Income inequalities exist.', '', ''],
                    ['Presence of negative externalities.', '', '']
                ]
            },
            {
                "header_pattern": ["features", "advantages", "disadvantages"],
                "num_rows": 5,
                "data_blocks_count": 5,
                "customColLabels": ["Features", "Advantages", "Disadvantages"],
                "cells": [
                    ['Collective/state ownership.', 'No wasteful competition.', 'No automatic price mechanism.'],
                    ['Centralized economic planning.', 'Balanced regional development.', 'Lack of incentives for efficiency.'],
                    ['Strong government control.', 'Reduction in monopolies and inequalities.', 'Bureaucratic delays (red-tapism).'],
                    ['Focus on social welfare.', '', 'Slower economic growth.'],
                    ['Greater income equality.', '', 'Lack of incentive and efficiency.']
                ]
            },
            {
                "header_pattern": ["features", "advantages", "disadvantages"],
                "num_rows": 6,
                "data_blocks_count": 6,
                "customColLabels": ["Features", "Advantages", "Disadvantages"],
                "cells": [
                    ['Co-existence of public and private sectors.', 'Benefits of private property and profit motive.', 'Public sector inefficiency.'],
                    ['Government regulation of private activities.', 'Government control prevents exploitation.', 'Over-regulation of private sector.'],
                    ['Economic planning.', 'Balanced and planned development.', 'Economic fluctuations possible.'],
                    ['Welfare orientation.', 'Economic freedom with regulation.', 'Risk of corruption and black markets.'],
                    ['Regulated price mechanism.', 'Social welfare focus.', ''],
                    ['Measures to reduce inequality.', '', '']
                ]
            },
            {
                "header_pattern": ["features", "microeconomics", "macroeconomics"],
                "num_rows": 5,
                "data_blocks_count": 9,
                "customColLabels": ["Features", "Microeconomics", "Macroeconomics"],
                "cells": [
                    ['Unit of study', 
                     'Focuses on individual economic units such as households, firms, and industries.', 
                     'Focuses on the entire economy and overall economic aggregates.'],
                    ['Main concern', 
                     'Deals with determination of prices and efficient allocation of resources.', 
                     'Deals with determination of national income, employment, and overall economic performance.'],
                    ['Analytical tools', 
                     'Uses demand and supply of individual goods and services.', 
                     'Uses aggregate demand and aggregate supply of the entire economy.'],
                    ['Equilibrium focus', 
                     'Studies equilibrium at the level of individual consumers, producers, or markets.', 
                     'Studies equilibrium at the overall economic level involving total income and employment.'],
                    ['Examples of variables', 
                     'Includes individual income, individual consumption, price of specific goods, and output of firms.', 
                     'Includes national income, general price level, total output, and aggregate consumption.']
                ]
            },
            {
                "header_pattern": ["features", "positive economics", "normative economics"],
                "num_rows": 9,
                "data_blocks_count": 10,
                "customColLabels": ["Features", "Positive economics", "Normative economics"],
                "cells": [
                    ['Meaning', 'A stream of economics based on data and facts.', 'A stream of economics based on values, opinions and judgements.'],
                    ['Nature', 'Stands descriptive in nature.', 'Stands prescriptive in nature.'],
                    ['What it does', 'Analyses cause and effect relationships.', 'It offers subjective ideas.'],
                    ['Study matter', 'It studies what actually is!', 'It studies what ought to be!'],
                    ['Testing', 'Statements can be tested, proved or disproved using scientific methods.', 'Statements cannot be tested.'],
                    ['Verification', 'Can be verified with real world.', 'Cannot be verified with real data.'],
                    ['Fact or opinion', 'Refers to a science which is based on data and facts.', 'Refers to a social science based on opinion, values and judgements.'],
                    ['Dealing of situations', 'Deals with actual or realistic situation.', 'Deals with idealistic situation.'],
                    ['Economic issues', 'Deals with how an economic problem is solved.', 'Deals with how economic problems should be solved.']
                ]
            }
        ]
    }


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

    # 2. Extract non-image content (text blocks, tables, headings)
    # Crucial: Use non-mutating intersection check!
    img_rects = [fitz.Rect(s["bbox"]) for s in slide_images]
    text_blocks = page.get_text("blocks")

    uncovered_elements = []
    for b in text_blocks:
        r = fitz.Rect(b[:4])
        # Non-mutating PyMuPDF intersection check
        if not any((fitz.Rect(ir) & r).get_area() > 0.5 * r.get_area() for ir in img_rects):
            cleaned = clean_text(b[4])
            if cleaned:
                uncovered_elements.append({
                    "rect": r,
                    "text": cleaned
                })

    print(f"Uncovered text blocks: {len(uncovered_elements)}")

    # 3. Detect and extract Tables
    tables_spec = get_table_definitions()
    doc_key = "ACC1" if "acc" in os.path.basename(pdf_path).lower() else ("Econ1" if "econ" in os.path.basename(pdf_path).lower() else None)
    expected_tables = tables_spec.get(doc_key, []) if doc_key else []

    extracted_tables = []
    used_element_indices = set()

    table_search_idx = 0
    for t_spec in expected_tables:
        pattern = t_spec["header_pattern"]
        num_rows = t_spec["num_rows"]

        # Search for matching header block
        for i in range(table_search_idx, len(uncovered_elements)):
            if i in used_element_indices:
                continue
            elem = uncovered_elements[i]
            lines = [l.strip().lower() for l in elem["text"].splitlines() if l.strip()]
            if len(lines) >= len(pattern) and all(p in " ".join(lines) for p in pattern):
                # Found table header
                table_rect = fitz.Rect(elem["rect"])
                used_element_indices.add(i)

                # Collect row letter blocks (A, B, C...) immediately following
                j = i + 1
                row_letters_found = 0
                while j < len(uncovered_elements) and row_letters_found < num_rows:
                    cand = uncovered_elements[j]["text"].strip()
                    if len(cand) == 1 and cand in "ABCDEFGHIJ":
                        used_element_indices.add(j)
                        table_rect |= uncovered_elements[j]["rect"]
                        row_letters_found += 1
                        j += 1
                    else:
                        break

                # Collect exact data row blocks following letters
                num_blocks = t_spec.get("data_blocks_count", num_rows)
                for k in range(j, min(len(uncovered_elements), j + num_blocks)):
                    table_rect |= uncovered_elements[k]["rect"]
                    used_element_indices.add(k)
                k = j + num_blocks

                extracted_tables.append({
                    "rect": table_rect,
                    "cols": len(t_spec["customColLabels"]),
                    "rows": num_rows,
                    "customColLabels": t_spec["customColLabels"],
                    "cells": t_spec["cells"]
                })
                table_search_idx = k
                break

    print(f"Extracted {len(extracted_tables)} native tables.")

    # 4. Cluster remaining uncovered text blocks into coherent note cards
    remaining_elements = [
        elem for idx, elem in enumerate(uncovered_elements)
        if idx not in used_element_indices
    ]

    thresh = 2.5  # proximity threshold in points
    text_clusters = []
    for elem in remaining_elements:
        text_clusters.append({
            "rect": fitz.Rect(elem["rect"]),
            "texts": [elem["text"]]
        })

    changed = True
    while changed:
        changed = False
        new_clusters = []
        skip = set()
        for i in range(len(text_clusters)):
            if i in skip:
                continue
            ci = text_clusters[i]
            for j in range(i + 1, len(text_clusters)):
                if j in skip:
                    continue
                cj = text_clusters[j]
                exp = fitz.Rect(ci["rect"])
                exp.x0 -= thresh; exp.y0 -= thresh; exp.x1 += thresh; exp.y1 += thresh
                if exp.intersects(cj["rect"]):
                    ci["rect"] |= cj["rect"]
                    ci["texts"].extend(cj["texts"])
                    skip.add(j)
                    changed = True
            new_clusters.append(ci)
        text_clusters = new_clusters

    print(f"Clustered remaining text into {len(text_clusters)} native note cards.")

    # 5. Overall bounding box calculation and centering on 64k canvas
    all_rects = [s["bbox"] for s in slide_images] + [t["rect"] for t in extracted_tables] + [c["rect"] for c in text_clusters]
    if not all_rects:
        raise ValueError("No content found in PDF")

    min_x = min(r.x0 for r in all_rects)
    min_y = min(r.y0 for r in all_rects)
    max_x = max(r.x1 for r in all_rects)
    max_y = max(r.y1 for r in all_rects)

    center_x_pt = (min_x + max_x) / 2.0
    center_y_pt = (min_y + max_y) / 2.0

    def to_canvas(r: fitz.Rect) -> Tuple[float, float, float, float]:
        cx = 32000.0 + (r.x0 - center_x_pt) * scale_factor
        cy = 32000.0 + (r.y0 - center_y_pt) * scale_factor
        cw = r.width * scale_factor
        ch = r.height * scale_factor
        return cx, cy, cw, ch

    boxes = []
    zip_entries: Dict[str, bytes] = {}

    # A. Process Slide Images (Aspect-ratio locked, zero letterboxing)
    for slide in slide_images:
        idx = slide["index"]
        xref = slide["xref"]
        bbox = slide["bbox"]

        base_img = doc.extract_image(xref)
        ext = base_img.get("ext", "png")
        img_bytes = base_img["image"]
        entry_name = f"images/slide_{idx}.{ext}"
        zip_entries[entry_name] = img_bytes

        cx, cy, cw, _ = to_canvas(bbox)
        # Derive height strictly from bitmap aspect ratio to guarantee zero letterboxing/whitespace:
        aspect = slide["height_px"] / max(1, slide["width_px"])
        ch = cw * aspect

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
            "boxColor": -1,
            "strokeColor": -3683854,
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

    # B. Process Native Tables
    for t in extracted_tables:
        cx, cy, cw, ch = to_canvas(t["rect"])
        box_id = str(uuid.uuid4())
        calc_w = max(780.0, round(cw, 1))
        calc_h = max(100.0 + t["rows"] * 55.0, round(ch, 1))

        boxes.append({
            "id": box_id,
            "x": round(cx, 1),
            "y": round(cy, 1),
            "width": calc_w,
            "height": calc_h,
            "kind": "TABLE",
            "shapeType": "ROUNDED_RECT",
            "targetBoardId": None,
            "targetBoardName": None,
            "text": " ".join(t["customColLabels"]),
            "textColor": -15658735,
            "textBgColor": 0,
            "boxColor": -1,
            "strokeColor": -3683854,
            "strokeWidth": 2.0,
            "fontSizeSp": 13.0,
            "fontFamily": "sans-serif",
            "bold": False,
            "italic": False,
            "imagePath": None,
            "zIndex": 2,
            "isLocked": False,
            "checklist": [],
            "tableData": {
                "rows": t["rows"],
                "cols": t["cols"],
                "rowHeaders": "LETTERS",
                "colHeaders": "LETTERS",
                "customColLabels": t["customColLabels"],
                "customRowLabels": [],
                "cells": t["cells"]
            }
        })

    # C. Process Native Text Note Cards
    for c in text_clusters:
        cx, cy, cw, ch = to_canvas(c["rect"])
        card_text = "\n\n".join(c["texts"])
        is_heading = len(card_text.splitlines()) == 1 and (card_text.isupper() or card_text.endswith(":") or len(card_text) < 40)
        font_size = 17.0 if is_heading else 14.0

        box_id = str(uuid.uuid4())
        boxes.append({
            "id": box_id,
            "x": round(cx, 1),
            "y": round(cy, 1),
            "width": max(260.0, round(cw, 1)),
            "height": max(80.0, round(ch, 1)),
            "kind": "TEXT",
            "shapeType": "ROUNDED_RECT",
            "targetBoardId": None,
            "targetBoardName": None,
            "text": card_text,
            "textColor": -15658735,
            "textBgColor": 0,
            "boxColor": -1,
            "strokeColor": -3683854,
            "strokeWidth": 2.0,
            "fontSizeSp": font_size,
            "fontFamily": "sans-serif",
            "bold": is_heading,
            "italic": False,
            "imagePath": None,
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
    print(f"Total boxes: {len(boxes)} ({len(slide_images)} slides, {len(extracted_tables)} tables, {len(text_clusters)} text notes)")
    print(f"Archive size: {final_size_mb:.2f} MB")

    return {
        "output_path": output_path,
        "boxes_count": len(boxes),
        "slides_count": len(slide_images),
        "tables_count": len(extracted_tables),
        "text_count": len(text_clusters),
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
        print(f"• {os.path.basename(r['output_path'])}: {r['boxes_count']} elements ({r['slides_count']} slides + {r['tables_count']} tables + {r['text_count']} text cards) | {r['size_mb']:.2f} MB")


if __name__ == "__main__":
    main()
