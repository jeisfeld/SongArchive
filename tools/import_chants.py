#!/usr/bin/env python3
"""Export SongArchive chord sheets from the styled Chants Word document."""

import argparse
from collections import defaultdict
import json
from pathlib import Path, PurePosixPath
import re
import sys
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZipFile


W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
SONG_ID = re.compile(r"[XY0-9][0-9]{3}[a-z]?")
HEADING = re.compile(rf"^({SONG_ID.pattern})(?:\s+|$)", re.IGNORECASE)
LINE_KINDS = {
    "Akkorde": "chords",
    "Text": "lyrics",
    "TextEnde": "lyrics",
    "Additionaltext": "note",
    "Tonindikator": "pitch",
    "Index": "note",  # The translation at 0216 also uses this style.
}
RUN_TONES = {
    "AlternativerAkkord": "alternate",
    "AkkordeZchn": "chords",
    "Capo": "detail",
    "TonindikatorZchn": "pitch",
    "Texterklrung": "comment",
}
COLOR_TONES = {"0070C0": "chords", "C00000": "note", "BFBFBF": "alternate"}
SYMBOLS = {("Wingdings", "F0E0"): "→"}


def word_value(node, path):
    value = node.find(path)
    return None if value is None else value.get(W + "val", "true")


def run_parts(paragraph):
    for run in paragraph.iter(W + "r"):
        properties = run.find(W + "rPr")
        formatting = {}
        if properties is not None:
            tone = COLOR_TONES.get(word_value(properties, W + "color"))
            tone = tone or RUN_TONES.get(word_value(properties, W + "rStyle"))
            if tone:
                formatting["tone"] = tone
            underline = word_value(properties, W + "u")
            if underline and underline != "none":
                formatting["underline"] = underline
            for tag in ("b", "i"):
                if word_value(properties, W + tag) in ("true", "1", "on"):
                    formatting[{"b": "bold", "i": "italic"}[tag]] = True
            if word_value(properties, W + "vertAlign") == "superscript":
                formatting["superscript"] = True
        for element in run:
            if element.tag == W + "t" and element.text:
                yield element.text, formatting
            elif element.tag == W + "tab":
                yield "\t", {}
            elif element.tag == W + "cr" or (
                element.tag == W + "br" and element.get(W + "type", "textWrapping") == "textWrapping"
            ):
                yield "\n", {}
            elif element.tag == W + "sym":
                symbol = (element.get(W + "font"), element.get(W + "char"))
                if symbol not in SYMBOLS:
                    raise ValueError(f"unsupported Word symbol {symbol}")
                yield SYMBOLS[symbol], formatting


def styled_lines(parts, kind):
    runs, aside = [], []
    after_tab = False
    paragraph_start = True
    for text, formatting in [*parts, ("\n", {})]:
        if text == "\n":
            line = {"kind": kind, "runs": runs}
            if kind == "note":
                line["paragraphStart"] = paragraph_start
                paragraph_start = False
            aside_text = "".join(text for text, _ in aside).strip()
            if aside_text:
                line["aside"] = aside_text
                if kind in ("chords", "pitch") or any(f.get("tone") == "pitch" for _, f in aside):
                    line["asideTone"] = "pitch"
            yield line
            runs, aside = [], []
            after_tab = False
        elif text == "\t":
            if after_tab and aside and aside[-1][0] != " ":
                aside.append((" ", {}))
            after_tab = True
        elif after_tab:
            if aside and aside[-1][1] == formatting:
                aside[-1] = (aside[-1][0] + text, formatting)
            else:
                aside.append((text, formatting))
        elif runs and {key: value for key, value in runs[-1].items() if key != "text"} == formatting:
            runs[-1]["text"] += text
        else:
            runs.append({"text": text, **formatting})


def has_content(line):
    return bool(line.get("aside")) or any(run["text"].strip() for run in line["runs"])


def normalize_heading_field(parts):
    runs = []
    pending_space = False
    for text, formatting in parts:
        comment = formatting.get("tone") == "comment"
        for chunk in re.findall(r"\s+|\S+", text):
            if chunk.isspace():
                pending_space = bool(runs)
                continue
            if pending_space:
                chunk = " " + chunk
                pending_space = False
            if runs and (runs[-1].get("tone") == "comment") == comment:
                runs[-1]["text"] += chunk
            else:
                run = {"text": chunk}
                if comment:
                    run["tone"] = "comment"
                runs.append(run)
    return runs


def heading_fields(parts, start):
    fields = [[]]
    for text, formatting in parts:
        if start >= len(text):
            start -= len(text)
            continue
        text = text[start:]
        start = 0
        if text == "\t":
            fields.append([])
        else:
            fields[-1].append((text, formatting))
    return [normalize_heading_field(field) for field in fields]


def read_document(path):
    with ZipFile(path) as docx:
        document = ET.fromstring(docx.read("word/document.xml"))
    sections = defaultdict(list)
    sheet = None
    for paragraph in document.findall(f"{W}body/{W}p"):
        style = word_value(paragraph, f"{W}pPr/{W}pStyle")
        if style == "Liedtitel":
            parts = list(run_parts(paragraph))
            raw_heading = "".join(text for text, _ in parts)
            heading = raw_heading.strip()
            if not heading:
                continue
            if heading.casefold() == "index":
                break
            sheet = None
            match = HEADING.match(heading)
            if not match or not heading[match.end():]:
                print(f"Skipped heading without a song ID and title: {heading!r}", file=sys.stderr)
                continue
            start = len(raw_heading) - len(raw_heading.lstrip()) + match.end()
            title, *details = heading_fields(parts, start)
            fields = [title, *(detail for detail in details if detail)]
            title_text, *detail_texts = ["".join(run["text"] for run in field) for field in fields]
            sheet = {
                "version": 1,
                "title": title_text,
                "details": detail_texts,
                "lines": [],
            }
            if any(run.get("tone") == "comment" for field in fields for run in field):
                sheet["headingRuns"] = fields
            sections[normalize_id(match[1])].append(sheet)
        elif sheet is not None and style in LINE_KINDS:
            sheet["lines"].extend(styled_lines(run_parts(paragraph), LINE_KINDS[style]))
    if not sections:
        raise ValueError("No song headings with the Liedtitel style were found")
    return sections


def normalize_id(song_id):
    return song_id[:1].upper() + song_id[1:].lower()


def assign_ids(sections, variants):
    sheets = {}
    for heading_id, versions in sections.items():
        song_ids = variants.get(heading_id, [heading_id])
        if len(song_ids) != len(versions):
            raise ValueError(f"{heading_id}: found {len(versions)} headings for {len(song_ids)} mapped IDs")
        for index, (song_id, sheet) in enumerate(zip(song_ids, versions), start=1):
            if song_id is None:
                print(f"Skipped unmapped variant {index} of {heading_id}: {sheet['title']}", file=sys.stderr)
                continue
            if not SONG_ID.fullmatch(song_id) or song_id in sheets:
                raise ValueError(f"Invalid or duplicate export ID: {song_id}")
            lines = sheet["lines"]
            while lines and not has_content(lines[-1]):
                lines.pop()
            if not any(line["kind"] == "chords" and has_content(line) for line in lines):
                print(f"Skipped {song_id} because it has no chord rows", file=sys.stderr)
                continue
            sheets[song_id] = sheet
    return dict(sorted(sheets.items()))


def archive_ids(path):
    ids = set()
    with ZipFile(path) as archive:
        for name in archive.namelist():
            file = PurePosixPath(name)
            if file.suffix.lower() not in {".jpg", ".jpeg", ".png"}:
                continue
            match = HEADING.match(file.stem)
            if not match:
                raise ValueError(f"Image filename has no song ID: {name}")
            song_id = normalize_id(match[1])
            if song_id in ids:
                raise ValueError(f"Multiple images for song ID {song_id}")
            ids.add(song_id)
    if not ids:
        raise ValueError("No numbered images were found in the archive")
    return ids


def write_sql(sheets):
    print("START TRANSACTION;")
    for song_id, sheet in sheets.items():
        encoded = json.dumps(sheet, ensure_ascii=False, separators=(",", ":")).encode("utf-8").hex()
        imported = f"CONVERT(X'{encoded}' USING utf8mb4)"
        # Keep a disabled sheet on its image fallback when its Word source is updated.
        enabled = "JSON_EXTRACT(CASE WHEN JSON_VALID(chordmarkup) THEN chordmarkup END, '$.enabled')"
        disabled = "JSON_EXTRACT('{\"enabled\":false}', '$.enabled')"
        print(
            f"UPDATE songs SET chordmarkup = CASE WHEN JSON_TYPE({enabled}) = 'BOOLEAN' "
            f"AND JSON_UNQUOTE({enabled}) = 'false' THEN JSON_SET({imported}, '$.enabled', {disabled}) "
            f"ELSE {imported} END WHERE id = '{song_id}';"
        )
    print("COMMIT;")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("document", type=Path, help="Chants.docx")
    parser.add_argument("--variants", type=Path, default=Path(__file__).with_name("chant-variants.json"),
                        help="JSON map from a repeated heading ID to song IDs in document order")
    parser.add_argument("--images", type=Path, help="check which numbered images have chord sheets")
    parser.add_argument("--image-only", action="store_true", help="export only IDs in --images")
    parser.add_argument("--strict-images", action="store_true", help="fail if any image ID has no Word chord sheet")
    parser.add_argument("--song", action="append", type=normalize_id, help="export a song ID (repeat to select more songs)")
    parser.add_argument("--format", choices=("json", "sql"), default="json")
    args = parser.parse_args()
    if (args.image_only or args.strict_images) and not args.images:
        parser.error("--image-only and --strict-images require --images")
    try:
        variants = json.loads(args.variants.read_text(encoding="utf-8"))
        sheets = assign_ids(read_document(args.document), variants)
        if args.images:
            image_ids = archive_ids(args.images)
            missing = image_ids - sheets.keys()
            if missing:
                message = "No Word chord sheet for image IDs: " + ", ".join(sorted(missing))
                if args.strict_images:
                    raise ValueError(message)
                print(f"{message}. These songs keep their image view.", file=sys.stderr)
            extra = sheets.keys() - image_ids
            if extra and not args.image_only:
                print("Document IDs without images: " + ", ".join(sorted(extra)), file=sys.stderr)
            if args.image_only:
                sheets = {song_id: sheet for song_id, sheet in sheets.items() if song_id in image_ids}
                if not sheets:
                    raise ValueError("None of the image IDs has a Word chord sheet to export")
        if args.song:
            requested = set(args.song)
            missing = requested - sheets.keys()
            if missing:
                raise ValueError("No chord sheet in the selected export for song IDs: " + ", ".join(sorted(missing)))
            sheets = {song_id: sheet for song_id, sheet in sheets.items() if song_id in requested}
    except (OSError, BadZipFile, ET.ParseError, KeyError, ValueError) as error:
        parser.exit(1, f"{error}\n")
    if args.format == "sql":
        write_sql(sheets)
    else:
        print(json.dumps(sheets, ensure_ascii=False, indent=2))
    print(f"Exported {len(sheets)} chord sheets", file=sys.stderr)


if __name__ == "__main__":
    main()
