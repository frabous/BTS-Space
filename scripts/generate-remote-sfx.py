#!/usr/bin/env python3
"""Generate five short, original holographic UI cues; no downloaded or sampled audio."""
import argparse
from array import array
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/stellar"
TEMP = Path(os.environ.get("LOCALAPPDATA", "")) / "Temp/opencode"
RATE = 44100
SPECS = {"open": (0.42, (660, 990, 1320)), "hover": (0.07, (1480,)),
         "select": (0.22, (880, 1320)), "details": (0.20, (740, 1110)), "close": (0.19, (990, 660))}


def command(args):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def synthesize(name, seconds, notes):
    signal = array("d", [0.0]) * round(seconds * RATE)
    spacing = 0.075 if name == "open" else 0.045
    for note, frequency in enumerate(notes):
        offset = note * spacing
        for i in range(round(offset * RATE), len(signal)):
            t = i / RATE - offset
            attack = min(1, t / 0.004)
            decay = math.exp(-t * (65 if name == "hover" else 22))
            sweep = -240 if name == "close" else 90
            phase = math.tau * (frequency * t + sweep * t * t * 0.5)
            signal[i] += attack * decay * (math.sin(phase) + 0.12 * math.sin(phase * 2))
    peak = max(abs(value) for value in signal)
    for i in range(len(signal)):
        t = i / RATE
        # Both endpoints are silent, including the short hover tick.
        fade = min(1, t / 0.003, (seconds - t) / 0.018)
        signal[i] = signal[i] * fade * (0.35 if name == "hover" else 0.50) / peak
    return signal


def generate():
    with tempfile.TemporaryDirectory(prefix="remote-sfx-", dir=TEMP) as temporary:
        wav = Path(temporary) / "cue.wav"
        for name, (seconds, notes) in SPECS.items():
            signal = synthesize(name, seconds, notes)
            with wave.open(str(wav), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(RATE)
                output.writeframes(struct.pack("<" + "h" * len(signal), *(round(value * 32767) for value in signal)))
            command(["ffmpeg", "-v", "error", "-y", "-fflags", "+bitexact", "-i", str(wav),
                     "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "5", "-flags:a", "+bitexact",
                     str(ASSETS / "sounds" / ("remote_" + name + ".ogg"))])


def validate():
    manifest = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    for name, (seconds, _) in SPECS.items():
        assert manifest["remote." + name]["sounds"] == ["stellar:remote_" + name]
        path = ASSETS / "sounds" / ("remote_" + name + ".ogg")
        metadata = json.loads(command(["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)]))
        stream = metadata["streams"][0]
        assert stream["codec_name"] == "vorbis" and stream["channels"] == 1 and int(stream["sample_rate"]) == RATE
        assert abs(float(metadata["format"]["duration"]) - seconds) < 0.015
        raw = command(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-"])
        values = [value[0] for value in struct.iter_unpack("<f", raw)]
        peak = max(abs(value) for value in values)
        rms = math.sqrt(sum(value * value for value in values) / len(values))
        assert 0.15 < peak < 0.7 and 0.02 < rms < 0.3
        assert max(abs(value) for value in values[-100:]) < 0.025
        print(f"remote.{name}: mono Vorbis, {seconds:.2f}s, peak={peak:.3f}, RMS={rms:.3f}")
    print("PASS: five registered UI cues, correct format/durations, bounded levels and silent tails.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for executable in ("ffmpeg", "ffprobe"):
        if not shutil.which(executable):
            parser.error(executable + " must be on PATH")
    if not args.check:
        generate()
    validate()
