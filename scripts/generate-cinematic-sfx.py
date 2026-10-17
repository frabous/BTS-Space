#!/usr/bin/env python3
"""Original Railgun SFX, plus the mastering shared by the Blackhole, Moon Attract and Supernova sets.

Synthesized from oscillators and noise with the DSP primitives of
generate-asteroid-puller-sfx.py; no recorded, sampled or downloaded audio.
Requires numpy, scipy and ffmpeg/ffprobe on PATH. --check validates the installed
assets without writing them, --only regenerates a subset, --verify-reproducible
renders and encodes everything twice and compares the bytes with the installed files.

Every sound is mastered to a maximum momentary loudness (BS.1770 K-weighting,
400 ms windows) equal to the cue it replaced in the 1.1.0 mix, so the volumes set
in the soundtrack classes keep their balance. Loops are built circularly (whole
cycles, circular noise and filter state) and are never faded; the game supplies
their envelope and pitch.
"""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import shutil
import tempfile
import wave

import numpy as np
from scipy.signal import lfilter

spec = importlib.util.spec_from_file_location("carrier_sfx", Path(__file__).with_name("generate-asteroid-puller-sfx.py"))
dsp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dsp)
from_dsp = ("RATE", "TAU", "ROOT", "ASSETS", "SOUNDS", "axis", "norm", "smooth", "ramp", "hit", "window", "phase", "sine",
            "saw", "white", "pink", "brown", "lp", "hp", "bp", "svf", "flutter", "grains", "thud", "clank", "echoes",
            "reverb", "drive", "command")
globals().update({name: getattr(dsp, name) for name in from_dsp})
CEILING = 0.84  # leaves room for Vorbis overshoot on bright transients
SYNTHS = {}


def sound(seconds, seed, level, loop=False, registry=SYNTHS):
    def register(function):
        registry[function.__name__] = (seconds, seed, level, loop, function)
        return function
    return register


# ---------------------------------------------------------------- primitives

def n_wave(t, start, length):
    """Shock-front pressure signature: sharp rise, linear fall through zero, sharp return."""
    out = np.zeros(len(t))
    on = (t >= start) & (t < start + length)
    out[on] = 1 - 2 * (t[on] - start) / length
    return out


def swell(n, rng, rate, periodic=False):
    """Slow random motion in [-1, 1]."""
    return norm(lp(white(n, rng), rate, 2, periodic))


def whole(frequency, seconds):
    """Nearest frequency with a whole number of cycles per loop."""
    return round(frequency * seconds) / seconds


# ------------------------------------------------------------------ railgun

@sound(2.7, 1101, -13.1)
def ascent(t, rng):
    """Lock pulse at 0 s; a turbine whine and rushing air climb into orbit and thin to an airy release."""
    n = len(t)
    pulse = thud(t, 0, 110, 40, 0.3, 0.04) + 0.5 * norm(lp(white(n, rng), 1200)) * hit(t, 0, 0.05, 0.001)
    hum = norm(lp(saw(t, 55, 30) + saw(t, 55.4, 30), 900)) * (1 + 0.3 * np.sin(TAU * 7 * t)) * hit(t, 0, 1.1, 0.01)
    f = ramp(t, 0.05, 2.5, 190, 1600, True)
    whine = norm(sine(t, f) + 0.5 * sine(t, 2 * f) + 0.22 * sine(t, 3.02 * f) + 0.35 * sine(t, f / 2))
    whine *= flutter(n, rng, 10, 0.2) * smooth((t - 0.05) / 0.6) * (1 - smooth((t - 1.9) / 0.75))
    rush = norm(svf(pink(n, rng) + 0.35 * white(n, rng), ramp(t, 0, 2.4, 260, 6000, True), 1.0, "bp"))
    rush *= flutter(n, rng, 7, 0.35) * smooth(t / 0.4) * (0.45 + 0.55 * np.clip(t / 1.9, 0, 1)) * (1 - smooth((t - 1.95) / 0.7))
    release = norm(hp(white(n, rng), 4500)) * window(t, 1.9, 2.7, 0.35)
    body = norm(lp(brown(n, rng), 120)) * (0.4 + 0.6 * hit(t, 0, 0.8, 0.02)) * (1 - smooth((t - 1.6) / 1.0))
    mix = 0.85 * pulse + 0.3 * hum + 0.28 * whine + 0.8 * rush + 0.1 * release + 0.5 * body
    return reverb(drive(mix, 1.3), rng, 1.6, 0.2)


@sound(1.9, 1102, -16.2)
def warp(t, rng):
    """Vacuum inhale, an accelerating whoosh cresting into tearing air at 0.55 s, then a distant resonance."""
    n, crest = len(t), 0.55
    inhale = norm(svf(pink(n, rng), ramp(t, 0, 0.3, 5000, 700, True), 2.0, "bp"))
    inhale *= smooth(t / 0.25) * (1 - smooth((t - 0.22) / 0.12))
    cutoff = np.where(t < crest, ramp(t, 0.1, crest, 220, 5200, True), ramp(t, crest, 1.4, 5200, 300, True))
    sweep = norm(svf(pink(n, rng) + 0.5 * white(n, rng), cutoff, 1.3, "bp"))
    sweep *= smooth((t - 0.08) / 0.4) ** 2 * np.exp(-np.maximum(t - crest, 0) / 0.18)
    f = np.where(t < crest, ramp(t, 0.05, crest, 90, 1100, True), ramp(t, crest, 1.9, 1100, 160, True))
    tone = norm(sine(t, f) + 0.6 * sine(t, f * 1.5 + 3) + 0.3 * saw(t, f / 2, 12))
    tone *= smooth((t - 0.05) / 0.45) * np.exp(-np.maximum(t - crest, 0) / 0.12)
    tear = norm(hp(white(n, rng), 2500)) * np.maximum(flutter(n, rng, 90, 1.2), 0) * hit(t, crest - 0.03, 0.12, 0.03)
    resonance = norm(sine(t, 55) + 0.5 * sine(t, 82.6) + 0.25 * sine(t, 110.3))
    resonance *= smooth((t - crest) / 0.15) * np.exp(-np.maximum(t - crest, 0) / 0.6)
    sub = sine(t, ramp(t, 0.3, 1.2, 60, 32, True)) * smooth((t - 0.3) / 0.25) * np.exp(-np.maximum(t - crest, 0) / 0.45)
    mix = 0.5 * inhale + 0.9 * sweep + 0.22 * tone + 0.35 * tear + 0.35 * resonance + 0.45 * sub
    return reverb(drive(mix, 1.4), rng, 1.8, 0.3)


@sound(2.2, 1103, -15.6)
def flyby(t, rng):
    """The camera skims the cannon rails: machinery approaches, passes at 0.8 s with a Doppler drop, recedes."""
    n, passing, speed, distance, sound_speed = len(t), 0.8, 80.0, 7.0, 343.0
    x = speed * (t - passing)
    r = np.sqrt(x * x + distance * distance)
    shift = sound_speed / (sound_speed + speed * x / r)
    near = distance / r
    hum = norm(sum(a * sine(t, f * shift) for f, a in ((62, 1), (124.5, 0.6), (187, 0.4), (251, 0.3), (377, 0.2))))
    whine = norm(sine(t, 1180 * shift) + 0.4 * sine(t, 1771 * shift))
    metal = norm(sum(a * sine(t, f * shift) for f, a in ((713, 1), (1046, 0.7), (1597, 0.5), (2380, 0.3))))
    metal *= flutter(n, rng, 14, 0.5)
    air = norm(svf(pink(n, rng) + 0.3 * white(n, rng), 400 + 5200 * near ** 2, 0.9)) * near ** 1.3
    rumble = norm(lp(brown(n, rng), 140)) * near
    mix = (0.55 * hum * near ** 0.8 + 0.12 * whine * near ** 1.6 + 0.12 * metal * near ** 2.2
           + 0.8 * air + 0.4 * rumble) * np.minimum(1, t / 0.05)
    return reverb(drive(mix, 1.2), rng, 1.4, 0.18)


@sound(2.0, 1104, -13.7, loop=True)
def charge_loop(t, rng):
    """Capacitor bank under tension: transformer buzz, coil resonance and sizzling plasma; the game drives the rise."""
    n = len(t)
    buzz = drive(sine(t, 100) + 0.5 * sine(t, 100.5) + 0.3 * sine(t, 200), 3) * (0.85 + 0.15 * np.sin(TAU * 16 * t))
    coil = norm(svf(white(n, rng), 1250, 9, "bp", True)) + 0.4 * norm(sine(t, 1250) + 0.5 * sine(t, 1875.5))
    sizzle = norm(grains(t, rng, lambda x: 900, 3500, 11000, width=0.0012, periodic=True))
    hiss = norm(hp(white(n, rng), 6000, 2, True)) * flutter(n, rng, 25, 0.3, True)
    sub = sine(t, 50)
    mix = 0.55 * buzz + 0.3 * norm(coil) + 0.25 * sizzle + 0.1 * hiss + 0.35 * sub
    return reverb(mix * flutter(n, rng, 25, 0.15, True), rng, 0.6, 0.15, periodic=True)


@sound(0.32, 1105, -22.2)
def electric_snap(t, rng):
    """One high-voltage arc: dry crack at 0 s, two re-strikes, a metallic zing and a sizzle gone by 0.3 s."""
    n = len(t)
    crack = norm(hp(white(n, rng), 1500)) * hit(t, 0, 0.006, 0.0002)
    pop = thud(t, 0, 380, 120, 0.03, 0.006)
    restrike = sum(a * norm(hp(white(n, rng), 2500)) * hit(t, start, 0.004, 0.0002) for start, a in ((0.018, 0.6), (0.041, 0.4)))
    buzz = norm(bp(white(n, rng), 1800, 9000)) * (0.5 + 0.5 * np.sign(np.sin(TAU * 120 * t))) * hit(t, 0, 0.05, 0.001)
    zing = norm(sine(t, 3150 - 900 * t) + 0.6 * sine(t, 4730 - 1300 * t) + 0.3 * sine(t, 6880)) * hit(t, 0.002, 0.07, 0.001)
    sizzle = norm(grains(t, rng, lambda x: 1400 * math.exp(-x / 0.07) + 10, 3000, 12000, width=0.0008)) * smooth(t / 0.003)
    mix = 1.0 * crack + 0.35 * pop + 0.5 * restrike + 0.3 * buzz + 0.22 * zing + 0.45 * sizzle
    return reverb(mix, rng, 0.35, 0.08)


@sound(2.0, 1106, -14.2)
def cannon_shot(t, rng):
    """Rail discharge at 0 s: field crack, sub recoil, a tearing plasma roar and two metallic rail reflections."""
    n = len(t)
    crack = norm(hp(n_wave(t, 0, 0.004), 400)) + 0.7 * norm(hp(white(n, rng), 2500)) * hit(t, 0, 0.01, 0.0002)
    zap = sine(t, 4200 * np.exp(-t / 0.03) + 160) * hit(t, 0, 0.18, 0.0003)
    recoil = thud(t, 0, 120, 30, 0.45, 0.05)
    roar = norm(svf(white(n, rng) + pink(n, rng), ramp(t, 0, 0.9, 7000, 500, True), 0.8))
    roar *= np.maximum(flutter(n, rng, 45, 0.9), 0) * hit(t, 0.003, 0.35, 0.004)
    rail = clank(t, 0.0, 410, rng, 0.5, partials=6, bright=0.8)
    ring = sum(a * sine(t, f) for f, a in ((1460, 1), (2210, 0.6), (3390, 0.35))) * hit(t, 0, 0.45, 0.002)
    ring *= 1 + 0.3 * np.sin(TAU * 11 * t)
    em = sine(t, 900 * np.exp(-t / 0.12) + 70) * hit(t, 0, 0.5, 0.002)
    dry = drive(1.0 * recoil + 0.7 * crack + 0.5 * norm(zap) + 0.8 * roar + 0.25 * norm(rail)
                + 0.08 * norm(ring) + 0.3 * norm(em), 1.8)
    return reverb(dry + echoes(dry, ((0.12, 0.3), (0.27, 0.18)), 2400), rng, 1.8, 0.25)


@sound(1.0, 1107, -16.0)
def tunnel(t, rng):
    """Hyperspeed plasma tunnel: air shear and a climbing whistle peak at 0.35 s, streaks whip past, clean exit."""
    n, peak = len(t), 0.35
    envelope = smooth(t / 0.12) * np.exp(-np.maximum(t - peak, 0) / 0.18)
    shear = norm(svf(pink(n, rng) + 0.6 * white(n, rng), ramp(t, 0, 0.7, 900, 6500, True), 1.4, "bp"))
    shear *= flutter(n, rng, 35, 0.4)
    f = ramp(t, 0, 0.7, 700, 2600, True)
    whistle = norm(sine(t, f) + 0.4 * sine(t, f * 1.49)) * flutter(n, rng, 15, 0.25)
    streaks = np.zeros(n)
    for start in sorted(rng.uniform(0.02, 0.6, 9)):
        d = t - start
        on = (d >= 0) & (d < 0.07)
        streak = TAU * np.cumsum(5200 * np.exp(-d[on] / 0.03) + 900) / RATE
        streaks[on] += np.sin(streak) * np.sin(np.pi * d[on] / 0.07) ** 2 * rng.uniform(0.4, 1)
    crackle = norm(grains(t, rng, lambda x: 250 * math.exp(-max(x - peak, 0) / 0.15) + 1, 2500, 9000))
    low = norm(lp(brown(n, rng), 90))
    mix = (0.8 * shear + 0.22 * whistle + 0.25 * norm(streaks) + 0.25 * crackle + 0.45 * low) * envelope
    return reverb(mix, rng, 0.7, 0.15)


@sound(1.1, 1108, -13.0)
def descent(t, rng):
    """The beam knifes down through the air: the whistle plunges within 0.08 s, then a thin ionized hiss fades."""
    n = len(t)
    f = 650 + 5200 * np.exp(-t / 0.035) - 250 * t
    whistle = norm(sine(t, f) + 0.5 * sine(t, 1.33 * f) + 0.25 * sine(t, 2.01 * f)) * hit(t, 0, 0.16, 0.002)
    tear = norm(svf(white(n, rng) + pink(n, rng), 7000 * np.exp(-t / 0.06) + 1200, 0.9, "bp"))
    tear *= hit(t, 0, 0.12, 0.002) * flutter(n, rng, 60, 0.6)
    hiss = norm(hp(white(n, rng), 4200)) * hit(t, 0.01, 0.35, 0.02) * flutter(n, rng, 20, 0.3)
    crackle = norm(grains(t, rng, lambda x: 500 * math.exp(-x / 0.2) + 1, 4000, 11000))
    body = norm(bp(pink(n, rng), 250, 900)) * hit(t, 0, 0.09, 0.002)
    mix = 0.45 * whistle + 0.8 * tear + 0.4 * hiss + 0.25 * crackle + 0.3 * body
    return reverb(mix, rng, 0.9, 0.15)


@sound(2.0, 1109, -16.8)
def impact_hit(t, rng):
    """Beam contact at 0 s: pressure slam, ionized crack, a short expanding shock front, low resonance gone by 2 s."""
    n = len(t)
    slam = thud(t, 0, 85, 26, 0.75, 0.06) + 0.5 * thud(t, 0.004, 160, 55, 0.18, 0.02)
    crack = norm(hp(n_wave(t, 0, 0.008), 250)) + 0.5 * norm(hp(white(n, rng), 2000)) * hit(t, 0, 0.012, 0.0003)
    front = norm(svf(white(n, rng) + 2 * pink(n, rng), ramp(t, 0, 1.0, 5500, 150, True), 0.7)) * hit(t, 0.003, 0.32, 0.004)
    rumble = norm(lp(brown(n, rng), 130)) * flutter(n, rng, 4, 0.5) * hit(t, 0.02, 0.9, 0.05)
    ion = norm(grains(t, rng, lambda x: 380 * math.exp(-x / 0.25) + 2, 3000, 10000))
    hum = norm(sine(t, 41 + 18 * np.exp(-t / 0.3)) + 0.5 * sine(t, 82 + 30 * np.exp(-t / 0.3))) * hit(t, 0, 1.1, 0.01)
    dry = drive(1.0 * slam + 0.5 * crack + 0.75 * front + 0.55 * rumble + 0.12 * ion + 0.35 * hum, 2.0)
    return reverb(dry, rng, 2.0, 0.28, 5000, 900)


@sound(2.0, 1110, -13.6, loop=True)
def beam_loop(t, rng):
    """Sustained energy column: power roar, plasma hiss, a focused metallic ring and slow pressure flutter."""
    n = len(t)
    hum = lp(saw(t, 45, 30) + saw(t, 45.5, 30) + 0.6 * saw(t, 90, 20), 700, 2, True)
    roar = norm(bp(pink(n, rng) + 0.5 * brown(n, rng), 70, 1400, 2, True)) * flutter(n, rng, 6, 0.3, True)
    hiss = norm(bp(white(n, rng), 2500, 9000, 2, True)) * flutter(n, rng, 18, 0.4, True)
    ring = norm(svf(white(n, rng), 740, 14, "bp", True)) + 0.4 * norm(sine(t, 740) + 0.5 * sine(t, 1110.5))
    pulse = 1 + 0.12 * np.sin(TAU * 1.5 * t) + 0.08 * np.sin(TAU * 0.5 * t + 1)
    mix = (0.6 * norm(hum) + 0.8 * roar + 0.2 * hiss + 0.08 * norm(ring)) * pulse
    return reverb(drive(mix, 1.3), rng, 0.9, 0.18, periodic=True)


@sound(2.6, 1111, -13.7)
def rumble_tail(t, rng):
    """The beam powers down: the rumble is already there and sinks, metal ringing evaporates, soft release at 1.7 s."""
    n = len(t)
    fade = 1 - smooth(t / 2.5)
    f = ramp(t, 0, 2.4, 52, 30, True)
    hum = norm(svf(saw(t, f, 20) + saw(t, f * 1.01, 20), ramp(t, 0, 2.2, 700, 110, True), 0.8)) * fade
    rumble = norm(lp(brown(n, rng), 110)) * flutter(n, rng, 3, 0.5) * fade
    hiss = norm(hp(white(n, rng), 3200)) * np.exp(-t / 0.5)
    ring = sum(a * sine(t, f0 * (1 - 0.04 * t)) * np.exp(-t / tau) for f0, a, tau in ((1320, 1, 0.9), (2050, 0.6, 0.7), (3410, 0.35, 0.5)))
    release = norm(lp(pink(n, rng), 600)) * window(t, 1.3, 2.4, 0.4)
    mix = (0.55 * hum + 0.85 * rumble + 0.1 * hiss + 0.04 * norm(ring) + 0.25 * release) * smooth(t / 0.03)
    return reverb(mix, rng, 2.4, 0.35, 4000, 700)


# ------------------------------------------------------------------ mastering

def k_weight(x):
    """BS.1770 pre-filter (+4 dB shelf near 1.5 kHz) and RLB high-pass (38 Hz) at 44.1 kHz."""
    a = 10 ** (4 / 40)
    w = TAU * 1500 / RATE
    c, s = math.cos(w), 2 * math.sqrt(a) * math.sin(w) / (2 / math.sqrt(2))
    x = lfilter([a * ((a + 1) + (a - 1) * c + s), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - s)],
                [(a + 1) - (a - 1) * c + s, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - s], x)
    w = TAU * 38 / RATE
    c, alpha = math.cos(w), math.sin(w)
    return lfilter([(1 + c) / 2, -(1 + c), (1 + c) / 2], [1 + alpha, -2 * c, 1 - alpha], x)


def loudness(x, loop=False):
    """Maximum momentary loudness in LUFS (400 ms windows every 100 ms; whole clip if shorter, circular for loops)."""
    y = k_weight(np.tile(x, 2))[len(x):] if loop else k_weight(x)
    power = y * y
    size, hop = round(0.4 * RATE), round(0.1 * RATE)
    if len(power) <= size:
        return -0.691 + 10 * math.log10(max(float(np.mean(power)), 1e-12))
    if loop:
        power, starts = np.concatenate([power, power[:size]]), np.arange(0, len(power), hop)
    else:
        starts = np.arange(0, len(power) - size + 1, hop)
    total = np.concatenate([[0.0], np.cumsum(power)])
    return -0.691 + 10 * math.log10(max(float(np.max(total[starts + size] - total[starts])) / size, 1e-12))


def limit(x, ceiling=CEILING):
    """Soft knee above 70 % of the ceiling; memoryless, so loops stay seamless."""
    knee = 0.7 * ceiling
    magnitude = np.abs(x)
    over = magnitude > knee
    y = x.copy()
    y[over] = np.sign(x[over]) * (knee + (ceiling - knee) * np.tanh((magnitude[over] - knee) / (ceiling - knee)))
    return y


def master(name, registry=SYNTHS):
    """Render one sound at its loudness target. Returns the samples and the limiter's peak reduction in dB."""
    seconds, seed, level, loop, function = registry[name]
    rng = np.random.default_rng(seed)
    t = axis(seconds)
    x = function(t, rng)
    if loop:
        spectrum = np.fft.rfft(x)
        spectrum[np.fft.rfftfreq(len(x), 1 / RATE) < 22] = 0
        x = np.fft.irfft(spectrum, len(x))
        # Start the cycle on its quietest, flattest sample: the codec's file edges then land on near-silence.
        x = np.roll(x, -int(np.argmin(np.abs(x) + 4 * np.abs(x - np.roll(x, 1)))))
    else:
        x = hp(x, 22)
        x *= np.minimum(1, t / 0.002) * (1 - smooth((t - seconds + 0.08) / 0.08))
    x = norm(x - np.mean(x))
    gain = 1.0
    for _ in range(12):
        y = limit(x * gain)
        error = level - loudness(y, loop)
        if abs(error) < 0.05:
            break
        gain *= 10 ** (error / 20)
    return y, 20 * math.log10(gain / np.max(np.abs(y)))


def encode(values, path):
    with tempfile.TemporaryDirectory(prefix="stellar-sfx-") as temporary:
        wav = Path(temporary) / "source.wav"
        with wave.open(str(wav), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(RATE)
            output.writeframes(np.round(values * 32767).astype("<i2").tobytes())
        command(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-fflags", "+bitexact", "-i", str(wav),
                 "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "5", "-flags:a", "+bitexact",
                 "-fflags", "+bitexact", str(path)])


def decode(path):
    return np.frombuffer(command(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-acodec", "pcm_f32le", "-"]),
                         "<f4").astype(float)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(path, seconds, level, loop):
    """Format, duration, level, DC, loop seam or faded tail of one installed OGG."""
    metadata = json.loads(command(["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)]))
    stream = metadata["streams"][0]
    assert stream["codec_name"] == "vorbis" and stream["channels"] == 1 and int(stream["sample_rate"]) == RATE, path
    duration = float(metadata["format"]["duration"])
    assert abs(duration - seconds) < 0.025, f"{path.name}: {duration:.3f}s"
    values = decode(path)
    assert values.size and np.all(np.isfinite(values)), path
    peak = float(np.max(np.abs(values)))
    measured = loudness(values, loop)
    assert 0.1 < peak < 0.95 and abs(float(np.mean(values))) < 0.01, f"{path.name}: peak={peak:.3f}"
    assert level is None or abs(measured - level) < 1.0, f"{path.name}: {measured:.1f} LUFS, target {level:.1f}"
    seam = abs(float(values[-1]) - float(values[0]))
    if loop:
        assert seam < 0.05, f"Audible loop seam: {path.name} ({seam:.3f})"
    else:
        assert abs(float(values[-1])) < 0.02, f"{path.name}: tail is not faded"
    return values, peak, measured, seam


def validate(registry, label):
    manifest = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    for name, (seconds, _, level, loop, _) in registry.items():
        assert manifest["cinematic." + name]["sounds"] == ["stellar:" + name], "sounds.json entry: " + name
        _, peak, measured, seam = check(SOUNDS / (name + ".ogg"), seconds, level, loop)
        print(f"{name}: mono Vorbis {RATE} Hz, {seconds:.2f}s, peak={peak:.3f}, {measured:.1f} LUFS (target {level:.1f})"
              + (f", seam={seam:.4f}" if loop else ""))
    print(f"PASS: {len(registry)} {label} sounds, registered, loudness-matched, faded tails and seamless loops.")


def run(registry, description, label):
    parser = argparse.ArgumentParser(description=description, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="validate installed assets without writing")
    parser.add_argument("--only", nargs="*", choices=sorted(registry), help="regenerate only these sounds")
    parser.add_argument("--verify-reproducible", action="store_true",
                        help="render and encode twice, compare with the installed files")
    args = parser.parse_args()
    for executable in ("ffmpeg", "ffprobe"):
        if not shutil.which(executable):
            parser.error(executable + " must be on PATH")
    if args.verify_reproducible:
        with tempfile.TemporaryDirectory(prefix="stellar-sfx-repeat-") as temporary:
            for name in args.only or list(registry):
                digests = set()
                for copy in ("first", "second"):
                    path = Path(temporary) / (copy + ".ogg")
                    encode(master(name, registry)[0], path)
                    digests.add(digest(path))
                assert len(digests) == 1, "Non-reproducible render or encode: " + name
                assert digests == {digest(SOUNDS / (name + ".ogg"))}, "Installed file differs from the generator: " + name
        print(f"PASS: {label} sounds reproduced byte-for-byte with this ffmpeg build.")
        return
    if not args.check:
        for name in args.only or list(registry):
            values, reduction = master(name, registry)
            encode(values, SOUNDS / (name + ".ogg"))
            print(f"{name}: rendered, limiter peak reduction {reduction:.1f} dB")
    validate(registry, label)


if __name__ == "__main__":
    run(SYNTHS, __doc__, "Railgun")
