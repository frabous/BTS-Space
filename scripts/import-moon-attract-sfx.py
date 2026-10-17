#!/usr/bin/env python3
"""Import fourteen supplied Moon Attract audio files, or validate OGGs with --check."""
import argparse
from array import array
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/stellar"
RATE = 44100
# File duration, not the scheduled playback window. Loops retain all eight seconds.
SPECS = {
    "moon_attract_cannon_summon": (3.0, False),
    "moon_attract_cannon_deployment": (4.0, False),
    "moon_attract_gravity_charge": (5.0, False),
    "moon_attract_maximum_charge": (1.0, False),
    "moon_attract_gravity_beam_fire": (3.0, False),
    "moon_attract_moon_gravitational_pull": (8.0, True),
    "moon_attract_moon_approaching": (11.7, False),
    "moon_attract_atmospheric_entry": (5.7, False),
    "moon_attract_moon_fragmentation": (4.7, False),
    "moon_attract_pre_impact_tension": (1.7, False),
    "moon_attract_moon_impact": (6.0, False),
    "moon_attract_shockwave_expansion": (4.0, False),
    "moon_attract_debris_rain": (8.0, False),
    "moon_attract_aftermath_ambience": (8.0, True),
}


def command(arguments):
    result = subprocess.run(arguments, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise ValueError(result.stderr.decode("utf-8", errors="replace").strip())
    return result.stdout


def decode(path):
    raw = command(["ffmpeg", "-v", "error", "-i", str(path), "-map", "0:a:0",
                   "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-acodec", "pcm_f32le", "-"])
    return [sample[0] for sample in struct.iter_unpack("<f", raw)]


def source_files(directory):
    if not directory.is_dir():
        raise ValueError(f"Source directory does not exist: {directory}")
    sources = {}
    errors = []
    files = list(directory.iterdir())
    for name in SPECS:
        matches = [path for path in files if path.is_file() and path.stem == name
                   and path.suffix.lower() in {".mp3", ".wav", ".ogg"}]
        if len(matches) != 1:
            errors.append(f"{name}: expected one MP3/WAV/OGG, found {len(matches)}")
        else:
            sources[name] = matches[0]
    if errors:
        raise ValueError("Source preflight failed:\n" + "\n".join(errors))
    return sources


def convert(source, destination, seconds, loop):
    values = decode(source)
    if not values or not all(math.isfinite(value) for value in values):
        raise ValueError(f"Empty or non-finite audio: {source}")
    count = round(seconds * RATE)
    original_count = len(values)
    values = values[:count]
    mean = sum(values) / len(values)
    values = [value - mean for value in values]
    peak = max(abs(value) for value in values)
    if peak == 0:
        raise ValueError(f"Silent audio: {source}")
    # Keep the contract length; short boundary fades de-click but do not repair a loop.
    fade_in = RATE * 0.005
    fade_out = RATE * (0.005 if loop else 0.020)
    samples = array("h", (round(value * 0.68 / peak * 32767
                                * min(1, i / fade_in)
                                * min(1, (len(values) - 1 - i) / fade_out))
                           for i, value in enumerate(values)))
    samples.extend(array("h", [0]) * (count - len(samples)))
    wav = destination.with_suffix(".wav")
    with wave.open(str(wav), "wb") as file:
        file.setnchannels(1)
        file.setsampwidth(2)
        file.setframerate(RATE)
        file.writeframes(samples.tobytes())
    command(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav),
             "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "5", str(destination)])
    print(f"{source.name}: {original_count / RATE:.3f}s -> {seconds:.3f}s "
          f"(trim/pad, {'loop' if loop else 'one-shot'})")


def validate_audio(path, seconds, loop):
    metadata = json.loads(command(["ffprobe", "-v", "error", "-show_streams", "-show_format",
                                   "-of", "json", str(path)]))
    stream = metadata["streams"][0]
    duration = float(metadata["format"]["duration"])
    if (stream["codec_name"] != "vorbis" or stream["channels"] != 1
            or int(stream["sample_rate"]) != RATE or abs(duration - seconds) >= 0.025):
        raise ValueError(f"Invalid format or duration: {path}")
    values = decode(path)
    if not values or not all(math.isfinite(value) for value in values):
        raise ValueError(f"Empty or non-finite decoded audio: {path}")
    peak = max(abs(value) for value in values)
    rms = math.sqrt(sum(value * value for value in values) / len(values))
    mean = sum(values) / len(values)
    seam = abs(values[-1] - values[0]) if loop else 0
    if not (0.1 < peak < 0.95 and rms > 0.002 and abs(mean) < 0.01 and seam < 0.08):
        raise ValueError(f"Invalid audio levels: {path}, peak={peak}, RMS={rms}, seam={seam}")
    print(f"{path.stem}: mono Vorbis {RATE} Hz, {duration:.3f}s, "
          f"peak={peak:.3f}, RMS={rms:.3f}, seam={seam:.4f}")


def read_manifest():
    manifest = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    if not isinstance(manifest, dict):
        raise ValueError("sounds.json must contain an object")
    return manifest


def validate():
    manifest = read_manifest()
    for name, (seconds, loop) in SPECS.items():
        event = manifest.get("cinematic." + name, {})
        if event.get("sounds") != ["stellar:" + name]:
            raise ValueError(f"{name}: own audio is not installed (fallback may still be active)")
        validate_audio(ASSETS / "sounds" / (name + ".ogg"), seconds, loop)


def import_directory(directory):
    # Check all fourteen names and manifest entries before creating any output.
    sources = source_files(directory)
    manifest = read_manifest()
    for name in SPECS:
        event = manifest.get("cinematic." + name, {})
        if not isinstance(event, dict):
            raise ValueError(f"Invalid sound event: cinematic.{name}")
    for executable in ("ffmpeg", "ffprobe"):
        if shutil.which(executable) is None:
            raise ValueError(f"Required executable is not on PATH: {executable}")
    with tempfile.TemporaryDirectory(prefix="moon-attract-audio-") as directory:
        temp = Path(directory)
        for name, (seconds, loop) in SPECS.items():
            path = temp / (name + ".ogg")
            convert(sources[name], path, seconds, loop)
            validate_audio(path, seconds, loop)
            manifest.setdefault("cinematic." + name, {})["sounds"] = ["stellar:" + name]
        # No project assets are changed until every conversion has passed validation.
        payload = json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"
        (ASSETS / "sounds").mkdir(parents=True, exist_ok=True)
        for name in SPECS:
            shutil.copyfile(temp / (name + ".ogg"), ASSETS / "sounds" / (name + ".ogg"))
        (ASSETS / "sounds.json").write_text(payload, encoding="utf-8")
    print("Installed fourteen Moon Attract cues. World-tick scheduling is separate.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", type=Path, help="Directory containing the fourteen named files")
    parser.add_argument("--check", action="store_true", help="Validate installed OGGs and event bindings")
    args = parser.parse_args()
    if args.check and args.source is not None:
        parser.error("Use a source directory or --check, not both")
    try:
        if args.check:
            validate()
        elif args.source is not None:
            import_directory(args.source.resolve())
        else:
            parser.error("Provide an explicit source directory or --check")
    except (OSError, ValueError, KeyError, IndexError) as error:
        parser.exit(1, f"Audio import failed: {error}\n")


if __name__ == "__main__":
    main()
