#!/usr/bin/env python3
"""Original Supernova witness audio: pressure front, rolling bass and dust wind; no sampled or downloaded audio.

Reuses the DSP primitives and loudness-matched mastering of generate-cinematic-sfx.py.
Each sound has a sheltered copy: the same mastered take through a real two-pole 140 Hz
low-pass, never normalized separately. Requires numpy, scipy and ffmpeg/ffprobe on PATH;
--check validates the installed assets without writing them.
"""
import argparse
from array import array
import importlib.util
import json
import math
import os
from pathlib import Path
import shutil

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/stellar"
SOUNDS = ASSETS / "sounds"
TEMP = Path(os.environ.get("LOCALAPPDATA", "")) / "Temp/opencode"
spec = importlib.util.spec_from_file_location("cinematic_sfx", Path(__file__).with_name("generate-cinematic-sfx.py"))
sfx = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sfx)
RATE, TAU = sfx.RATE, math.tau
SYNTHS = {}


def sound(seconds, seed, level):
    def register(function):
        SYNTHS[function.__name__] = (seconds, seed, level, function.__name__.endswith("_loop"), function)
        return function
    return register


def lowpass(signal, cutoff, periodic):
    coefficient = 1 - math.exp(-TAU * cutoff / RATE)
    first = second = 0.0
    if periodic:
        for value in signal[-8192:]:
            first += coefficient * (value - first)
            second += coefficient * (first - second)
    output = array("d")
    for value in signal:
        first += coefficient * (value - first)
        second += coefficient * (first - second)
        output.append(second)
    return output


@sound(3.2, 1401, -11.8)
def pressure_hit(t, rng):
    """The front reaches the witness at 0 s: N-wave crack, chest punch, a roaring blast wind, then thunder rolling
    back off distant terrain (0.85, 1.55, 2.3 s)."""
    n = len(t)
    crack = sfx.norm(sfx.hp(sfx.n_wave(t, 0, 0.03), 60)) + 0.4 * sfx.norm(sfx.hp(sfx.white(n, rng), 1500)) * sfx.hit(t, 0, 0.02, 0.0005)
    punch = sfx.thud(t, 0, 70, 24, 0.9, 0.08)
    blast = sfx.norm(sfx.svf(sfx.white(n, rng) + 2 * sfx.pink(n, rng), sfx.ramp(t, 0, 1.8, 4500, 140, True), 0.7))
    blast *= sfx.hit(t, 0.005, 0.6, 0.006) * sfx.flutter(n, rng, 9, 0.4)
    roar = sfx.norm(sfx.svf(sfx.pink(n, rng) + sfx.brown(n, rng), sfx.ramp(t, 0, 3.0, 900, 200, True), 0.7))
    roar *= sfx.flutter(n, rng, 3, 0.6) * sfx.hit(t, 0.1, 1.4, 0.25)
    rattle = sfx.norm(sfx.grains(t, rng, lambda x: 300 * math.exp(-x / 0.5) + 2, 1200, 6000, width=0.003))
    dry = sfx.drive(1.0 * punch + 0.7 * crack + 0.8 * blast + 0.6 * roar + 0.12 * rattle, 2.0)
    rolled = dry + sfx.echoes(dry, ((0.85, 0.35), (1.55, 0.25), (2.3, 0.15)), 700)
    return sfx.reverb(rolled, rng, 3.0, 0.32, 4000, 600)


@sound(2.0, 1402, -16.6)
def rumble_loop(t, rng):
    """Rolling bass after the front: deep thunder bed with gritty low mids that still read on small speakers."""
    n = len(t)
    rumble = sfx.norm(sfx.lp(sfx.brown(n, rng), 120, 2, True)) * sfx.flutter(n, rng, 2.5, 0.5, True)
    tone = sfx.norm(sfx.sine(t, 36) + 0.5 * sfx.sine(t, 36.5) + 0.35 * sfx.sine(t, 54) + 0.2 * sfx.sine(t, 72.5))
    tone *= 0.8 + 0.2 * np.sin(TAU * 0.5 * t)
    grit = sfx.norm(sfx.bp(sfx.pink(n, rng), 200, 900, 2, True)) * sfx.flutter(n, rng, 4, 0.6, True)
    return sfx.reverb(0.9 * rumble + 0.45 * tone + 0.25 * grit, rng, 1.5, 0.25, periodic=True)


@sound(2.0, 1403, -13.8)
def wind_loop(t, rng):
    """Dust storm: gusting body, a moving howl, hiss and grit. The low body keeps the sheltered copy audible."""
    n = len(t)
    gust = sfx.flutter(n, rng, 1.5, 0.6, True)
    body = sfx.norm(sfx.bp(sfx.pink(n, rng), 90, 900, 2, True)) * gust
    howl = sfx.norm(sfx.svf(sfx.white(n, rng), 650 * (1 + 0.25 * np.sin(TAU * 0.5 * t) + 0.1 * np.sin(TAU * 1.5 * t)), 6, "bp", True))
    hiss = sfx.norm(sfx.bp(sfx.white(n, rng), 1500, 8000, 2, True)) * (0.6 + 0.4 * gust)
    dust = sfx.norm(sfx.grains(t, rng, lambda x: 400, 2500, 9000, width=0.001, periodic=True)) * gust
    return 0.6 * body + 0.25 * howl * gust + 0.7 * hiss + 0.2 * dust


SPECS = {name: (seconds, seed) for name, (seconds, seed, *_) in SYNTHS.items()}
# Maximum momentary loudness of the open take, matched to the 1.1.0 mix.
LEVELS = {name: level for name, (_, _, level, *_) in SYNTHS.items()}


def synthesize(name, seconds, seed):
    opened = array("d", sfx.master(name, SYNTHS)[0])
    # Real two-pole filtering, not a pitch shift presented as sound occlusion.
    muffled = lowpass(opened, 140, name.endswith("_loop"))
    mean = sum(muffled) / len(muffled)
    return opened, array("d", ((value - mean) * 0.95 for value in muffled))


def generate():
    if not SOUNDS.is_dir():
        raise RuntimeError("Missing project sounds directory")
    for name, (seconds, seed) in SPECS.items():
        for suffix, signal in zip(("", "_muffled"), synthesize(name, seconds, seed)):
            sfx.encode(np.asarray(signal), SOUNDS / ("supernova_" + name + suffix + ".ogg"))


def high_frequency_energy(values):
    """Mean windowed FFT energy above 1 kHz; bass is not a proxy for treble."""
    size = 1024
    window = [0.5 - 0.5 * math.cos(TAU * i / (size - 1)) for i in range(size)]
    first_bin = math.ceil(1000 * size / RATE)
    energy = blocks = 0
    for start in range(0, len(values) - size + 1, size):
        spectrum = [complex(values[start + i] * window[i]) for i in range(size)]
        j = 0
        for i in range(1, size):
            bit = size >> 1
            while j & bit:
                j ^= bit
                bit >>= 1
            j ^= bit
            if i < j:
                spectrum[i], spectrum[j] = spectrum[j], spectrum[i]
        width = 2
        while width <= size:
            step = complex(math.cos(-TAU / width), math.sin(-TAU / width))
            for base in range(0, size, width):
                weight = 1
                for i in range(width // 2):
                    left = spectrum[base + i]
                    right = spectrum[base + i + width // 2] * weight
                    spectrum[base + i] = left + right
                    spectrum[base + i + width // 2] = left - right
                    weight *= step
            width *= 2
        energy += sum(value.real ** 2 + value.imag ** 2 for value in spectrum[first_bin:size // 2 + 1]) / size ** 2
        blocks += 1
    return energy / max(1, blocks)


def validate_filtered_pair(opened, muffled, loop):
    reference = lowpass(opened, 140, loop)
    mean = sum(reference) / len(reference)
    reference = [(value - mean) * 0.95 for value in reference]
    assert len(reference) == len(muffled), "Filtered recording duration or alignment changed"
    reference_power = sum(value * value for value in reference)
    muffled_power = sum(value * value for value in muffled)
    assert reference_power / len(reference) > 1e-10, "Filtered reference is below the useful signal floor"
    gain = math.sqrt(muffled_power / reference_power)
    correlation = sum(a * b for a, b in zip(reference, muffled)) / math.sqrt(reference_power * muffled_power)
    # Codec loss is allowed; a quiet filtered wind must not be independently boosted to an arbitrary peak.
    assert 0.85 < gain < 1.15, "Sheltered recording level differs from its unnormalized 140 Hz reference"
    assert correlation > 0.85, "Sheltered recording must come from the same aligned take"
    opened_high = high_frequency_energy(opened)
    muffled_high = high_frequency_energy(muffled)
    assert muffled_high < opened_high * 0.15, "Sheltered recording must suppress power above 1 kHz"
    return muffled_high / opened_high, gain, correlation


def validate():
    manifest = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    for name, (seconds, _) in SPECS.items():
        decoded = []
        for suffix in ("", "_muffled"):
            event = name + suffix
            assert manifest["supernova." + event]["sounds"] == ["stellar:supernova_" + event]
            path = SOUNDS / ("supernova_" + event + ".ogg")
            metadata = json.loads(sfx.command(["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)]))
            stream = metadata["streams"][0]
            assert stream["codec_name"] == "vorbis" and stream["channels"] == 1 and int(stream["sample_rate"]) == RATE
            assert abs(float(metadata["format"]["duration"]) - seconds) < 0.025
            values = sfx.decode(path).tolist()
            assert values and all(math.isfinite(value) for value in values)
            peak = max(abs(value) for value in values)
            rms = math.sqrt(sum(value * value for value in values) / len(values))
            assert 0 < peak < 0.95 and rms > 0 and abs(sum(values) / len(values)) < 0.01
            if not suffix:
                assert peak > 0.08 and rms > 0.01
            if name.endswith("_loop"):
                assert abs(values[-1] - values[0]) < 0.08, "Audible loop seam: " + event
            decoded.append(values)
            print(f"{event}: mono Vorbis {RATE} Hz, {seconds:.1f}s, peak={peak:.3f}, RMS={rms:.3f}")
        high, gain, correlation = validate_filtered_pair(*decoded, name.endswith("_loop"))
        print(f"{name}: sheltered/open power above 1 kHz={high:.5f}, reference gain={gain:.3f}, correlation={correlation:.3f}")
    print("PASS: six assets, bounded levels, loop seams and measured sheltered low-pass filtering.")


def validate_levels():
    for name, (seconds, _) in SPECS.items():
        loop = name.endswith("_loop")
        measured = sfx.loudness(sfx.decode(SOUNDS / ("supernova_" + name + ".ogg")), loop)
        assert abs(measured - LEVELS[name]) < 1.0, f"supernova_{name}: {measured:.1f} LUFS, target {LEVELS[name]:.1f}"
        print(f"supernova_{name}: {measured:.1f} LUFS (target {LEVELS[name]:.1f})")
    print("PASS: open takes loudness-matched to the 1.1.0 mix.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for executable in ("ffmpeg", "ffprobe"):
        if not shutil.which(executable):
            parser.error(executable + " must be on PATH")
    if not args.check:
        if (ROOT / "docs/supernova-audio-sources.json").exists():
            parser.error("Imported Supernova recordings are installed. Synthetic generation would overwrite them; "
                         "use --check or import-supernova-sfx.py instead. Move the import receipt explicitly "
                         "before restoring synthetic fallback audio.")
        generate()
    validate()
    validate_levels()


if __name__ == "__main__":
    main()
