#!/usr/bin/env python3
"""Original SS-03C Kinetic Rain SFX; synthesized, no sampled or downloaded audio.

Reuses the DSP primitives of generate-asteroid-puller-sfx.py. Requires numpy, scipy and
ffmpeg/ffprobe on PATH; --check validates the installed assets without writing them.
KineticRainAudio plays the rail launch and the impacts positionally, delayed by the speed of
sound, so their accents sit at 0 s. The rod scream starts one tick after launch and peaks at
1.72 s, as the rod lands; kinetic_hammer_fall starts at the clamp release and peaks at 2.45 s.
"""
import argparse
import importlib.util
import json
import math
from pathlib import Path
import shutil
import tempfile
import wave

import numpy as np

spec = importlib.util.spec_from_file_location("carrier_sfx", Path(__file__).with_name("generate-asteroid-puller-sfx.py"))
dsp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dsp)
from_dsp = ("RATE", "TAU", "ASSETS", "SOUNDS", "axis", "norm", "smooth", "ramp", "hit", "window", "phase", "sine", "saw",
            "white", "pink", "brown", "lp", "hp", "bp", "svf", "flutter", "grains", "thud", "clank", "echoes", "reverb",
            "drive", "command")
globals().update({name: getattr(dsp, name) for name in from_dsp})
SYNTHS = {}


def sound(seconds, seed, peak=0.82, loop=False):
    def register(function):
        SYNTHS[function.__name__] = (seconds, seed, peak, loop, function)
        return function
    return register


def n_wave(t, start, length):
    """Sonic boom pressure signature: a sharp rise, a linear fall through zero, a sharp return."""
    out = np.zeros(len(t))
    on = (t >= start) & (t < start + length)
    out[on] = 1 - 2 * (t[on] - start) / length
    return out


# ------------------------------------------------------------------ rods

@sound(1.0, 5101)
def kinetic_rod_launch(t, rng):
    """Rail salvo at 0 s: field snap, coil discharge, breech thunk, the rod's hiss leaving the bore."""
    n = len(t)
    snap = norm(hp(white(n, rng), 3500)) * hit(t, 0, 0.004, 0.0002)
    zap = sine(t, 3400 * np.exp(-t / 0.025) + 220) * hit(t, 0, 0.09, 0.0004)
    thunk = thud(t, 0, 140, 48, 0.22, 0.03)
    ring = dsp.clank(t, 0.004, 260, rng, 0.22, 5, 0.6)
    hum = norm(sine(t, 120) + 0.5 * sine(t, 240) + 0.3 * saw(t, 60, 10)) * hit(t, 0, 0.18, 0.002)
    hiss = norm(svf(white(n, rng), ramp(t, 0, 0.5, 9000, 1500, True), 0.9, "bp")) * hit(t, 0.01, 0.2, 0.004)
    dry = drive(0.9 * thunk + 0.55 * snap + 0.45 * norm(zap) + 0.3 * norm(ring) + 0.2 * hum + 0.35 * hiss, 1.5)
    return reverb(dry + echoes(dry, ((0.14, 0.2), (0.33, 0.1)), 2600), rng, 0.9, 0.2)


@sound(1.8, 5102)
def kinetic_rod_scream(t, rng):
    """Incoming rod: a tearing whistle that climbs and swells to the strike at 1.72 s."""
    n, at = len(t), 1.72
    u = np.clip(t / at, 0, 1)
    swell = u ** 3 * (1 - smooth((t - at) / 0.02))
    tear = norm(svf(pink(n, rng) + 0.5 * white(n, rng), ramp(t, 0, at, 700, 4200, True), 2.4, "bp")) * flutter(n, rng, 30, 0.35)
    f = ramp(t, 0, at, 900, 2300, True)
    whistle = norm(sine(t, f) + 0.35 * sine(t, f * 1.51) + 0.15 * sine(t, f * 2.03)) * flutter(n, rng, 12, 0.3)
    crackle = norm(grains(t, rng, lambda x: 30 + 260 * (x / at) ** 2, 2500, 9000))
    roar = norm(svf(brown(n, rng), ramp(t, 0, at, 120, 600, True))) * flutter(n, rng, 8, 0.4)
    crack = norm(hp(n_wave(t, at, 0.006), 300))
    mix = (0.75 * tear + 0.3 * whistle + 0.25 * crackle + 0.5 * roar) * swell + 0.5 * crack
    return reverb(mix, rng, 0.8, 0.12)


@sound(1.8, 5103)
def kinetic_rod_impact(t, rng):
    """Rod strikes at 0 s: shock crack, a deep ground blow, rock burst, debris rain and a hot sizzle."""
    n = len(t)
    crack = norm(hp(n_wave(t, 0, 0.012), 200)) + 0.5 * norm(hp(white(n, rng), 2200)) * hit(t, 0, 0.01, 0.0003)
    blow = thud(t, 0, 95, 26, 0.55, 0.04)
    burst = norm(svf(white(n, rng) + pink(n, rng), ramp(t, 0, 0.6, 7500, 160, True), 0.75)) * hit(t, 0.002, 0.24, 0.001)
    debris = norm(grains(t, rng, lambda x: 220 * math.exp(-x / 0.3) + 3, 700, 4800, width=0.006))
    sizzle = norm(grains(t, rng, lambda x: 300 * math.exp(-x / 0.45) + 2, 3800, 10000)) * smooth(t / 0.04)
    rumble = norm(lp(brown(n, rng), 110)) * hit(t, 0.02, 0.7, 0.04)
    dry = drive(1.0 * blow + 0.8 * burst + 0.55 * crack + 0.3 * debris + 0.1 * sizzle + 0.45 * rumble, 1.8)
    return reverb(dry, rng, 1.8, 0.24)


@sound(3.0, 5104, peak=0.7)
def kinetic_rod_impact_far(t, rng):
    """Distant strike: a dull thump at 0 s and thunder rolling off the terrain."""
    n = len(t)
    thump = thud(t, 0, 62, 24, 0.7, 0.07) + 0.4 * norm(lp(n_wave(t, 0, 0.05), 900))
    roll = norm(lp(brown(n, rng), 150)) * flutter(n, rng, 3, 0.6) * hit(t, 0.05, 1.1, 0.1)
    dry = 0.9 * norm(thump) + 0.6 * roll
    return reverb(dry + echoes(dry, ((0.6, 0.32), (1.15, 0.2), (1.8, 0.1)), 650), rng, 2.4, 0.3, 2600, 500)


# ------------------------------------------------------------------ hammer

@sound(2.5, 5201)
def kinetic_hammer_fall(t, rng):
    """Hammer falling free: a deepening roar and a low howl that swell to the strike at 2.45 s."""
    n, at = len(t), 2.45
    u = np.clip(t / at, 0, 1)
    swell = (0.08 + 0.92 * u ** 2.5) * (1 - smooth((t - at) / 0.02))
    roar = norm(lp(svf(pink(n, rng) + brown(n, rng), ramp(t, 0, at, 200, 900, True), 0.6, "bp"), 3000)) * flutter(n, rng, 10, 0.4)
    f = ramp(t, 0, at, 220, 640, True)
    howl = norm(sine(t, f) + 0.5 * sine(t, f * 1.5) + 0.25 * sine(t, f * 2.02)) * flutter(n, rng, 6, 0.3)
    sub = norm(sine(t, ramp(t, 0, at, 30, 46)) + 0.4 * norm(lp(brown(n, rng), 70)))
    crackle = norm(grains(t, rng, lambda x: 20 + 160 * (x / at) ** 2, 1500, 7000))
    mix = (0.8 * roar + 0.3 * howl + 0.55 * sub + 0.18 * crackle) * swell
    return reverb(mix, rng, 1.2, 0.15)


@sound(5.0, 5202)
def kinetic_hammer_impact(t, rng):
    """The hammer lands at 0 s: shock crack, a ground blow felt more than heard, a roaring blast,
    falling rock and long thunder rolling back off the terrain."""
    n = len(t)
    crack = norm(hp(n_wave(t, 0, 0.02), 150)) + 0.4 * norm(hp(white(n, rng), 1800)) * hit(t, 0, 0.02, 0.0003)
    blow = thud(t, 0, 70, 19, 1.4, 0.08) + 0.6 * thud(t, 0.012, 140, 40, 0.3, 0.03)
    blast = norm(svf(white(n, rng) + 2 * pink(n, rng), ramp(t, 0, 1.6, 6000, 120, True), 0.7)) * hit(t, 0.004, 0.7, 0.003)
    roar = norm(svf(pink(n, rng) + brown(n, rng), ramp(t, 0, 3, 1400, 180, True))) * flutter(n, rng, 5, 0.5) * hit(t, 0.05, 1.6, 0.08)
    debris = norm(grains(t, rng, lambda x: 160 * math.exp(-(x - 0.4) ** 2 / 0.6) + 4, 500, 4000, width=0.008, start=0.2))
    thunder = norm(lp(brown(n, rng), 160)) * flutter(n, rng, 2, 0.6) * hit(t, 0.3, 2.2, 0.4)
    dry = drive(1.0 * blow + 0.6 * crack + 0.75 * blast + 0.6 * roar + 0.25 * debris + 0.5 * thunder, 2.0)
    rolled = dry + echoes(dry, ((0.7, 0.35), (1.25, 0.25), (1.9, 0.16), (2.7, 0.09)), 800)
    return reverb(rolled, rng, 3.2, 0.32, 5000, 700)


# -------------------------------------------------------------- encode/check

def render(name):
    seconds, seed, peak, loop, function = SYNTHS[name]
    rng = np.random.default_rng(seed)
    t = axis(seconds)
    x = function(t, rng)
    x = hp(x, 22)
    x *= np.minimum(1, t / 0.002) * (1 - smooth((t - seconds + 0.08) / 0.08))
    x -= np.mean(x)
    return x * peak / np.max(np.abs(x))


def generate(names):
    with tempfile.TemporaryDirectory(prefix="kinetic-rain-sfx-") as temporary:
        wav = Path(temporary) / "source.wav"
        for name in names:
            samples = np.round(render(name) * 32767).astype("<i2")
            with wave.open(str(wav), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(RATE)
                output.writeframes(samples.tobytes())
            command(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-fflags", "+bitexact",
                     "-i", str(wav), "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "5",
                     "-flags:a", "+bitexact", str(SOUNDS / (name + ".ogg"))])


def validate():
    manifest = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    for name, (seconds, _, peak, loop, _) in SYNTHS.items():
        assert manifest["cinematic." + name]["sounds"] == ["stellar:" + name], "sounds.json entry: " + name
        path = SOUNDS / (name + ".ogg")
        metadata = json.loads(command(["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)]))
        stream = metadata["streams"][0]
        assert stream["codec_name"] == "vorbis" and stream["channels"] == 1 and int(stream["sample_rate"]) == RATE, path
        duration = float(metadata["format"]["duration"])
        assert abs(duration - seconds) < 0.025, f"{name}: {duration:.3f}s"
        values = np.frombuffer(command(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-acodec", "pcm_f32le", "-"]), "<f4")
        assert values.size and np.all(np.isfinite(values)), path
        level = float(np.max(np.abs(values)))
        rms = float(np.sqrt(np.mean(values.astype(float) ** 2)))
        assert 0.45 < level < 0.95 and rms > 0.02 and abs(float(np.mean(values))) < 0.01, f"{name}: peak={level:.3f} rms={rms:.3f}"
        assert abs(float(values[-1])) < 0.02, f"{name}: tail is not faded"
        print(f"{name}: mono Vorbis {RATE} Hz, {duration:.2f}s, peak={level:.3f}, RMS={rms:.3f}")
    print(f"PASS: {len(SYNTHS)} SS-03C sounds, registered, bounded levels and faded tails.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="validate installed assets without writing")
    parser.add_argument("--only", nargs="*", choices=sorted(SYNTHS), help="regenerate only these sounds")
    args = parser.parse_args()
    for executable in ("ffmpeg", "ffprobe"):
        if not shutil.which(executable):
            parser.error(executable + " must be on PATH")
    if not args.check:
        generate(args.only or list(SYNTHS))
    validate()


if __name__ == "__main__":
    main()
