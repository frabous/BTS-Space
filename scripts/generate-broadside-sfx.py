#!/usr/bin/env python3
"""Original SS-03B Broadside Command SFX; synthesized, no sampled or downloaded audio.

Reuses the DSP primitives of generate-asteroid-puller-sfx.py. Requires numpy, scipy and
ffmpeg/ffprobe on PATH; --check validates the installed assets without writing them.
BroadsideAudio plays the gun, shell and impact sounds positionally, delayed by the speed of
sound; their accents sit at 0 s so the delay lands on the visual event. broadside_board peaks
at 0.6 s (the cue starts 12 ticks before the transfer). Loops repeat exactly over one cycle.
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


# ------------------------------------------------------------------ guns

@sound(1.8, 4101)
def broadside_fire(t, rng):
    """Coilgun report at 0 s: field snap, muzzle blast, barrel run-out clank, coil discharge and hiss."""
    n = len(t)
    snap = norm(hp(white(n, rng), 3000)) * hit(t, 0, 0.006, 0.0003)
    zap = sine(t, 2600 * np.exp(-t / 0.035) + 160) * hit(t, 0, 0.12, 0.0005)
    blast = norm(svf(white(n, rng) + pink(n, rng), ramp(t, 0, 0.45, 7000, 260, True), 0.7)) * hit(t, 0.002, 0.16, 0.001)
    boom = thud(t, 0, 105, 36, 0.4, 0.05)
    clank = dsp.clank(t, 0.035, 132, rng, 0.45) + 0.6 * dsp.clank(t, 0.05, 207, rng, 0.3)
    hiss = norm(hp(white(n, rng), 2400)) * window(t, 0.08, 0.7, 0.12) * 0.6
    whine = sine(t, 1900 * np.exp(-t / 0.3) + 520) * hit(t, 0, 0.35, 0.004)
    dry = drive(1.0 * boom + 0.85 * blast + 0.5 * snap + 0.3 * norm(zap) + 0.35 * norm(clank) + 0.12 * hiss + 0.06 * whine, 1.7)
    return reverb(dry + echoes(dry, ((0.18, 0.25), (0.41, 0.15)), 2200), rng, 1.6, 0.22)


@sound(3.2, 4102, peak=0.7)
def broadside_fire_far(t, rng):
    """The same report kilometres away: dull shock at 0 s, low boom and terrain echoes."""
    n = len(t)
    shock = np.zeros(n)
    on = t < 0.05
    shock[on] = 1 - 2 * t[on] / 0.05
    shock = lp(shock, 1100)
    boom = thud(t, 0, 60, 27, 0.7, 0.08)
    roll = norm(lp(brown(n, rng), 160)) * flutter(n, rng, 4, 0.5) * hit(t, 0.08, 1.0, 0.12)
    dry = norm(shock) + 0.8 * boom + 0.5 * roll
    return reverb(dry + echoes(dry, ((0.6, 0.35), (1.1, 0.22), (1.7, 0.12)), 700), rng, 2.6, 0.32, 3000, 600)


@sound(1.0, 4103, peak=0.75)
def broadside_shell_flyby(t, rng):
    """Supersonic slug passing at 0 s: N-wave crack, then a receding plasma whoosh."""
    n = len(t)
    wave_ = np.zeros(n)
    on = t < 0.008
    wave_[on] = 1 - 2 * t[on] / 0.008
    crack = norm(hp(wave_, 300))
    whoosh = norm(svf(pink(n, rng) + 0.4 * white(n, rng), ramp(t, 0, 0.8, 4200, 500, True), 1.3, "bp")) * hit(t, 0, 0.25, 0.01)
    buzz = norm(sine(t, ramp(t, 0, 0.6, 980, 610, True)) * (0.6 + 0.4 * np.sign(sine(t, 61)))) * hit(t, 0, 0.18, 0.004)
    return reverb(1.0 * crack + 0.55 * whoosh + 0.12 * buzz, rng, 1.0, 0.18)


@sound(2.2, 4104)
def broadside_impact(t, rng):
    """Shell lands at 0 s: plasma flash crack, ground blast, debris patter and a hot sizzle."""
    n = len(t)
    crack = norm(hp(white(n, rng), 1600)) * hit(t, 0, 0.012, 0.0004)
    blast = norm(svf(white(n, rng) + pink(n, rng), ramp(t, 0, 0.7, 6500, 180, True), 0.7)) * hit(t, 0.002, 0.28, 0.002)
    boom = thud(t, 0, 85, 30, 0.6, 0.06)
    debris = norm(grains(t, rng, lambda x: 160 * math.exp(-x / 0.35) + 2, 900, 5000, width=0.005))
    sizzle = norm(grains(t, rng, lambda x: 260 * math.exp(-x / 0.5) + 1, 3500, 9500)) * smooth(t / 0.05)
    rumble = norm(lp(brown(n, rng), 120)) * hit(t, 0.02, 0.8, 0.05)
    dry = drive(1.0 * boom + 0.85 * blast + 0.45 * crack + 0.3 * debris + 0.12 * sizzle + 0.4 * rumble, 1.6)
    return reverb(dry, rng, 2.0, 0.25)


@sound(3.4, 4105, peak=0.7)
def broadside_impact_far(t, rng):
    """Distant impact: a thump at 0 s and a long rolling rumble off the terrain."""
    n = len(t)
    thump = thud(t, 0, 70, 28, 0.6, 0.07) + 0.5 * norm(lp(white(n, rng), 600)) * hit(t, 0, 0.08, 0.002)
    roll = norm(lp(brown(n, rng), 140)) * flutter(n, rng, 3, 0.6) * hit(t, 0.05, 1.3, 0.1)
    dry = 0.9 * norm(thump) + 0.6 * roll
    return reverb(dry + echoes(dry, ((0.7, 0.3), (1.3, 0.2), (2.0, 0.1)), 600), rng, 2.8, 0.3, 2600, 500)


@sound(1.5, 4106)
def broadside_reload(t, rng):
    """Breech cycle: unlock 0 s, slug rammed 0.05-0.33 s, lock 0.38 s, coils recharge, ready chirp 1.22 s."""
    n = len(t)
    unlock = dsp.clank(t, 0, 310, rng, 0.25)
    ram = norm(bp(white(n, rng), 700, 2600)) * window(t, 0.05, 0.33, 0.04) * (1 + 0.5 * np.sin(TAU * 31 * t))
    lock = dsp.clank(t, 0.38, 180, rng, 0.4) + 0.7 * thud(t, 0.38, 120, 60, 0.12)
    f = ramp(t, 0.42, 1.18, 280, 1450, True)
    charge = norm(sine(t, f) + 0.4 * sine(t, 2 * f) + 0.15 * saw(t, f / 2, 12)) * window(t, 0.42, 1.2, 0.08)
    chirp = (sine(t, 2093) * window(t, 1.22, 1.29, 0.01) + sine(t, 2794) * window(t, 1.31, 1.4, 0.01))
    mix = 0.5 * norm(unlock) + 0.35 * ram + 0.6 * norm(lock) + 0.18 * charge + 0.16 * chirp
    return reverb(mix, rng, 1.2, 0.22, 6500, 1800)


@sound(1.6, 4107, peak=0.6, loop=True)
def broadside_servo_loop(t, rng):
    """Traverse drive: motor whine, gearbox hum and ratchet; the soundtrack sets level and pitch by slew rate."""
    n = len(t)
    whine = sine(t, 220) + 0.45 * sine(t, 440) + 0.25 * sine(t, 661.25) + 0.12 * sine(t, 880)
    whine = norm(whine) * (0.85 + 0.15 * np.sin(TAU * 5 * t))
    gear = norm(sine(t, 55) + 0.6 * sine(t, 110) + 0.3 * sine(t, 165)) * (0.75 + 0.25 * np.sin(TAU * 2.5 * t))
    clicks = np.zeros(n)
    for k in range(40):
        clicks[int((k + 0.5) * n / 40)] = 0.6 + 0.4 * rng.uniform()
    ratchet = norm(bp(clicks, 1800, 5200, 2, True))
    hiss = norm(bp(white(n, rng), 2500, 7000, 2, True))
    mix = 0.45 * whine + 0.55 * gear + 0.18 * ratchet + 0.05 * hiss
    return reverb(mix, rng, 0.6, 0.15, periodic=True)


# ------------------------------------------------------------- gondola

@sound(2.0, 4201, peak=0.6, loop=True)
def broadside_bridge_loop(t, rng):
    """Gondola bed: hull hum through the pylon, air handling, and sparse telemetry blips."""
    n = len(t)
    hum = sum(a * sine(t, f) for f, a in ((41, 1), (82, 0.5), (123, 0.25), (246, 0.08)))
    hum = norm(hum) * (1 + 0.08 * np.sin(TAU * 0.5 * t))
    air = norm(lp(pink(n, rng), 900, 2, True)) * (0.9 + 0.1 * np.sin(TAU * 1.5 * t))
    blips = np.zeros(n)
    for start, freq in ((0.31, 1568), (0.37, 2093), (1.12, 1318.5), (1.64, 1760)):
        blips += sine(t, freq) * window(t, start, start + 0.045, 0.006)
    whine = sine(t, 3200) * 0.5
    mix = 0.6 * hum + 0.45 * air + 0.08 * blips + 0.02 * whine
    return reverb(mix, rng, 0.8, 0.2, periodic=True)


@sound(2.6, 4202)
def broadside_board(t, rng):
    """Transfer beam: rising shimmer, the transfer at 0.6 s, arrival clunk 0.75 s, pressurisation hiss."""
    n, at = len(t), 0.6
    before = (t < at).astype(float) * (1 - smooth((t - at + 0.01) / 0.01))
    u = np.clip(t / at, 0, 1)
    shimmer = norm(svf(pink(n, rng), ramp(t, 0, at, 400, 5200, True), 2.2, "bp")) * u ** 2 * before
    f = ramp(t, 0, at, 110, 440, True)
    tone = norm(sine(t, f) + 0.5 * sine(t, f * 1.5) + 0.3 * sine(t, f * 2.01)) * u ** 2 * before
    whoosh = norm(svf(white(n, rng), ramp(t, at, at + 0.5, 6000, 400, True), 0.8)) * hit(t, at, 0.2, 0.004)
    flash = norm(hp(white(n, rng), 2500)) * hit(t, at, 0.03, 0.0005)
    clunk = dsp.clank(t, 0.75, 150, rng, 0.6) + thud(t, 0.75, 90, 45, 0.2)
    hiss = norm(hp(white(n, rng), 3000)) * window(t, 0.8, 1.9, 0.25)
    hum = norm(sine(t, 220) + 0.4 * sine(t, 330)) * hit(t, at, 0.9, 0.05)
    mix = 0.55 * shimmer + 0.35 * tone + 0.7 * whoosh + 0.4 * flash + 0.6 * norm(clunk) + 0.15 * hiss + 0.25 * hum
    return reverb(mix, rng, 1.8, 0.28)


@sound(0.4, 4203, peak=0.6)
def broadside_lock(t, rng):
    """Fire-control acquisition: relay click and two rising tones."""
    n = len(t)
    click = norm(hp(white(n, rng), 3000)) * hit(t, 0, 0.004, 0.0003)
    tones = sine(t, 1760) * window(t, 0.01, 0.08, 0.008) + sine(t, 2349) * window(t, 0.1, 0.19, 0.008)
    return reverb(0.35 * click + 0.8 * tones, rng, 0.4, 0.15, 8000, 3000)


# -------------------------------------------------------------- encode/check

def render(name):
    seconds, seed, peak, loop, function = SYNTHS[name]
    rng = np.random.default_rng(seed)
    t = axis(seconds)
    x = function(t, rng)
    if loop:
        spectrum = np.fft.rfft(x)
        spectrum[np.fft.rfftfreq(len(x), 1 / RATE) < 22] = 0
        x = np.fft.irfft(spectrum, len(x))
    else:
        x = hp(x, 22)
        x *= np.minimum(1, t / 0.002) * (1 - smooth((t - seconds + 0.08) / 0.08))
    x -= np.mean(x)
    return x * peak / np.max(np.abs(x))


def generate(names):
    with tempfile.TemporaryDirectory(prefix="broadside-sfx-") as temporary:
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
        seam = abs(float(values[-1]) - float(values[0]))
        if loop:
            assert seam < 0.05, f"Audible loop seam: {name} ({seam:.3f})"
        else:
            assert abs(float(values[-1])) < 0.02, f"{name}: tail is not faded"
        print(f"{name}: mono Vorbis {RATE} Hz, {duration:.2f}s, peak={level:.3f}, RMS={rms:.3f}"
              + (f", seam={seam:.4f}" if loop else ""))
    print(f"PASS: {len(SYNTHS)} SS-03B sounds, registered, bounded levels, faded tails and seamless loops.")


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
