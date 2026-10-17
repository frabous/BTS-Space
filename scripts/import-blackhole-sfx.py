#!/usr/bin/env python3
"""Import the seven supplied Blackhole MP3s, or validate installed OGGs with --check."""
import argparse
from array import array
import hashlib
import json
import math
from pathlib import Path
import struct
import subprocess
import tempfile
import wave
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/stellar"
RATE = 44100
SPECS = {
    "blackhole_ascent": ("01_Ascension_Vers_Espace.mp3", 3.0, False),
    "blackhole_space_bed": ("02_Ambiance_Spatiale_LOOP.mp3", 8.0, True),
    "blackhole_reveal": ("03_Revelation_Blackhole.mp3", 4.0, False),
    "blackhole_accretion": ("04_Disque_Accretion_LOOP.mp3", 8.0, True),
    "blackhole_tidal": ("05_Tension_Gravitationnelle_LOOP.mp3", 4.0, True),
    "blackhole_horizon": ("06_Franchissement_Horizon.mp3", 3.0, False),
    "blackhole_return": ("07_Liberation_Retour_Sol.mp3", 3.5, False),
}


def command(arguments):
    return subprocess.run(arguments, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def decode(path):
    raw = command(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(RATE),
                   "-f", "f32le", "-acodec", "pcm_f32le", "-"])
    return [sample[0] for sample in struct.iter_unpack("<f", raw)]


def validate():
    manifest = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    report = {}
    for name, (_, seconds, loop) in SPECS.items():
        assert manifest["cinematic." + name]["sounds"] == ["stellar:" + name], name
        path = ASSETS / "sounds" / (name + ".ogg")
        metadata = json.loads(command(["ffprobe", "-v", "error", "-show_streams", "-show_format",
                                       "-of", "json", str(path)]))
        stream = metadata["streams"][0]
        duration = float(metadata["format"]["duration"])
        expected = seconds - (0.25 if loop else 0)
        assert stream["codec_name"] == "vorbis" and stream["channels"] == 1, path
        assert int(stream["sample_rate"]) == RATE and abs(duration - expected) < 0.025, path
        values = decode(path)
        peak = max(abs(value) for value in values)
        rms = math.sqrt(sum(value * value for value in values) / len(values))
        mean = sum(values) / len(values)
        seam = abs(values[-1] - values[0]) if loop else 0
        assert 0.1 < peak < 0.95 and rms > 0.002 and abs(mean) < 0.01, (name, peak, rms, mean)
        assert seam < 0.08, (name, "Loop discontinuity", seam)
        report[name] = dict(duration=duration, peak=peak, rms=rms, seam=seam,
                            sha256=hashlib.sha256(path.read_bytes()).hexdigest())
        print(f"{name}: mono Vorbis {RATE} Hz, {duration:.2f}s, peak={peak:.3f}, RMS={rms:.3f}, seam={seam:.4f}")
    return report


def import_archive(archive):
    output = ROOT / "build/blackhole-audio"
    output.mkdir(parents=True, exist_ok=True)
    originals = {}
    with zipfile.ZipFile(archive) as source, tempfile.TemporaryDirectory(dir=output) as directory:
        temp = Path(directory)
        for name, (filename, seconds, loop) in SPECS.items():
            # Read only the seven known entries; never extract archive-controlled paths.
            data = source.read(filename)
            mp3 = temp / filename
            mp3.write_bytes(data)
            values = decode(mp3)
            originals[name] = dict(file=filename, duration=len(values) / RATE,
                                   sha256=hashlib.sha256(data).hexdigest())
            count = round(seconds * RATE)
            assert len(values) >= count - round(0.1 * RATE), (filename, "Unexpected short source")
            values = values[:count] + [0.0] * max(0, count - len(values))
            if loop:
                overlap = round(0.25 * RATE)
                seam = [values[count - overlap + i] * (1 - i / (overlap - 1))
                        + values[i] * i / (overlap - 1) for i in range(overlap)]
                values = values[overlap:count - overlap] + seam
            else:
                for i in range(len(values)):
                    values[i] *= min(1, i / (RATE * 0.005)) * min(1, (len(values) - 1 - i) / (RATE * 0.020))
            mean = sum(values) / len(values)
            peak = max(abs(value - mean) for value in values)
            assert peak > 0, filename
            samples = array("h", (round((value - mean) * 0.68 / peak * 32767) for value in values))
            wav = temp / (name + ".wav")
            with wave.open(str(wav), "wb") as file:
                file.setnchannels(1); file.setsampwidth(2); file.setframerate(RATE)
                file.writeframes(samples.tobytes())
            command(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav),
                     "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "5",
                     str(ASSETS / "sounds" / (name + ".ogg"))])
    report = dict(archive=str(archive), sources=originals, installed=validate())
    (output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", nargs="?", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        validate()
    elif args.archive is not None:
        import_archive(args.archive)
    else:
        parser.error("Provide an archive or --check")


if __name__ == "__main__":
    main()
