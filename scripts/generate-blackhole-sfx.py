#!/usr/bin/env python3
"""Original Blackhole SFX; synthesized, no recorded, sampled or downloaded audio.

Reuses the DSP primitives and loudness-matched mastering of generate-cinematic-sfx.py.
Requires numpy, scipy and ffmpeg/ffprobe on PATH; --check validates the installed
assets without writing them. BlackholeSoundtrack plays the three loops together and
moves their pitch (bed 0.94-1, disk 0.80-1, strain 0.84-1.08); the landing thump of
blackhole_return sits at 2.0 s.
"""
import importlib.util
from pathlib import Path

import numpy as np

spec = importlib.util.spec_from_file_location("cinematic_sfx", Path(__file__).with_name("generate-cinematic-sfx.py"))
sfx = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sfx)
globals().update({name: getattr(sfx, name) for name in sfx.from_dsp + ("n_wave", "swell", "whole")})
SYNTHS = {}
BED, STRAIN = 7.75, 3.75


def sound(seconds, seed, level, loop=False):
    return sfx.sound(seconds, seed, level, loop, SYNTHS)


@sound(3.0, 2101, -16.4)
def blackhole_ascent(t, rng):
    """Gravitational lift: soft pressure pulse at 0 s, rushing air and a rising resonance near 1.1 s, then filtered away."""
    n = len(t)
    pulse = sine(t, 34 + 30 * np.exp(-t / 0.25)) * smooth(t / 0.03) * np.exp(-t / 0.6)
    cutoff = np.where(t < 1.1, ramp(t, 0, 1.1, 280, 4200, True), ramp(t, 1.1, 2.9, 4200, 160, True))
    air = norm(svf(pink(n, rng) + 0.25 * white(n, rng), cutoff, 0.8)) * flutter(n, rng, 5, 0.3)
    air *= smooth(t / 0.5) * (1 - smooth((t - 1.2) / 1.6))
    f = ramp(t, 0.1, 2.2, 70, 420, True)
    resonance = norm(norm(svf(pink(n, rng), f * 2, 7, "bp")) + 0.5 * norm(sine(t, f) + 0.4 * sine(t, 2 * f)))
    resonance *= smooth((t - 0.1) / 0.6) * (1 - smooth((t - 1.4) / 1.3))
    sub = norm(lp(brown(n, rng), 70)) * np.exp(-t / 1.0) * smooth(t / 0.05)
    mix = 0.7 * pulse + 0.85 * air + 0.3 * resonance + 0.5 * sub
    return reverb(mix, rng, 2.6, 0.35, 5000, 800)


@sound(BED, 2102, -12.9, loop=True)
def blackhole_space_bed(t, rng):
    """Deep space: a near-silent low drone, sparse glassy overtones and slow distant pressure swells."""
    n = len(t)
    drone = norm(sum(a * sine(t, whole(f, BED)) for f, a in ((41.2, 1), (61.8, 0.5), (82.6, 0.3), (123.6, 0.12))))
    drone *= 0.75 + 0.25 * swell(n, rng, 0.4, True)
    pressure = norm(lp(brown(n, rng), 80, 2, True)) * (0.5 + 0.5 * swell(n, rng, 0.3, True))
    shimmer = np.zeros(n)
    for f in (1240, 1861, 2483, 3107, 3725):
        vibrato = 3 * np.sin(TAU * whole(0.5, BED) * t + rng.uniform(0, TAU))
        shimmer += np.clip(swell(n, rng, 0.35, True), 0, 1) ** 3 * sine(t, whole(f, BED) + vibrato)
    hiss = norm(bp(white(n, rng), 3000, 9000, 2, True))
    mix = 0.6 * drone + 0.6 * pressure + 0.18 * norm(shimmer) + 0.03 * hiss
    return reverb(mix, rng, 3.0, 0.45, 6000, 900, periodic=True)


@sound(4.0, 2103, -16.1)
def blackhole_reveal(t, rng):
    """Reveal: a deep resonance blooms over 0.8 s and sags under its own mass, glassy shimmer above, dark tail by 4 s."""
    n = len(t)
    bloom = smooth(t / 0.8) * (1 - smooth((t - 1.2) / 2.6))
    sag = 1 - 0.05 * smooth(t / 2.5)
    low = sum(a * (sine(t, f * sag) + 0.7 * sine(t, f * sag * 1.004)) for f, a in ((36, 1), (54, 0.55), (72, 0.4), (108, 0.25)))
    low = drive(low, 1.8) * bloom
    pad = norm(svf(saw(t, 108 * sag, 30) + saw(t, 108.6 * sag, 30), 150 + 900 * window(t, 0.2, 3.0, 0.9), 1.2)) * bloom
    glass = sum(a * sine(t, f) * (1 + 0.25 * np.sin(TAU * 5.5 * t + k))
                for k, (f, a) in enumerate(((2210, 1), (3315, 0.6), (4970, 0.35), (2760, 0.5))))
    glass = norm(glass) * smooth((t - 0.35) / 0.9) * np.exp(-np.maximum(t - 1.3, 0) / 0.9)
    air = norm(svf(pink(n, rng), ramp(t, 0, 0.8, 300, 2500, True), 0.8)) * smooth(t / 0.7) * np.exp(-np.maximum(t - 0.8, 0) / 0.35)
    tail = norm(lp(brown(n, rng), 90)) * window(t, 0.6, 4.0, 1.2)
    mix = 0.75 * low + 0.3 * pad + 0.08 * glass + 0.3 * air + 0.35 * tail
    return reverb(mix, rng, 3.2, 0.4, 5000, 700)


@sound(BED, 2104, -20.0, loop=True)
def blackhole_accretion(t, rng):
    """Accretion disk: low rumble, swirling plasma whose band turns with the disk, soft hiss, slow metallic resonances."""
    n = len(t)
    rumble = norm(lp(brown(n, rng), 140, 2, True)) * (1 + 0.3 * swell(n, rng, 0.6, True))
    hum = norm(sine(t, whole(31, BED)) + 0.6 * sine(t, whole(62.2, BED)) + 0.3 * sine(t, whole(93.5, BED)))
    hum *= 0.8 + 0.2 * np.sin(TAU * whole(0.52, BED) * t)
    centre = 700 * 2 ** (0.9 * np.sin(TAU * whole(0.39, BED) * t) + 0.4 * np.sin(TAU * whole(1.16, BED) * t + 1))
    swirl = norm(svf(pink(n, rng) + 0.3 * white(n, rng), centre, 2.2, "bp", True)) * (1 + 0.35 * np.sin(TAU * whole(1.29, BED) * t))
    plasma = norm(grains(t, rng, lambda x: 160, 2500, 8000, periodic=True))
    hiss = norm(bp(white(n, rng), 1800, 7000, 2, True)) * (1 + 0.3 * swell(n, rng, 0.8, True))
    metal = sum(norm(svf(white(n, rng), whole(f, BED), 28, "bp", True)) * np.clip(swell(n, rng, 0.35, True), 0, 1) ** 2
                for f in (310, 467, 733, 1180))
    mix = 0.75 * rumble + 0.35 * hum + 0.45 * swirl + 0.08 * plasma + 0.1 * hiss + 0.12 * norm(metal)
    return reverb(drive(mix, 1.2), rng, 2.2, 0.3, periodic=True)


@sound(STRAIN, 2105, -17.6, loop=True)
def blackhole_tidal(t, rng):
    """Tidal strain: a stretched groan, bending metallic creaks, fluttering pressure and a thin glassy vibration."""
    n, cycle = len(t), 1 / STRAIN
    carrier = whole(48, STRAIN) + 2.5 * np.sin(TAU * cycle * t) + 1.2 * np.sin(TAU * 3 * cycle * t + 0.7)
    index = 2.2 + 1.3 * np.sin(TAU * 2 * cycle * t)
    groan = np.sin(phase(t, carrier) + index * np.sin(phase(t, whole(72.3, STRAIN))))
    groan = norm(bp(groan, 70, 800, 2, True)) * (0.75 + 0.25 * np.sin(TAU * 2 * cycle * t + 1))
    clicks = np.zeros(n)
    for start, length, rate in ((0.3, 0.35, 40), (1.6, 0.25, 55), (2.55, 0.45, 30)):
        x = start
        while x < start + length:
            clicks[int(x * RATE) % n] += rng.uniform(0.4, 1)
            x += 1 / (rate * (1 + (x - start) / length))
    bend = 1 + 0.15 * np.sin(TAU * cycle * t)
    creaks = norm(svf(clicks, 520 * bend, 7, "bp", True)) + 0.6 * norm(svf(clicks, 860 * bend, 9, "bp", True))
    pressure = norm(lp(brown(n, rng), 90, 2, True)) * flutter(n, rng, 7, 0.6, True)
    glass = sine(t, whole(2380, STRAIN) + 9 * np.sin(TAU * whole(6.4, STRAIN) * t)) * (0.6 + 0.4 * np.sin(TAU * cycle * t))
    sub = sine(t, whole(28, STRAIN)) * (0.6 + 0.4 * np.sin(TAU * cycle * t))
    mix = 0.75 * groan + 0.3 * norm(creaks) + 0.45 * pressure + 0.05 * glass + 0.3 * sub
    return reverb(mix, rng, 1.6, 0.25, periodic=True)


@sound(3.0, 2106, -14.6)
def blackhole_horizon(t, rng):
    """Event horizon: suction at once, a twisting pressure rush with stretched glass, soft collapse at 1.35 s, filtered away."""
    n, fall = len(t), 1.35
    suction = norm(svf(pink(n, rng) + 0.5 * white(n, rng), ramp(t, 0, 0.35, 4200, 700, True), 1.8, "bp"))
    suction *= smooth(t / 0.04) * (1 - smooth((t - 0.2) / 0.3))
    rate = ramp(t, 0.1, fall, 3, 22, True)
    rush = norm(svf(pink(n, rng) + 0.4 * white(n, rng), ramp(t, 0.1, fall, 250, 5200, True), 0.9))
    rush *= (1 + 0.6 * np.sin(phase(t, rate))) * smooth((t - 0.08) / 0.5) ** 1.5
    twist = norm(svf(pink(n, rng), 600 * 2 ** (2.5 * np.sin(phase(t, rate / 2))), 4, "bp")) * smooth((t - 0.1) / 0.6)
    glass = sum(a * sine(t, ramp(t, 0.1, fall, f0, f1, True)) for f0, f1, a in ((1500, 2600, 1), (2250, 3900, 0.6), (3100, 5200, 0.35)))
    glass = norm(glass) * smooth((t - 0.2) / 0.6) * (1 - smooth((t - fall + 0.1) / 0.15))
    closing = np.where(t < fall, 9000, ramp(t, fall, fall + 0.9, 9000, 120, True))
    body = svf(0.4 * suction + 0.8 * rush + 0.35 * twist + 0.08 * glass, closing, 0.7) * (1 - smooth((t - fall - 0.6) / 0.8))
    after = np.maximum(t - fall, 0)
    collapse = sine(t, 28 + 50 * np.exp(-after / 0.12)) * smooth((t - fall + 0.02) / 0.03) * np.exp(-after / 0.5)
    settle = norm(lp(brown(n, rng), 120)) * smooth((t - fall) / 0.05) * np.exp(-after / 0.45)
    return reverb(body + 0.9 * collapse + 0.45 * settle, rng, 2.4, 0.3, 4500, 700)


@sound(3.5, 2107, -10.4)
def blackhole_return(t, rng):
    """Release: a compressed dark resonance unwinds, an airy whoosh descends, a low landing thump at 2.0 s, warm tail."""
    n, land = len(t), 2.0
    f = ramp(t, 0, 1.2, 38, 70, True)
    dark = np.sin(phase(t, f) + ramp(t, 0, 1.2, 4, 0.3) * np.sin(phase(t, f * 1.5)))
    dark = norm(lp(dark, 900)) * smooth(t / 0.15) * (1 - smooth((t - 0.6) / 0.9))
    whoosh = norm(svf(pink(n, rng) + 0.3 * white(n, rng), ramp(t, 0.35, land, 3800, 350, True), 1.1, "bp"))
    whoosh *= window(t, 0.3, land - 0.05, 0.6) * flutter(n, rng, 5, 0.25)
    thump = thud(t, land, 90, 38, 0.32, 0.04) + 0.35 * norm(lp(white(n, rng), 700)) * hit(t, land, 0.06, 0.002)
    tone = norm(sine(t, 55) + 0.5 * sine(t, 82.5)) * hit(t, land, 1.1, 0.03)
    air = norm(lp(pink(n, rng), 500)) * hit(t, land, 0.6, 0.02)
    mix = 0.55 * dark + 0.75 * whoosh + 1.0 * thump + 0.2 * tone + 0.25 * air
    return reverb(mix, rng, 2.2, 0.3, 4500, 900)


if __name__ == "__main__":
    sfx.run(SYNTHS, __doc__, "Blackhole")
