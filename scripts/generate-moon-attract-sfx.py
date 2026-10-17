#!/usr/bin/env python3
"""Original moon_attract_* SFX (gravity charge, beam, inbound asteroid, impact and aftermath);
synthesized, no recorded, sampled or downloaded audio.

Reuses the DSP primitives and loudness-matched mastering of generate-cinematic-sfx.py.
Requires numpy, scipy and ffmpeg/ffprobe on PATH; --check validates the installed assets
without writing them. The technical moon_attract names are kept for save and resource
compatibility: AsteroidPullerSoundtrack, GravityInversionSoundtrack and SupernovaSoundtrack
play them. Accents sit at 0 s except the summon housing thud (1.4 s), the deployment lock
(3.3 s) and the pre-impact crest (1.2 s). The Asteroid Puller stops the inbound layers at its
silence tick, so the entry and tension cues reach full intensity early.
"""
import importlib.util
import math
from pathlib import Path

import numpy as np

spec = importlib.util.spec_from_file_location("cinematic_sfx", Path(__file__).with_name("generate-cinematic-sfx.py"))
sfx = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sfx)
globals().update({name: getattr(sfx, name) for name in sfx.from_dsp + ("n_wave", "swell", "whole")})
SYNTHS = {}
LOOP = 8.0


def sound(seconds, seed, level, loop=False):
    return sfx.sound(seconds, seed, level, loop, SYNTHS)


def stones(t, rng, events, periodic=False):
    """Stone hits; each event is (time, size, gain). Size 0 is a bright pebble tick, 1 a boulder with a low thud."""
    n = len(t)
    out = np.zeros(n)
    for start, size, gain in events:
        first = int(start * RATE)
        if first >= n and not periodic:
            continue
        count = int((0.03 + 0.5 * size) * RATE)
        d = np.arange(count) / RATE
        tau = 0.004 + 0.07 * size ** 1.5
        centre = 3800 * 0.07 ** size
        body = norm(bp(rng.standard_normal(count) * np.exp(-d / tau), centre / 1.8, min(centre * 1.8, 16000)))
        modes = sum(np.sin(TAU * centre * ratio * rng.uniform(0.95, 1.05) * d + rng.uniform(0, TAU)) * np.exp(-d * ratio / (1.6 * tau))
                    for ratio in (1, 1.71, 2.53))
        event = body + 0.35 * norm(modes)
        if size > 0.55:
            low = TAU * np.cumsum(45 + 60 * (1 - size) + 50 * np.exp(-d / 0.03)) / RATE
            event += 1.5 * (size - 0.4) * np.sin(low) * np.exp(-d / (0.08 + 0.2 * size))
        event *= gain * np.minimum(1, d / 0.0005)
        index = first + np.arange(count)
        if periodic:
            np.add.at(out, index % n, event)
        else:
            keep = index < n
            out[index[keep]] += event[keep]
    return out


def fracture(t, rng, start, size):
    """Crust fracture at start: a dry snap and crackling tear; big ones add a deep groan and grinding slabs."""
    n = len(t)
    snap = norm(hp(white(n, rng), 1200)) * hit(t, start, 0.006 + 0.012 * size, 0.0003)
    tear = norm(grains(t, rng, lambda x: 900 * math.exp(-(x - start) / (0.03 + 0.08 * size)) + 1, 700, 7000,
                       width=0.0015, start=start, end=start + 0.5))
    groan = thud(t, start, 75 + 45 * (1 - size), 30, 0.15 + 0.4 * size, 0.05)
    grind = norm(bp(brown(n, rng) + 0.5 * white(n, rng), 160, 1400)) * np.maximum(flutter(n, rng, 25, 1.0), 0)
    grind *= window(t, start + 0.02, start + 0.15 + 0.6 * size, 0.08)
    return (0.6 + 0.4 * size) * snap + 0.5 * tear + 0.9 * size * groan + 0.4 * size * norm(grind)


@sound(3.0, 3101, -18.0)
def moon_attract_cannon_summon(t, rng):
    """The cannon materializes: vacuum intake and converging particles, a hollow housing thud at 1.4 s, short dry tail."""
    n, lock = len(t), 1.4
    before = 1 - smooth((t - lock + 0.01) / 0.02)
    intake = norm(svf(pink(n, rng) + 0.4 * white(n, rng), ramp(t, 0, lock, 3500, 500, True), 1.6, "bp"))
    intake *= smooth(t / lock) ** 2 * before
    bloom = sine(t, ramp(t, 0, lock, 28, 46, True)) * smooth(t / 1.0) * before
    particles = norm(grains(t, rng, lambda x: 15 + 500 * (x / lock) ** 3, 2500, 9000, width=0.0015, end=lock))
    shimmer = norm(sum(a * sine(t, ramp(t, 0.2, lock, f, f * 1.5, True)) for f, a in ((1900, 1), (2850, 0.6), (4100, 0.3))))
    shimmer *= smooth((t - 0.2) / 0.8) * before
    housing = thud(t, lock, 95, 42, 0.35, 0.04) + 0.6 * norm(clank(t, lock, 128, rng, 0.4, partials=6, bright=0.6))
    hollow = norm(svf(white(n, rng), 210, 6, "bp")) * hit(t, lock, 0.25, 0.002)
    mix = 0.45 * intake + 0.35 * bloom + 0.2 * particles + 0.07 * shimmer + 1.0 * housing + 0.3 * hollow
    return reverb(drive(mix, 1.4), rng, 1.2, 0.18)


@sound(4.0, 3102, -22.0)
def moon_attract_cannon_deployment(t, rng):
    """Segments unfold on three servo stages (0.1, 1.0, 1.9 s) with ratchets and hydraulics; lock clunk at 3.3 s."""
    n, lock = len(t), 3.3
    mix = np.zeros(n)
    for start, length, f0, f1 in ((0.1, 0.8, 520, 700), (1.0, 0.75, 610, 820), (1.9, 1.1, 450, 760)):
        shape = window(t, start, start + length, 0.08)
        f = ramp(t, start, start + length, f0, f1)
        whine = norm(sine(t, f) + 0.45 * sine(t, 2 * f) + 0.2 * sine(t, 3.01 * f)) * (1 + 0.2 * np.sin(TAU * 31 * t))
        clicks = np.zeros(n)
        x = start
        while x < start + length:
            clicks[int(x * RATE)] = rng.uniform(0.5, 1)
            x += 1 / rng.uniform(14, 22)
        ratchet = norm(bp(clicks, 1800, 5200)) + 0.5 * norm(bp(clicks, 300, 900))
        stop = norm(clank(t, start + length, rng.uniform(220, 300), rng, 0.25, partials=5))
        mix += 0.25 * whine * shape + 0.35 * ratchet * shape + 0.4 * stop
    breaths = sum(hit(t, s, 0.25, 0.04) for s in (0.92, 1.85, 3.05))
    hydraulic = norm(bp(white(n, rng), 900, 5000)) * breaths
    groan = np.sin(phase(t, 70 + 4 * np.sin(TAU * 0.6 * t)) + 2 * np.sin(phase(t, 105)))
    groan = norm(bp(groan, 120, 700)) * window(t, 0.1, lock, 0.5)
    clunk = thud(t, lock, 110, 45, 0.25, 0.03) + norm(clank(t, lock, 165, rng, 0.8, partials=7))
    clunk += 0.5 * norm(hp(white(n, rng), 2500)) * hit(t, lock, 0.01, 0.0003)
    rattle = norm(grains(t, rng, lambda x: 120 * math.exp(-(x - lock) / 0.12) + 1, 1500, 6000, width=0.002,
                         start=lock + 0.03, end=lock + 0.6))
    mix += 0.25 * hydraulic + 0.15 * groan + 0.9 * norm(clunk) + 0.2 * rattle
    return reverb(mix, rng, 1.4, 0.2, 6000, 1500)


@sound(5.0, 3103, -14.0)
def moon_attract_gravity_charge(t, rng):
    """Gravity charge: pressure hum, accelerating magnetic pulses and crackle; 70 % of the rise by 1.5 s, crest held 4-5 s."""
    n = len(t)
    u = np.clip(0.7 * smooth(t / 1.5) + 0.3 * smooth((t - 1.5) / 2.5), 0, 1)
    f = 38 * (1 + 0.35 * u)
    hum = norm(svf(saw(t, f, 30) + saw(t, f * 1.007, 30) + 0.6 * saw(t, 2 * f, 20), 180 + 1400 * u ** 1.5, 1.6))
    pulses = np.zeros(n)
    x = 0.08
    while x < 4.9:
        k = int(x * RATE)
        pulses[k:k + 30] += rng.uniform(0.5, 1) * np.hanning(30)
        x += rng.uniform(0.6, 1.4) / (3 + 30 * u[k] ** 2)
    zaps = norm(bp(pulses, 1500, 6000)) + 0.6 * norm(svf(pulses, 140, 3, "bp"))
    metal = norm(svf(white(n, rng), 880 * (1 + 0.3 * u), 12, "bp")) * u
    crackle = norm(grains(t, rng, lambda x: 20 + 600 * (min(x, 4) / 4) ** 2, 3000, 10000))
    rise = norm(sine(t, 90 + 270 * u) + 0.5 * sine(t, (90 + 270 * u) * 1.5)) * u ** 2
    crest = 1 + 0.35 * smooth((t - 4.0) / 0.3) * np.sin(TAU * 24 * t)
    level = (0.15 + 0.85 * u) * (1 - smooth((t - 4.85) / 0.12))
    mix = (0.6 * hum + 0.35 * sine(t, f / 2) * (0.5 + 0.5 * u) + 0.3 * norm(zaps) + 0.2 * metal + 0.2 * crackle
           + 0.15 * rise) * crest * level
    return reverb(drive(mix, 1.5), rng, 1.2, 0.18)


@sound(1.0, 3104, -15.3)
def moon_attract_maximum_charge(t, rng):
    """Maximum-charge accent: magnetic latch and snap at 0 s, a saturated trembling core held to 0.75 s, clean stop."""
    n = len(t)
    latch = norm(clank(t, 0, 310, rng, 0.08, partials=5)) + norm(hp(white(n, rng), 2000)) * hit(t, 0, 0.008, 0.0002)
    knock = thud(t, 0, 140, 50, 0.12, 0.02)
    core = drive(sine(t, 180) + 0.7 * sine(t, 181.5) + 0.5 * sine(t, 270) + 0.4 * sine(t, 360.7), 3.5)
    core *= (1 + 0.45 * np.sin(TAU * 31 * t)) * smooth((t - 0.01) / 0.04) * (1 - smooth((t - 0.72) / 0.12))
    sizzle = norm(grains(t, rng, lambda x: 700, 3500, 11000, width=0.001, end=0.8)) * (1 - smooth((t - 0.7) / 0.1))
    zap = sine(t, 2600 * np.exp(-t / 0.02) + 300) * hit(t, 0, 0.06, 0.0003)
    mix = 0.6 * latch + 0.8 * knock + 0.45 * core + 0.25 * sizzle + 0.3 * zap
    return reverb(mix, rng, 0.5, 0.08)


@sound(3.0, 3105, -13.4)
def moon_attract_gravity_beam_fire(t, rng):
    """Gravity beam: crack and compact recoil at 0 s, a focused tearing jet held to 2 s, then a dry suction collapse."""
    n, hold = len(t), 2.0
    crack = norm(hp(n_wave(t, 0, 0.005), 300)) + 0.6 * norm(hp(white(n, rng), 2200)) * hit(t, 0, 0.012, 0.0003)
    recoil = thud(t, 0, 130, 45, 0.18, 0.025)
    held = smooth(t / 0.04) * (1 - smooth((t - hold) / 0.25))
    jet = norm(svf(white(n, rng) + pink(n, rng), 1600 + 400 * np.sin(TAU * 3 * t), 2.2, "bp"))
    jet *= np.maximum(flutter(n, rng, 70, 0.9), 0)
    core = norm(lp(saw(t, 98 + 6 * np.sin(TAU * 5 * t), 30) + saw(t, 98.7, 30), 1200))
    edges = norm(grains(t, rng, lambda x: 600 if x < hold else 50, 3500, 11000, width=0.001))
    ring = norm(clank(t, 0.0, 520, rng, 0.6, partials=5, bright=0.7))
    suction = norm(svf(pink(n, rng), ramp(t, hold - 0.1, 2.9, 600, 4000, True), 2.5, "bp"))
    suction *= window(t, hold - 0.1, 2.9, 0.15) * ramp(t, hold, 2.9, 1, 0.2)
    mix = (0.6 * crack + 0.6 * recoil + (0.75 * jet + 0.3 * core + 0.15 * edges) * held + 0.2 * ring + 0.35 * suction)
    return reverb(mix, rng, 1.0, 0.12)


@sound(LOOP, 3106, -18.9, loop=True)
def moon_attract_moon_gravitational_pull(t, rng):
    """Gravitational field bed: dark pressure drone, slow tidal groans, mineral stress grit, faint electromagnetic flutter."""
    n, cycle = len(t), 1 / LOOP
    drone = norm(sum(a * sine(t, whole(f, LOOP)) for f, a in ((32.5, 1), (48.75, 0.5), (65.1, 0.35), (97.6, 0.15))))
    drone = drive(drone, 1.6) * (0.85 + 0.15 * swell(n, rng, 0.5, True))
    pressure = norm(lp(brown(n, rng), 110, 2, True)) * flutter(n, rng, 1.5, 0.5, True)
    carrier = whole(41, LOOP) + 3 * np.sin(TAU * cycle * t)
    groan = np.sin(phase(t, carrier) + (1.8 + 1.2 * np.sin(TAU * 2 * cycle * t)) * np.sin(phase(t, whole(61.5, LOOP))))
    groan = norm(bp(groan, 60, 600, 2, True)) * np.clip(swell(n, rng, 0.3, True), 0, 1) ** 1.5
    grit = norm(grains(t, rng, lambda x: 90, 600, 4000, width=0.003, periodic=True)) * (0.5 + 0.5 * swell(n, rng, 0.6, True))
    em = norm(sine(t, whole(220, LOOP)) + 0.5 * sine(t, whole(331, LOOP))) * (0.5 + 0.5 * np.sin(TAU * whole(9, LOOP) * t))
    em *= 0.5 + 0.5 * swell(n, rng, 0.4, True)
    mix = 0.6 * drone + 0.6 * pressure + 0.45 * groan + 0.12 * grit + 0.05 * em
    return reverb(mix, rng, 2.5, 0.3, periodic=True)


@sound(11.7, 3107, -20.5)
def moon_attract_moon_approaching(t, rng):
    """Inbound asteroid, one long escalation: distant rumble and tremors, crust groans, rolling friction and rushing air;
    peak at the very end, no collision."""
    n, total = len(t), 11.7
    u = np.clip(t / total, 0, 1)
    rumble = norm(lp(brown(n, rng), 90)) * flutter(n, rng, 2, 0.5)
    tremors = lp(sum(rng.uniform(0.4, 1) * thud(t, s, 60, 30, 0.6, 0.1) for s in np.sort(rng.uniform(0.5, total - 0.5, 9))), 200)
    f = 38 + 22 * u
    groan = np.sin(phase(t, f + 2 * np.sin(TAU * 0.3 * t)) + 2.2 * np.sin(phase(t, f * 1.48)))
    groan = norm(bp(groan, 60, 700)) * np.clip(swell(n, rng, 0.35), 0, 1) ** 1.5 * smooth((t - 2) / 4)
    friction = norm(bp(pink(n, rng), 250, 2200)) * np.maximum(flutter(n, rng, 18, 0.9), 0) * smooth((t - 3) / 5)
    air = norm(svf(pink(n, rng) + 0.3 * white(n, rng), ramp(t, 0, total, 250, 3800, True), 0.8)) * u ** 2.5
    pebbles = stones(t, rng, [(s, rng.uniform(0, 0.4), rng.uniform(0.2, 1)) for s in np.sort(rng.uniform(4, total, 60))])
    mix = 0.75 * rumble + 0.4 * norm(tremors) + 0.35 * groan + 0.25 * friction + 0.65 * air + 0.06 * norm(pebbles)
    mix *= (0.3 + 0.7 * u ** 1.5) * (1 - smooth((t - total + 0.15) / 0.15))
    return reverb(mix, rng, 2.4, 0.28, 4500, 700)


@sound(5.7, 3108, -16.2)
def moon_attract_atmospheric_entry(t, rng):
    """Atmospheric entry: already fast, a turbulent air roar with plasma sizzle, heat crackle and pressure buffets,
    intensifying until a clean cut at 5.7 s."""
    n = len(t)
    u = np.clip(t / 5.6, 0, 1)
    roar = norm(svf(pink(n, rng) + 0.5 * brown(n, rng) + 0.2 * white(n, rng), ramp(t, 0, 5.6, 500, 1600, True), 0.6))
    roar *= flutter(n, rng, 12, 0.4)
    body = norm(lp(brown(n, rng), 160)) * flutter(n, rng, 4, 0.5)
    sizzle = norm(hp(white(n, rng), 5000)) * flutter(n, rng, 40, 0.6) * (0.4 + 0.6 * u)
    crackle = norm(grains(t, rng, lambda x: 60 + 400 * (x / 5.7) ** 2, 1800, 8000, width=0.002))
    starts = np.sort(rng.uniform(0.3, 5.4, 7))
    buffets = norm(lp(white(n, rng), 400)) * sum(rng.uniform(0.5, 1) * hit(t, s, 0.12, 0.03) for s in starts)
    buffets += sum(0.6 * thud(t, s, 70, 40, 0.15, 0.04) for s in starts)
    mix = 0.85 * roar + 0.45 * body + 0.15 * sizzle + 0.2 * crackle + 0.45 * buffets
    return reverb(mix * (0.55 + 0.45 * u ** 1.3) * smooth(t / 0.35), rng, 1.0, 0.12)


@sound(4.7, 3109, -23.0)
def moon_attract_moon_fragmentation(t, rng):
    """The asteroid breaks up in flight: uneven crust fractures growing closer and denser, grinding slabs, gravel shivers."""
    n = len(t)
    mix = np.zeros(n)
    x = 0.15
    while x < 4.4:
        mix += rng.uniform(0.6, 1) * fracture(t, rng, x, rng.uniform(0.3, 1.0))
        # Exponential gaps cluster some fractures and leave others alone; no steady rhythm.
        x += 0.06 + rng.exponential(0.42) * (1 - 0.6 * x / 4.4)
    splinters = stones(t, rng, [(s, rng.uniform(0, 0.3), rng.uniform(0.2, 1)) for s in np.sort(rng.uniform(0.2, 4.5, 140))])
    gravel = norm(grains(t, rng, lambda x: 40 + 180 * x / 4.7, 1500, 7000, width=0.004))
    rubble = norm(lp(brown(n, rng), 200)) * flutter(n, rng, 6, 0.6) * (0.3 + 0.7 * t / 4.7)
    mix = norm(mix) + 0.25 * norm(splinters) + 0.15 * gravel + 0.3 * rubble
    return reverb(mix * (1 - smooth((t - 4.55) / 0.12)), rng, 1.5, 0.22)


@sound(1.7, 3110, -21.0)
def moon_attract_pre_impact_tension(t, rng):
    """Breath before impact: a dark vacuum surge narrows to a crest at 1.2 s with strained metal and suspended grit;
    no reverb, so the following silence stays clean."""
    n, crest = len(t), 1.2
    u = smooth(t / crest)
    surge = norm(svf(pink(n, rng) + 0.3 * white(n, rng), ramp(t, 0, crest, 300, 3000, True), 1.4, "bp")) * (0.25 + 0.75 * u)
    pressure = norm(lp(brown(n, rng), 120)) * (0.3 + 0.7 * u ** 2)
    metal = norm(sum(a * sine(t, f * (1 + 0.04 * u)) for f, a in ((392, 1), (583, 0.7), (871, 0.45), (1307, 0.3))))
    metal *= u ** 1.5 * (1 + 0.3 * np.sin(phase(t, ramp(t, 0, crest, 4, 18))))
    grit = norm(grains(t, rng, lambda x: 80 + 500 * min(x / crest, 1) ** 2, 2500, 9000, width=0.0015))
    sub = sine(t, ramp(t, 0, crest, 30, 44, True)) * u
    return (0.75 * surge + 0.5 * pressure + 0.12 * metal + 0.15 * grit + 0.45 * sub) * smooth(t / 0.02)


@sound(6.0, 3111, -15.2)
def moon_attract_moon_impact(t, rng):
    """The asteroid strikes at 0 s: stone-and-metal crack over a sub punch, crushing slabs and ground rupture,
    collapsing rock, then a dark crater rumble decaying to 6 s."""
    n = len(t)
    crack = norm(hp(n_wave(t, 0, 0.012), 200)) + 0.6 * norm(hp(white(n, rng), 1800)) * hit(t, 0, 0.015, 0.0003)
    metal = norm(clank(t, 0.0, 185, rng, 0.9, partials=7, bright=0.7))
    punch = thud(t, 0, 80, 22, 1.2, 0.07) + 0.6 * thud(t, 0.006, 150, 45, 0.3, 0.02)
    blast = norm(svf(white(n, rng) + 2 * pink(n, rng), ramp(t, 0, 1.4, 6000, 160, True), 0.7)) * hit(t, 0.004, 0.45, 0.004)
    slabs = sum(fracture(t, rng, s, size) for s, size in ((0.09, 0.9), (0.21, 0.8), (0.38, 1.0), (0.6, 0.7),
                                                          (0.85, 0.85), (1.2, 0.6), (1.65, 0.5)))
    rupture = norm(bp(brown(n, rng) + 0.5 * pink(n, rng), 80, 900)) * flutter(n, rng, 8, 0.7) * hit(t, 0.03, 1.4, 0.06)
    collapse = stones(t, rng, [(s, rng.uniform(0.3, 1.0), rng.uniform(0.3, 1)) for s in np.sort(rng.uniform(0.15, 3.5, 70))])
    crater = norm(lp(brown(n, rng), 120)) * flutter(n, rng, 2, 0.6) * hit(t, 0.2, 2.2, 0.3)
    dry = drive(1.0 * punch + 0.6 * crack + 0.3 * metal + 0.75 * blast + 0.45 * norm(slabs) + 0.5 * rupture
                + 0.18 * norm(collapse) * smooth((t - 0.1) / 0.3) + 0.55 * crater, 2.0)
    return reverb(dry + echoes(dry, ((0.6, 0.25), (1.3, 0.15)), 700), rng, 3.2, 0.3, 4500, 600)


@sound(4.0, 3112, -16.6)
def moon_attract_shockwave_expansion(t, rng):
    """Shock front already moving: an air slap at 0 s opens into a rushing pressure wall that rolls away and thins."""
    n = len(t)
    slap = norm(bp(white(n, rng), 300, 3000)) * hit(t, 0, 0.05, 0.002) + 0.5 * norm(lp(n_wave(t, 0, 0.02), 1500))
    front = norm(svf(pink(n, rng) + 0.6 * white(n, rng), ramp(t, 0, 3.8, 3500, 300, True), 0.7)) * flutter(n, rng, 10, 0.45)
    front *= smooth(t / 0.08) * np.exp(-t / 1.0)
    roll = norm(bp(brown(n, rng) + pink(n, rng), 90, 700)) * flutter(n, rng, 3, 0.6) * smooth(t / 0.3) * np.exp(-t / 1.3)
    dust = norm(grains(t, rng, lambda x: 800 * math.exp(-x / 0.9) + 2, 2500, 10000, width=0.001)) * flutter(n, rng, 20, 0.6)
    mix = 0.45 * slap + 0.9 * front + 0.6 * roll + 0.2 * dust
    return reverb(mix, rng, 2.4, 0.3, 5000, 800)


@sound(8.0, 3113, -19.1)
def moon_attract_debris_rain(t, rng):
    """Debris rain: heavy landings, pebble ticks, bounces and dusty slides in irregular clusters, thinning to the last stones."""
    n = len(t)
    events = []
    x = 0.02
    while x < 7.4:
        density = 1 - x / 7.6
        for _ in range(rng.integers(1, 2 + int(6 * density))):
            start = x + rng.exponential(0.05)
            size = rng.beta(1.3, 3.0) * (0.4 + 0.6 * density)
            gain = rng.uniform(0.3, 1) * (0.35 + 0.65 * density)
            events.append((start, size, gain))
            if size > 0.3 and rng.random() < 0.6:
                events.append((start + rng.uniform(0.08, 0.25), 0.5 * size, 0.4 * gain))
        x += rng.exponential(0.12 + 0.6 * (1 - density) ** 2)
    rocks = stones(t, rng, events)
    slides = norm(bp(pink(n, rng), 400, 3500)) * np.clip(swell(n, rng, 2.5), 0, 1) ** 2 * np.exp(-t / 3)
    dust = norm(hp(white(n, rng), 3000)) * np.exp(-t / 2.5) * flutter(n, rng, 8, 0.5)
    bed = norm(lp(brown(n, rng), 140)) * np.exp(-t / 2.0)
    mix = 0.85 * norm(rocks) + 0.18 * slides + 0.05 * dust + 0.3 * bed
    return reverb(mix, rng, 1.8, 0.25, 5000, 900)


@sound(LOOP, 3114, -21.5, loop=True)
def moon_attract_aftermath_ambience(t, rng):
    """Ruined crater: dusty wind, low earth settling, occasional mineral ticks and a faint residual electrical fizz."""
    n = len(t)
    wind = norm(svf(pink(n, rng), 500 * 2 ** (0.8 * swell(n, rng, 0.2, True)), 1.2, "bp", True))
    wind *= 0.6 + 0.4 * swell(n, rng, 0.25, True)
    whistle = norm(svf(white(n, rng), 1450 * 2 ** (0.3 * swell(n, rng, 0.15, True)), 12, "bp", True))
    whistle *= np.clip(swell(n, rng, 0.2, True), 0, 1) ** 2
    settle = norm(lp(brown(n, rng), 100, 2, True)) * (0.6 + 0.4 * swell(n, rng, 0.4, True))
    ticks = stones(t, rng, [(s, rng.uniform(0, 0.35), rng.uniform(0.2, 1)) for s in rng.uniform(0, LOOP, 14)], periodic=True)
    fizz = norm(grains(t, rng, lambda x: 25, 4000, 11000, width=0.0008, periodic=True)) * np.clip(swell(n, rng, 0.5, True), 0, 1)
    mix = 0.7 * wind + 0.06 * whistle + 0.55 * settle + 0.12 * norm(ticks) + 0.05 * fizz
    return reverb(mix, rng, 2.2, 0.3, periodic=True)


if __name__ == "__main__":
    sfx.run(SYNTHS, __doc__, "Moon Attract")
