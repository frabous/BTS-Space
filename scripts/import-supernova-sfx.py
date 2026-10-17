#!/usr/bin/env python3
"""Import three explicitly supplied Supernova recordings and derive their sheltered variants."""
import argparse
from array import array
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import shutil
import struct
import tempfile
import wave

spec = importlib.util.spec_from_file_location("supernova_sfx", Path(__file__).with_name("generate-supernova-sfx.py"))
sfx = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sfx)
ROOT, SOUNDS, RATE = sfx.ROOT, sfx.SOUNDS, sfx.RATE
RECEIPT = ROOT / "docs/supernova-audio-sources.json"


def decode(path):
    raw = sfx.sfx.command(["ffmpeg", "-v", "error", "-i", str(path), "-map", "0:a:0", "-ac", "1",
                           "-ar", str(RATE), "-f", "f32le", "-acodec", "pcm_f32le", "-"])
    values = array("d", (sample[0] for sample in struct.iter_unpack("<f", raw)))
    if not values or not all(math.isfinite(value) for value in values):
        raise ValueError("Empty or non-finite source: " + path.name)
    return values


def prepare(values, seconds, loop):
    values = array("d", values)
    count = round(seconds * RATE)
    if len(values) < count - round(0.025 * RATE):
        raise ValueError("Source shorter than the scheduled clip")
    if not values or not all(math.isfinite(value) for value in values):
        raise ValueError("Empty or non-finite samples")
    overlap = min(round(0.25 * RATE), max(0, len(values) - count)) if loop else 0
    if loop and overlap < 2:
        raise ValueError("A loop needs extra recorded audio for its wrap crossfade")
    if loop:
        # 2.25 s becomes 2 s: overlap the end with the beginning, not with silence.
        seam = array("d")
        for i in range(overlap):
            weight = (1 - math.cos(math.pi * i / (overlap - 1))) * 0.5
            seam.append(values[count + i] * (1 - weight) + values[i] * weight)
        values = values[overlap:count] + seam
        # Rephase the already continuous cycle near a zero crossing for the codec boundary.
        start = min(range(min(round(0.02 * RATE), count)),
                    key=lambda i: abs(values[i] - values[i - 1]) + 0.1 * (abs(values[i]) + abs(values[i - 1])))
        values = values[start:] + values[:start]
    else:
        values = values[:count]
        values.extend(array("d", [0]) * (count - len(values)))
    mean = sum(values) / count
    values = array("d", (value - mean for value in values))
    if not loop:
        for i in range(count):
            values[i] *= min(1, i / (RATE * 0.003), (count - 1 - i) / (RATE * 0.020))
    peak = max(abs(value) for value in values)
    if peak < 0.000001:
        raise ValueError("Silent source")
    opened = array("d", (value * 0.65 / peak for value in values))
    # Same processed recording, same duration and cycle phase; never normalize the filtered copy separately.
    muffled = sfx.lowpass(opened, 140, loop)
    mean = sum(muffled) / count
    muffled = array("d", ((value - mean) * 0.95 for value in muffled))
    return opened, muffled, overlap / RATE


def encode(values, path, wav):
    with wave.open(str(wav), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(struct.pack("<" + "h" * len(values), *(round(value * 32767) for value in values)))
    sfx.sfx.command(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-fflags", "+bitexact",
                     "-i", str(wav), "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "5",
                     "-flags:a", "+bitexact", "-fflags", "+bitexact", str(path)])


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_import():
    sfx.validate()
    receipt = json.loads(RECEIPT.read_text(encoding="utf-8"))
    expected_files = {"supernova_" + name + suffix + ".ogg" for name in sfx.SPECS for suffix in ("", "_muffled")}
    if set(receipt["installed_sha256"]) != expected_files:
        raise ValueError("The import receipt must cover exactly the six Supernova assets")
    for filename, expected in receipt["installed_sha256"].items():
        if Path(filename).name != filename or not filename.startswith("supernova_") or not filename.endswith(".ogg"):
            raise ValueError("Invalid asset name in the import receipt")
        if digest(SOUNDS / filename) != expected:
            raise ValueError("Installed recording differs from the import receipt: " + filename)
    print("PASS: installed audio matches the supplied recordings' import receipt.")


def import_files(sources):
    if not SOUNDS.is_dir() or not sfx.TEMP.is_dir() or not RECEIPT.parent.is_dir():
        raise ValueError("Missing project sounds, docs or approved OpenCode temporary directory")
    for path in sources.values():
        if not path.is_file():
            raise ValueError("Missing explicitly supplied source: " + str(path))
    originals = {name: digest(path) for name, path in sources.items()}
    receipt = {"provider": "User-supplied ElevenLabs recordings", "sample_rate": RATE,
               "open_peak_target": 0.65, "muffled_lowpass_hz": 140, "sources": {}, "installed_sha256": {}}
    with tempfile.TemporaryDirectory(prefix="supernova-import-", dir=sfx.TEMP) as directory:
        temporary = Path(directory)
        for name, (seconds, _) in sfx.SPECS.items():
            path = sources[name]
            values = decode(path)
            opened, muffled, overlap = prepare(values, seconds, name.endswith("_loop"))
            receipt["sources"][name] = {"file": path.name, "sha256": originals[name],
                                       "decoded_seconds": len(values) / RATE, "installed_seconds": seconds,
                                       "wrap_overlap_seconds": overlap}
            for suffix, signal in zip(("", "_muffled"), (opened, muffled)):
                destination = temporary / ("supernova_" + name + suffix + ".ogg")
                encode(signal, destination, temporary / "source.wav")
                receipt["installed_sha256"][destination.name] = digest(destination)
        # Validate all six candidates before replacing any installed file.
        previous_sounds = sfx.SOUNDS
        try:
            sfx.SOUNDS = temporary
            sfx.validate()
        finally:
            sfx.SOUNDS = previous_sounds
        if any(digest(sources[name]) != original for name, original in originals.items()):
            raise ValueError("A source changed during conversion; nothing installed")
        backup = ROOT / "build/supernova-audio-backup" / datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f")
        backup.mkdir(parents=True)
        for filename in receipt["installed_sha256"]:
            destination = SOUNDS / filename
            if destination.is_symlink():
                raise ValueError("Refusing to replace a linked audio asset: " + str(destination))
            if destination.is_file():
                shutil.copyfile(destination, backup / filename)
        if RECEIPT.is_symlink():
            raise ValueError("Refusing to replace a linked receipt")
        if RECEIPT.is_file():
            shutil.copyfile(RECEIPT, backup / RECEIPT.name)
        try:
            for filename in receipt["installed_sha256"]:
                shutil.copyfile(temporary / filename, SOUNDS / filename)
            RECEIPT.write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        except OSError:
            for filename in receipt["installed_sha256"]:
                if (backup / filename).is_file():
                    shutil.copyfile(backup / filename, SOUNDS / filename)
            if (backup / RECEIPT.name).is_file():
                shutil.copyfile(backup / RECEIPT.name, RECEIPT)
            raise
    validate_import()
    print("Installed three supplied takes and their filtered copies. Source MP3s are unchanged.")
    print("Previous installed files backed up to " + str(backup))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("pressure", "rumble", "wind"):
        parser.add_argument("--" + name, type=Path, help="Explicit source recording for " + name)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for executable in ("ffmpeg", "ffprobe"):
        if not shutil.which(executable):
            parser.error(executable + " must be on PATH")
    supplied = (args.pressure, args.rumble, args.wind)
    if args.check and any(supplied):
        parser.error("Use --check or three source paths, not both")
    try:
        if args.check:
            validate_import()
        elif not all(supplied):
            parser.error("Provide --pressure, --rumble and --wind")
        else:
            import_files({"pressure_hit": args.pressure.resolve(), "rumble_loop": args.rumble.resolve(),
                          "wind_loop": args.wind.resolve()})
    except (OSError, ValueError, KeyError, AssertionError) as error:
        parser.exit(1, "Supernova audio import failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
