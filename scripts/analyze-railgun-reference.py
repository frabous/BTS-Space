#!/usr/bin/env python3
"""Extract a 100 ms reference gallery using FFmpeg, ffprobe and Pillow.

Usage: python scripts/analyze-railgun-reference.py VIDEO [--output DIRECTORY]
       python scripts/analyze-railgun-reference.py --check [--output DIRECTORY]
Requested times are relative to the first decoded video PTS. Nearest real
frames are selected (earlier frame wins ties); requests beyond video frames
use the last frame and retain its true PTS. Existing output is never replaced.
"""

import argparse
from bisect import bisect_left
from fractions import Fraction
import html
import json
from pathlib import Path
import re
import shutil
import subprocess

from PIL import Image, ImageDraw, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "build/reference-analysis/100ms"
STEP = Fraction(1, 10)
TILE = (260, 390)
LABEL_HEIGHT = 48


def command(args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result


def probe(video, ffprobe):
    result = command([
        ffprobe, "-v", "error", "-select_streams", "v:0", "-show_streams",
        "-show_format", "-show_frames", "-show_entries",
        "stream=index,codec_name,width,height,r_frame_rate,avg_frame_rate,time_base,"
        "start_pts,start_time,duration,nb_frames:format=duration:"
        "frame=pts,best_effort_timestamp", "-of", "json", str(video),
    ])
    data = json.loads(result.stdout)
    frames = data["frames"]
    pts = [int(frame["pts"]) for frame in frames]
    if len(pts) < 2 or any(b <= a for a, b in zip(pts, pts[1:])):
        raise ValueError("Expected at least two frames with strictly increasing source PTS")
    return data, pts


def plan_samples(data, pts):
    stream = data["streams"][0]
    time_base = Fraction(stream["time_base"])
    source_times = [(p - pts[0]) * time_base for p in pts]
    duration = Fraction(data["format"]["duration"])
    requested = [i * STEP for i in range(int(duration / STEP) + 1)]
    indexes = []
    for target in requested:
        right = bisect_left(source_times, target)
        candidates = {max(0, right - 1), min(right, len(pts) - 1)}
        indexes.append(min(candidates, key=lambda i: (abs(source_times[i] - target), i)))
    deltas = {b - a for a, b in zip(pts, pts[1:])}
    period = (pts[1] - pts[0]) * time_base
    cfr = len(deltas) == 1 and Fraction(stream["avg_frame_rate"]) == 1 / period
    stride = STEP / period
    selected = sorted(set(indexes))
    if cfr and stride.denominator == 1:
        regular = [i for i in selected if i % int(stride) == 0]
        expected = list(range(0, regular[-1] + 1, int(stride)))
        if regular == expected:
            expression = f"not(mod(n\\,{int(stride)}))*lte(n\\,{regular[-1]})"
            extras = [i for i in selected if i not in regular]
            expression += "".join(f"+eq(n\\,{i})" for i in extras)
            strategy = f"One-pass select every {int(stride)}th decoded frame"
            if extras:
                strategy += "; also select boundary frame(s) " + ", ".join(map(str, extras))
        else:
            expression = "+".join(f"eq(n\\,{i})" for i in selected)
            strategy = "One-pass select nearest decoded frame indexes"
    else:
        expression = "+".join(f"eq(n\\,{i})" for i in selected)
        strategy = "One-pass select nearest decoded frame indexes"
    return requested, indexes, selected, expression, {
        "strategy": strategy, "cfr_confirmed_by_all_decoded_pts": cfr,
        "pts_delta_ticks": sorted(deltas), "interval_seconds": float(STEP),
        "timestamp_origin": "First decoded video PTS", "tie_break": "Earlier frame",
        "out_of_range_policy": "Use nearest boundary frame; never invent a PTS",
    }


def extract(video, output, ffmpeg, expression, selected, pts):
    frames_dir = output / "frames"
    frames_dir.mkdir()
    args = [
        ffmpeg, "-hide_banner", "-nostdin", "-loglevel", "info", "-n", "-copyts",
        "-i", str(video), "-map", "0:v:0", "-an", "-sn", "-dn",
        "-vf", "select='" + expression + "',showinfo", "-fps_mode", "passthrough",
        "-c:v", "mjpeg", "-q:v", "2", "-start_number", "0",
        str(frames_dir / "decoded_%04d.jpg"),
    ]
    result = command(args)
    log = result.stderr.decode("utf-8", errors="replace")
    (output / "extraction.log").write_text(log, encoding="utf-8")
    actual = [int(p) for p in re.findall(r"\bn:\s*\d+\s+pts:\s*(-?\d+)", log)]
    if actual != [pts[i] for i in selected]:
        raise ValueError("FFmpeg showinfo PTS do not match selected ffprobe source frames")
    if len(list(frames_dir.glob("decoded_*.jpg"))) != len(selected):
        raise ValueError("Unexpected extracted JPEG count")
    return args


def name_samples(output, requested, indexes, selected, pts, time_base):
    extracted = {frame: output / "frames" / f"decoded_{i:04d}.jpg"
                 for i, frame in enumerate(selected)}
    samples = []
    for index, (target, frame) in enumerate(zip(requested, indexes)):
        path = f"frames/frame_{index:04d}_{float(target):05.1f}s.jpg"
        shutil.copyfile(extracted[frame], output / path)
        relative = (pts[frame] - pts[0]) * time_base
        samples.append({
            "index": index, "requested_ms": int(target * 1000),
            "requested_seconds": float(target), "source_frame_index": frame,
            "source_pts": pts[frame], "actual_source_seconds": float(pts[frame] * time_base),
            "actual_relative_seconds": float(relative),
            "offset_seconds": float(relative - target),
            "boundary_clamped": target > (pts[-1] - pts[0]) * time_base,
            "path": path,
        })
    for path in extracted.values():
        path.unlink()
    return samples


def sheet_crop(output, samples, width, height):
    # Keep every row with visible content in ANY sample; trim only shared bars.
    top, bottom = height, 0
    for sample in samples:
        with Image.open(output / sample["path"]) as image:
            bbox = image.convert("L").point(lambda p: 255 if p > 16 else 0).getbbox()
            if bbox:
                top, bottom = min(top, bbox[1]), max(bottom, bbox[3])
    return (0, top, width, bottom) if bottom > top else (0, 0, width, height)


def make_sheets(output, samples, crop):
    directory = output / "sheets"
    directory.mkdir()
    font = ImageFont.load_default(size=17)
    sheets = []
    for start in range(0, len(samples), 20):
        group = samples[start:start + 20]
        number = start // 20 + 1
        path = (f"sheets/sheet_{number:02d}_{group[0]['requested_seconds']:05.1f}s-"
                f"{group[-1]['requested_seconds']:05.1f}s.jpg")
        sheet = Image.new("RGB", (5 * TILE[0], 4 * TILE[1]), (18, 22, 30))
        draw = ImageDraw.Draw(sheet)
        for position, sample in enumerate(group):
            x, y = (position % 5) * TILE[0], (position // 5) * TILE[1]
            with Image.open(output / sample["path"]) as image:
                tile = ImageOps.contain(image.crop(crop), (TILE[0] - 8, TILE[1] - LABEL_HEIGHT - 8))
                sheet.paste(tile, (x + (TILE[0] - tile.width) // 2,
                                  y + (TILE[1] - LABEL_HEIGHT - tile.height) // 2))
            label_y = y + TILE[1] - LABEL_HEIGHT
            draw.text((x + 6, label_y),
                      f"#{sample['index']:03d} Requested {sample['requested_seconds']:.1f}s",
                      font=font, fill="white")
            draw.text((x + 6, label_y + 23),
                      f"Actual PTS {sample['actual_source_seconds']:.6f}s",
                      font=font, fill=(255, 195, 95) if sample["boundary_clamped"] else (170, 205, 255))
        sheet.save(output / path, quality=95, subsampling=0)
        sheets.append({"index": number, "path": path, "sample_count": len(group),
                       "first_sample_index": start, "last_sample_index": start + len(group) - 1})
    return sheets


def make_index(output, manifest):
    parts = ['<!doctype html><html lang="en"><meta charset="utf-8">',
             '<meta name="viewport" content="width=device-width, initial-scale=1">',
             '<title>Railgun reference - 100 ms gallery</title><style>',
             'body{background:#12161e;color:#eee;font:16px system-ui;margin:24px}',
             'a{color:#aad0ff} .sheet{max-width:100%;height:auto} ',
             '.frames{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:12px}',
             'figure{margin:0} .frames img{width:100%;height:280px;object-fit:contain}',
             'figcaption{font-size:14px} section{margin-bottom:32px}</style>',
             '<h1>Railgun reference - 100 ms gallery</h1>',
             '<p><a href="manifest.json">Manifest</a> | <a href="extraction.log">Extraction log</a></p>',
             '<p>Full frames preserve source resolution and black bars. Sheets crop shared black rows only. '
             'Requested times are relative to the first decoded video PTS. Actual PTS labels are source times. '
             'A request beyond the video uses the last real frame (see manifest offsets).</p>',
             '<nav>' + ' | '.join(f'<a href="#sheet-{s["index"]}">Sheet {s["index"]}</a>'
                                 for s in manifest["sheets"]) + '</nav>']
    for sheet in manifest["sheets"]:
        parts.append(f'<section id="sheet-{sheet["index"]}"><h2>Sheet {sheet["index"]}</h2>'
                     f'<a href="{sheet["path"]}"><img class="sheet" src="{sheet["path"]}" '
                     f'alt="Contact sheet {sheet["index"]}" loading="lazy"></a><div class="frames">')
        for sample in manifest["samples"][sheet["first_sample_index"]:sheet["last_sample_index"] + 1]:
            label = (f'#{sample["index"]:03d} Requested {sample["requested_seconds"]:.1f}s; '
                     f'actual PTS {sample["actual_source_seconds"]:.6f}s')
            parts.append(f'<figure><a href="{sample["path"]}"><img src="{sample["path"]}" '
                         f'alt="{html.escape(label)}" loading="lazy"></a>'
                         f'<figcaption>{html.escape(label)}</figcaption></figure>')
        parts.append('</div></section>')
    parts.append('</html>')
    (output / "index.html").write_text("\n".join(parts), encoding="utf-8")


def validate(output, manifest=None):
    if manifest is None:
        manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    ffprobe = shutil.which("ffprobe")
    if ffprobe is None:
        raise ValueError("ffprobe must be on PATH to validate against the source video")
    source = manifest["source"]
    data, pts = probe(Path(source["path"]), ffprobe)
    stream = data["streams"][0]
    time_base = Fraction(stream["time_base"])
    source_times = [(p - pts[0]) * time_base for p in pts]
    expected_source = {
        "resolution": [stream["width"], stream["height"]],
        "time_base": str(time_base), "first_decoded_pts": pts[0],
        "last_decoded_pts": pts[-1], "decoded_frame_count": len(pts),
        "first_decoded_seconds": float(pts[0] * time_base),
        "last_decoded_seconds": float(pts[-1] * time_base),
        "container_duration_seconds": data["format"]["duration"],
    }
    for field, expected in expected_source.items():
        if source.get(field) != expected:
            raise ValueError(f"Source {field} does not match fresh ffprobe data")
    samples, sheets = manifest["samples"], manifest["sheets"]
    expected_count = int(Fraction(data["format"]["duration"]) / STEP) + 1
    if len(samples) != expected_count:
        raise ValueError("Sample count does not match the source duration at 100 ms intervals")
    if len({s["path"] for s in samples}) != expected_count:
        raise ValueError("Sample paths must be unique")
    for index, sample in enumerate(samples):
        target = index * STEP
        right = bisect_left(source_times, target)
        candidates = {max(0, right - 1), min(right, len(pts) - 1)}
        frame = min(candidates, key=lambda i: (abs(source_times[i] - target), i))
        relative = source_times[frame]
        expected_sample = {
            "index": index, "requested_ms": int(target * 1000),
            "requested_seconds": float(target), "source_frame_index": frame,
            "source_pts": pts[frame], "actual_source_seconds": float(pts[frame] * time_base),
            "actual_relative_seconds": float(relative),
            "offset_seconds": float(relative - target),
            "boundary_clamped": target > source_times[-1],
        }
        for field, expected in expected_sample.items():
            if type(sample.get(field)) is not type(expected) or sample.get(field) != expected:
                raise ValueError(f"Sample {index} {field}: expected {expected!r}, got {sample.get(field)!r}")
        with Image.open(output / sample["path"]) as image:
            image.load()
            if image.format != "JPEG" or list(image.size) != expected_source["resolution"]:
                raise ValueError(f"Sample {index} must be a full-resolution source-sized JPEG")
    if len(sheets) != (expected_count + 19) // 20:
        raise ValueError("Unexpected contact sheet count")
    covered = []
    for sheet in sheets:
        covered.extend(range(sheet["first_sample_index"], sheet["last_sample_index"] + 1))
        if sheet["sample_count"] != sheet["last_sample_index"] - sheet["first_sample_index"] + 1:
            raise ValueError(f"Sheet {sheet['index']} sample count does not match its index range")
        with Image.open(output / sheet["path"]) as image:
            image.load()
            if image.format != "JPEG" or image.size != (1300, 1560):
                raise ValueError(f"Sheet {sheet['index']} must be a 1300x1560 JPEG")
    if covered != list(range(expected_count)):
        raise ValueError("Contact sheets must cover every sample exactly once in order")
    if len(list((output / "frames").glob("*.jpg"))) != expected_count:
        raise ValueError("Unexpected JPEG count in frames directory")
    if len(list((output / "sheets").glob("*.jpg"))) != len(sheets):
        raise ValueError("Unexpected JPEG count in sheets directory")
    if not (output / "index.html").is_file():
        raise ValueError("Missing index.html")
    return {"status": "PASS", "unique_consecutive_requested_times": expected_count,
            "full_resolution_jpegs_decoded": expected_count, "contact_sheets_decoded": len(sheets),
            "sheet_sample_counts": [s["sample_count"] for s in sheets],
            "boundary_clamped_samples": sum(s["boundary_clamped"] for s in samples),
            "max_absolute_offset_seconds": max(abs(s["offset_seconds"]) for s in samples)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", nargs="?", type=Path)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true", help="Validate an existing gallery without writing")
    args = parser.parse_args()
    output = args.output.resolve()
    if args.check:
        print(json.dumps(validate(output), indent=2))
        return
    if args.video is None or not args.video.is_file():
        parser.error("Provide an existing source video")
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        parser.error("Output must be absent or an empty directory; existing files are never overwritten")
    tools = {name: shutil.which(name) for name in ("ffmpeg", "ffprobe")}
    if not all(tools.values()):
        parser.error("ffmpeg and ffprobe must be on PATH")
    video = args.video.resolve()
    data, pts = probe(video, tools["ffprobe"])
    requested, indexes, selected, expression, sampling = plan_samples(data, pts)
    stream = data["streams"][0]
    time_base = Fraction(stream["time_base"])
    output.mkdir(parents=True, exist_ok=True)
    print(f"Extracting {len(selected)} real frames for {len(requested)} requested times...", flush=True)
    extraction_command = extract(video, output, tools["ffmpeg"], expression, selected, pts)
    samples = name_samples(output, requested, indexes, selected, pts, time_base)
    crop = sheet_crop(output, samples, stream["width"], stream["height"])
    sheets = make_sheets(output, samples, crop)
    manifest = {
        "schema_version": 1, "source": {
            "path": str(video), "resolution": [stream["width"], stream["height"]],
            "codec": stream["codec_name"], "r_frame_rate": stream["r_frame_rate"],
            "avg_frame_rate": stream["avg_frame_rate"], "time_base": str(time_base),
            "stream_start_pts": stream.get("start_pts"), "first_decoded_pts": pts[0],
            "last_decoded_pts": pts[-1], "decoded_frame_count": len(pts),
            "first_decoded_seconds": float(pts[0] * time_base),
            "last_decoded_seconds": float(pts[-1] * time_base),
            "container_duration_seconds": data["format"]["duration"],
            "video_stream_duration_seconds": stream.get("duration"),
        }, "sampling": sampling, "tools": tools,
        "extraction": {"command": extraction_command, "showinfo_pts_verified": True,
                       "unique_decoded_frames_extracted": len(selected), "jpeg_quality": 2},
        "contact_sheet": {"columns": 5, "rows": 4, "tile_size": list(TILE),
                          "crop_box": list(crop), "black_threshold": 16,
                          "crop_policy": "Union of visible row bounds across all samples; sheets only"},
        "samples": samples, "sheets": sheets,
    }
    make_index(output, manifest)
    manifest_path = output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    manifest["validation"] = validate(output)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(output), "sampling": sampling, "crop_box": list(crop),
                      "validation": manifest["validation"]}, indent=2))


if __name__ == "__main__":
    main()
