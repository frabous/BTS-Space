#!/usr/bin/env python3
"""Original SS-03 Carrier Ops / Asteroid Puller SFX; synthesized, no sampled or downloaded audio.

Requires numpy, scipy and ffmpeg/ffprobe on PATH. --check validates the installed
assets without writing them. carrier_* sounds belong to the reusable destroyer
choreography (CarrierFlight); lance_*, tether_* and asteroid_* to the Asteroid
Puller payload. Accent offsets are documented per sound so cues can land on
animation ticks (AsteroidPullerSoundtrack). Loops are built circularly: every
tone, modulation, noise field and filter state repeats exactly over one cycle.
"""
import argparse
import json
import math
from pathlib import Path
import shutil
import subprocess
import tempfile
import wave

import numpy as np
from scipy.signal import butter, fftconvolve, sosfilt

RATE = 44100
TAU = 2 * math.pi
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/stellar"
SOUNDS = ASSETS / "sounds"
SYNTHS = {}


def sound(seconds, seed, peak=0.82, loop=False):
    def register(function):
        SYNTHS[function.__name__] = (seconds, seed, peak, loop, function)
        return function
    return register


# ---------------------------------------------------------------- primitives

def axis(seconds):
    return np.arange(round(seconds * RATE)) / RATE


def norm(x):
    peak = np.max(np.abs(x))
    return x / peak if peak > 0 else x


def smooth(u):
    u = np.clip(u, 0, 1)
    return u * u * (3 - 2 * u)


def ramp(t, t0, t1, v0, v1, exponential=False):
    u = np.clip((t - t0) / (t1 - t0), 0, 1)
    return v0 * (v1 / v0) ** u if exponential else v0 + (v1 - v0) * u


def hit(t, start, tau, attack=0.002):
    d = t - start
    return np.where(d < 0, 0.0, np.minimum(1, np.maximum(d, 0) / attack) * np.exp(-np.maximum(d - attack, 0) / tau))


def window(t, start, end, edge):
    return smooth((t - start) / edge) * (1 - smooth((t - end + edge) / edge))


def phase(t, freq):
    return TAU * np.cumsum(np.broadcast_to(np.asarray(freq, float), t.shape)) / RATE


def sine(t, freq, offset=0.0):
    return np.sin(phase(t, freq) + offset)


def saw(t, freq, harmonics=24):
    f = np.broadcast_to(np.asarray(freq, float), t.shape)
    p = phase(t, f)
    out = np.zeros_like(t)
    for k in range(1, harmonics + 1):
        out += np.where(f * k < 16000, np.sin(k * p) / k, 0)
    return out


def white(n, rng):
    return rng.standard_normal(n)


def colored(n, rng, slope):
    """Circular 1/f^slope noise: periodic over its own length, so loops have no seam."""
    spectrum = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / RATE)
    spectrum[1:] /= f[1:] ** (slope / 2)
    spectrum[0] = 0
    return norm(np.fft.irfft(spectrum, n))


def pink(n, rng):
    return colored(n, rng, 1)


def brown(n, rng):
    return colored(n, rng, 2)


def static(x, kind, cutoff, order=2, periodic=False):
    sos = butter(order, cutoff, kind, fs=RATE, output="sos")
    if periodic:
        return sosfilt(sos, np.tile(x, 2))[len(x):]
    return sosfilt(sos, x)


def lp(x, cutoff, order=2, periodic=False):
    return static(x, "lowpass", cutoff, order, periodic)


def hp(x, cutoff, order=2, periodic=False):
    return static(x, "highpass", cutoff, order, periodic)


def bp(x, low, high, order=2, periodic=False):
    return static(x, "bandpass", [low, high], order, periodic)


def svf(x, cutoff, q=0.707, mode="lp", periodic=False):
    """Topology-preserving state-variable filter; stable under fast cutoff sweeps."""
    n = len(x)
    fc = np.clip(np.broadcast_to(np.asarray(cutoff, float), (n,)), 15, RATE * 0.45)
    if periodic:
        x, fc = np.tile(x, 2), np.tile(fc, 2)
    g = np.tan(np.pi * fc / RATE)
    k = 1 / q
    a1 = 1 / (1 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    xs, b1, b2, b3 = x.tolist(), a1.tolist(), a2.tolist(), a3.tolist()
    ic1 = ic2 = 0.0
    out = [0.0] * len(xs)
    for i, value in enumerate(xs):
        v3 = value - ic2
        v1 = b1[i] * ic1 + b2[i] * v3
        v2 = ic2 + b2[i] * ic1 + b3[i] * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        out[i] = v2 if mode == "lp" else k * v1 if mode == "bp" else value - k * v1 - v2
    out = np.array(out)
    return out[n:] if periodic else out


def flutter(n, rng, rate, depth, periodic=False):
    """Random amplitude motion around 1 (combustion, plasma, rolling thunder)."""
    wobble = norm(lp(white(n, rng), rate, 2, periodic))
    return 1 + depth * wobble


def grains(t, rng, rate, low, high, width=0.0025, start=0.0, end=None, periodic=False):
    """Poisson-distributed noise grains (crackle, debris, plasma pops) band-limited to low..high Hz."""
    n = len(t)
    out = np.zeros(n)
    end = t[-1] if end is None else end
    x = start
    while True:
        x += rng.exponential(1 / max(rate(x), 0.05))
        if x >= end:
            break
        length = int(width * RATE * rng.uniform(0.5, 1.6)) + 3
        grain = rng.standard_normal(length) * np.exp(-np.arange(length) / (length / 4)) * rng.uniform(0.15, 1) ** 1.5
        index = (int(x * RATE) + np.arange(length))
        if periodic:
            np.add.at(out, index % n, grain)
        else:
            keep = index < n
            out[index[keep]] += grain[keep]
    return bp(out, low, high, 2, periodic)


def thud(t, start, high, low, tau, glide=0.06):
    d = np.maximum(t - start, 0)
    f = np.where(t >= start, low + (high - low) * np.exp(-d / glide), 0)
    return np.sin(phase(t, f)) * hit(t, start, tau, 0.0015)


def clank(t, start, f0, rng, decay=0.5, partials=7, bright=1.0):
    """Struck metal plate: inharmonic modes plus a short strike transient."""
    ratios = (1, 2.32, 3.86, 5.12, 6.71, 8.43, 10.9, 13.2)
    d = t - start
    on = d >= 0
    out = np.zeros_like(t)
    for i in range(partials):
        f = f0 * ratios[i] * rng.uniform(0.97, 1.03)
        if f > 15000:
            break
        amplitude = (0.72 ** i) * (bright if i else 1)
        out[on] += amplitude * np.exp(-d[on] / (decay * 0.74 ** i)) * np.sin(TAU * f * d[on] + rng.uniform(0, TAU))
    strike = hp(white(len(t), rng), 1800) * hit(t, start, 0.004, 0.0005)
    return out + 0.6 * norm(strike) * np.max(np.abs(out))


def echoes(x, taps, cutoff):
    out = np.zeros_like(x)
    for delay, gain in taps:
        shift = int(delay * RATE)
        if shift < len(x):
            out[shift:] += gain * x[:len(x) - shift]
    return lp(out, cutoff)


def reverb(x, rng, rt60, wet, bright=7000, dark=1400, periodic=False):
    n = int(rt60 * RATE)
    ti = np.arange(n) / RATE
    ir = svf(rng.standard_normal(n) * np.exp(-6.91 * ti / rt60), np.geomspace(bright, dark, n), 0.6)
    ir[:int(0.011 * RATE)] = 0
    ir /= math.sqrt(np.sum(ir * ir))
    if periodic:
        tail = fftconvolve(np.tile(x, 1 + math.ceil(n / len(x))), ir)
        start = len(x) * math.ceil(n / len(x))
        wetted = tail[start:start + len(x)]
    else:
        wetted = fftconvolve(x, ir)[:len(x)]
    return (1 - wet) * x + wet * wetted * np.max(np.abs(x)) / max(np.max(np.abs(wetted)), 1e-12)


def drive(x, amount):
    return np.tanh(amount * norm(x)) / math.tanh(amount)


# -------------------------------------------------------------- carrier family

@sound(2.4, 3101)
def carrier_warp_open(t, rng):
    """Aperture tears open; inhale 0-0.8 s, exit burst at 0.8 s, portal hum and crackle tail."""
    n, burst = len(t), 0.8
    before = (t < burst).astype(float) * (1 - smooth((t - burst + 0.006) / 0.006))
    u = np.clip(t / burst, 0, 1)
    inhale = norm(svf(pink(n, rng), ramp(t, 0, burst, 180, 4200, True), 1.6, "bp")) * u ** 3 * before
    f = ramp(t, 0, burst, 46, 118, True)
    tone = saw(t, f, 18) + saw(t, f * 1.007, 18) + saw(t, f * 0.994, 18)
    tone = norm(svf(tone, ramp(t, 0, burst, 250, 2600, True), 0.9)) * u ** 2 * before
    crackle = norm(grains(t, rng, lambda x: 8 + 110 * (x / burst) ** 2 if x < burst else 110 * math.exp(-(x - burst) / 0.45),
                          2600, 9000))
    d = t - burst
    sub = thud(t, burst, 80, 27, 0.5, 0.09)
    blast = norm(svf(white(n, rng), ramp(t, burst, burst + 0.6, 7000, 280, True), 0.7)) * hit(t, burst, 0.24)
    halo = smooth(d / 0.12) * np.exp(-np.maximum(d, 0) / 0.8) * (d >= 0)
    hum = sum(a * sine(t, fr + 3 * np.sin(TAU * 5.3 * t)) for fr, a in ((110, 1), (165, 0.7), (220.5, 0.5), (331, 0.35)))
    hum = norm(hum) * halo * (0.75 + 0.25 * np.sin(TAU * 7 * t))
    drone = norm(sine(t, 41) + 0.4 * sine(t, 82.3)) * halo
    mix = 0.85 * inhale + 0.45 * tone + 0.22 * crackle + 1.0 * sub + 0.75 * blast + 0.2 * hum + 0.4 * drone
    return reverb(drive(mix, 1.4), rng, 2.4, 0.28)


@sound(2.0, 3102)
def carrier_warp_collapse(t, rng):
    """Flash crack at 0 s, inward swirl, pinch implosion at 0.55 s, sub tail."""
    n, pinch = len(t), 0.55
    before = (t < pinch).astype(float) * (1 - smooth((t - pinch + 0.005) / 0.005))
    flash = norm(hp(white(n, rng), 2000)) * hit(t, 0, 0.035, 0.0005)
    swirl = norm(svf(pink(n, rng), ramp(t, 0, pinch, 3200, 140, True), 3.0, "bp"))
    swirl *= smooth(t / 0.1) * (0.4 + 0.6 * t / pinch) * before
    f = ramp(t, 0, pinch, 520, 48, True)
    fall = norm(sine(t, f) + 0.35 * sine(t, 2 * f)) * (0.3 + 0.7 * t / pinch) * before
    pop = norm(lp(white(n, rng), 2500)) * hit(t, pinch, 0.03, 0.0008)
    sub = thud(t, pinch, 95, 32, 0.55, 0.06)
    tail = norm(lp(brown(n, rng), 110)) * hit(t, pinch, 0.9, 0.02)
    ring = sine(t, 1320) * (0.6 + 0.4 * np.sin(TAU * 6 * t)) * hit(t, pinch, 0.5, 0.01)
    mix = 0.55 * flash + 0.5 * swirl + 0.3 * fall + 0.7 * pop + 1.0 * sub + 0.45 * tail + 0.08 * ring
    return reverb(drive(mix, 1.6), rng, 2.2, 0.3)


@sound(2.6, 3103)
def carrier_brake(t, rng):
    """Retro burn while the destroyer decelerates out of the aperture."""
    n = len(t)
    envelope = smooth(t / 0.18) * (1 - smooth((t - 1.5) / 1.0))
    roar = norm(svf(pink(n, rng), ramp(t, 0, 2.6, 1800, 900, True), 0.8)) * flutter(n, rng, 12, 0.35)
    rumble = norm(lp(brown(n, rng), 90))
    chug = norm(lp(saw(t, ramp(t, 0, 2.6, 44, 37), 20), 260))
    hiss = norm(hp(white(n, rng), 3500))
    ignition = thud(t, 0.02, 120, 45, 0.2)
    mix = (0.8 * roar + 0.6 * rumble + 0.35 * chug + 0.08 * hiss) * envelope + 0.5 * ignition
    return reverb(mix, rng, 1.6, 0.2)


@sound(2.8, 3104)
def carrier_bay_open(t, rng):
    """Latch at 0 s, doors travel to their end stop at 0.5 s, cradle servo and lock at 1.06 s."""
    n = len(t)
    latch = clank(t, 0.0, 175, rng, 0.7)
    pneumatic = norm(hp(white(n, rng), 2200)) * hit(t, 0.0, 0.25, 0.005)
    doors = window(t, 0.05, 0.52, 0.08)
    groan = norm(svf(saw(t, 46 + 3 * np.sin(TAU * 0.7 * t), 24), 380, 1.4)) * doors
    whine_f = ramp(t, 0.05, 0.5, 620, 760)
    whine = norm(sine(t, whine_f) + 0.3 * sine(t, 2 * whine_f)) * doors * (0.85 + 0.15 * np.sin(TAU * 30 * t))
    rumble = norm(lp(brown(n, rng), 160)) * doors
    stop = clank(t, 0.5, 92, rng, 1.1) + 0.8 * thud(t, 0.5, 70, 42, 0.3)
    servo_window = window(t, 0.32, 1.05, 0.06)
    servo_f = ramp(t, 0.32, 1.05, 880, 1240)
    servo = norm(sine(t, servo_f) + 0.4 * sine(t, 2 * servo_f) + 0.15 * lp(np.sign(sine(t, 3 * servo_f)), 3000)) * servo_window
    clicks = np.zeros(n)
    tick = 0.32
    while tick < 1.05:
        clicks[int(tick * RATE)] = rng.uniform(0.6, 1)
        tick += 1 / 28 * rng.uniform(0.85, 1.15)
    ratchet = norm(bp(clicks, 2500, 6000)) * servo_window
    lock = clank(t, 1.06, 260, rng, 0.4) + 0.5 * thud(t, 1.06, 110, 60, 0.12)
    mix = (0.55 * norm(latch) + 0.35 * pneumatic + 0.5 * groan + 0.12 * whine + 0.45 * rumble
           + 0.9 * norm(stop) + 0.14 * servo + 0.12 * ratchet + 0.45 * norm(lock))
    return reverb(mix, rng, 1.7, 0.3, 6000, 1600)


@sound(1.6, 3105)
def carrier_clamp_release(t, rng):
    """Twin clamps snap open at 0 s; the payload's weight leaves the cradle."""
    n = len(t)
    clamps = clank(t, 0.0, 215, rng, 0.8) + 0.9 * clank(t, 0.055, 168, rng, 0.9)
    blast = norm(lp(hp(white(n, rng), 1400), 9000)) * hit(t, 0.0, 0.35, 0.003)
    load = thud(t, 0.01, 90, 48, 0.25)
    ring = sum(a * sine(t, f) * hit(t, 0.0, tau, 0.002) for f, a, tau in ((1830, 1, 1.0), (2915, 0.7, 0.7), (4410, 0.5, 0.5)))
    mix = 0.85 * norm(clamps) + 0.5 * blast + 0.7 * load + 0.12 * norm(ring)
    return reverb(mix, rng, 1.8, 0.32, 6500, 1700)


@sound(2.0, 3106, peak=0.6, loop=True)
def carrier_drive_loop(t, rng):
    """Capital-ship drive bed; the soundtrack raises level and pitch for the spool."""
    n = len(t)
    hum = sum(a * sine(t, f) for f, a in ((41, 1), (41.5, 0.5), (82, 0.55), (123, 0.32), (164.5, 0.2), (205, 0.12)))
    hum = norm(hum) * (1 + 0.12 * np.sin(TAU * 3 * t))
    rumble = norm(lp(pink(n, rng), 180, 2, True)) * (1 + 0.2 * np.sin(TAU * 0.5 * t))
    whine = norm(sine(t, 1640 + 6 * np.sin(TAU * t)) + 0.3 * sine(t, 3280 + 12 * np.sin(TAU * t)))
    hiss = norm(bp(white(n, rng), 2000, 7000, 2, True))
    mix = 0.8 * hum + 0.6 * rumble + 0.07 * whine + 0.05 * hiss
    return reverb(mix, rng, 1.2, 0.2, periodic=True)


@sound(3.2, 3107)
def carrier_spool(t, rng):
    """Turbine spool 0-0.7 s, main-drive ignition at 0.7 s, receding acceleration roar."""
    n, depart = len(t), 0.7
    f = np.where(t < depart, ramp(t, 0, depart, 280, 1650, True),
                 np.where(t < 2.0, ramp(t, depart, 2.0, 1650, 1900), ramp(t, 2.0, 3.2, 1900, 1500)))
    whine = norm(sine(t, f) + 0.35 * sine(t, 2 * f) + 0.2 * sine(t, 3.02 * f) + 0.2 * lp(saw(t, f / 12, 30), 900))
    whine *= smooth(t / depart) * (1 - smooth((t - 2.3) / 0.9))
    cutoff = np.where(t < depart, ramp(t, 0, depart, 300, 1600, True), np.where(t < 2.0, 1600, ramp(t, 2.0, 3.2, 1600, 500, True)))
    roar_level = smooth((t - 0.2) / 0.6) * (1 - smooth((t - 2.3) / 0.9))
    roar = norm(svf(pink(n, rng), cutoff, 0.8)) * flutter(n, rng, 9, 0.25) * roar_level
    ignition = thud(t, depart, 110, 40, 0.45) + 0.6 * norm(lp(white(n, rng), 900)) * hit(t, depart, 0.3)
    rumble = norm(lp(brown(n, rng), 70)) * roar_level
    mix = 0.22 * whine + 0.75 * roar + 0.8 * ignition + 0.5 * rumble
    return reverb(mix, rng, 2.0, 0.25)


# ------------------------------------------------------------ Graviton Lance

@sound(2.8, 3201)
def lance_reentry(t, rng):
    """Plasma roar around the falling lance; fades by 2.4 s for the retro burn."""
    n = len(t)
    base = pink(n, rng) + 0.3 * norm(white(n, rng))
    roar = norm(lp(svf(base, ramp(t, 0, 2.4, 380, 700, True), 0.5, "bp"), 2500)) * flutter(n, rng, 14, 0.45)
    crackle = norm(grains(t, rng, lambda x: 40 + 35 * x, 1800, 7000))
    rumble = norm(lp(brown(n, rng), 110))
    whistle = sine(t, ramp(t, 0, 2.8, 2600, 1900, True) + 25 * np.sin(TAU * 7 * t)) * flutter(n, rng, 20, 0.4)
    envelope = smooth(t / 0.5) * (1 - smooth((t - 2.25) / 0.5)) * (0.6 + 0.4 * np.clip(t / 2.2, 0, 1))
    mix = (0.8 * roar + 0.25 * crackle + 0.55 * rumble + 0.06 * whistle) * envelope
    return reverb(mix, rng, 1.4, 0.15)


@sound(1.8, 3202)
def lance_retro(t, rng):
    """Retro rockets light at 0 s and cut at touchdown (1.1 s)."""
    n, cut = len(t), 1.1
    ignition = thud(t, 0, 140, 50, 0.18) + 0.5 * norm(hp(white(n, rng), 2000)) * hit(t, 0, 0.02, 0.0005)
    jet = brown(n, rng) + 0.6 * pink(n, rng)
    roar = norm(svf(jet, 2200, 0.7)) * (1 + 0.5 * norm(bp(white(n, rng), 25, 45)))
    roar = norm(roar) * smooth(t / 0.05) * (1 - smooth((t - cut) / 0.06))
    crackle = norm(grains(t, rng, lambda x: 150, 1500, 6000, end=cut))
    shutdown = norm(bp(white(n, rng), 600, 3000)) * hit(t, cut, 0.12, 0.01)
    tail = norm(lp(brown(n, rng), 80)) * hit(t, cut, 0.5, 0.02)
    mix = 0.7 * ignition + 0.8 * roar + 0.3 * crackle + 0.3 * shutdown + 0.35 * tail
    return reverb(mix, rng, 1.8, 0.25)


@sound(2.6, 3203)
def lance_touchdown(t, rng):
    """Two-hundred-tonne landing at 0 s; legs ring, frame groans, squat rebound at 0.62 s."""
    n = len(t)
    slam = thud(t, 0, 72, 30, 0.55, 0.07)
    impact = norm(lp(white(n, rng), 1400)) * hit(t, 0, 0.09, 0.001)
    crack = norm(hp(white(n, rng), 2000)) * hit(t, 0, 0.015, 0.0005)
    legs = clank(t, 0.0, 118, rng, 1.3, bright=0.8) + clank(t, 0.03, 141, rng, 1.0) + clank(t, 0.05, 97, rng, 1.4)
    carrier = ramp(t, 0.12, 1.0, 190, 150)
    groan = np.sin(phase(t, carrier) + 2.5 * np.sin(phase(t, carrier * 0.5)))
    groan = norm(bp(groan, 300, 1200)) * window(t, 0.1, 1.0, 0.2)
    hiss = norm(hp(white(n, rng), 2600)) * hit(t, 0.04, 0.35, 0.03)
    debris = norm(grains(t, rng, lambda x: 60 * math.exp(-x / 0.3) + 1, 1500, 5000, width=0.004))
    rebound = clank(t, 0.62, 160, rng, 0.4) + thud(t, 0.62, 90, 50, 0.15)
    mix = (1.0 * slam + 0.75 * impact + 0.35 * crack + 0.5 * norm(legs) + 0.22 * groan + 0.25 * hiss
           + 0.28 * debris + 0.35 * norm(rebound))
    return reverb(drive(mix, 1.3), rng, 2.0, 0.25)


@sound(1.4, 3204)
def lance_anchor(t, rng):
    """Four anchor spikes drive into the ground (0, 0.085, 0.17, 0.27 s) then hydraulics lock."""
    n = len(t)
    mix = np.zeros(n)
    for start, level in ((0.0, 1.0), (0.085, 0.9), (0.17, 0.95), (0.27, 0.85)):
        crunch = norm(bp(brown(n, rng) + 0.3 * white(n, rng), 150, 1600)) * hit(t, start, 0.09)
        chunk = norm(clank(t, start, rng.uniform(270, 320), rng, 0.18, partials=4))
        mix += level * (0.9 * thud(t, start, 85, 38, 0.14) + 0.55 * crunch + 0.35 * chunk)
    hiss = norm(hp(white(n, rng), 2000)) * window(t, 0.34, 0.85, 0.08)
    lock = norm(clank(t, 0.82, 420, rng, 0.12, partials=5))
    rumble = norm(lp(brown(n, rng), 90)) * hit(t, 0, 0.5, 0.01)
    mix = mix / 1.8 + 0.22 * hiss + 0.3 * lock + 0.4 * rumble
    return reverb(mix, rng, 1.6, 0.22)


@sound(2.8, 3205)
def lance_unfold(t, rng):
    """Barrel stages extend; locks at 0.8 (stage 2), 1.3 (stage 3), 1.5 (fins), 1.9 s (crown) then the crown energizes."""
    n = len(t)
    mix = np.zeros(n)
    stages = ((0.1, 0.7, 420, 690, 0.8, 230, True), (0.6, 0.7, 480, 760, 1.3, 260, True),
              (0.9, 0.6, 700, 900, 1.5, 340, False), (1.3, 0.6, 560, 980, 1.9, 400, False))
    for start, length, f0, f1, lock_time, lock_f0, slide in stages:
        shape = window(t, start, start + length, 0.06)
        f = ramp(t, start, start + length, f0, f1)
        whine = sine(t, f) + 0.45 * sine(t, 2 * f) + 0.25 * sine(t, 3 * f) + 0.12 * sine(t, 4 * f)
        mix += 0.3 * norm(whine) * shape
        if slide:
            mix += 0.22 * norm(bp(white(n, rng), 900, 2600)) * (1 + 0.6 * np.sin(TAU * 38 * t)) * shape
        lock = clank(t, lock_time, lock_f0, rng, 0.35) + 0.8 * thud(t, lock_time, lock_f0 * 0.45, lock_f0 * 0.25, 0.1)
        mix += 0.6 * norm(lock)
    shing = norm(svf(white(n, rng), ramp(t, 0.9, 1.2, 2500, 6500, True), 2.0, "bp")) * hit(t, 0.9, 0.25, 0.02)
    crown = window(t, 1.9, 2.8, 0.35)
    energy = norm(sine(t, 110) + 0.6 * sine(t, 220) + 0.3 * sine(t, 330) + 0.2 * lp(saw(t, 110, 40), 1800)) * crown
    sparks = norm(grains(t, rng, lambda x: 30, 3000, 8000, start=1.9))
    mix += 0.35 * shing + 0.35 * energy + 0.15 * sparks * crown
    return reverb(mix, rng, 1.4, 0.2)


@sound(2.6, 3206)
def lance_harpoon(t, rng):
    """Graviton harpoon leaves the muzzle at 0 s; the bolt recedes, terrain answers with echoes."""
    n = len(t)
    thoom = thud(t, 0, 62, 30, 0.7, 0.1)
    zap_f = 1100 * np.exp(-t / 0.06) + 70
    zap = (sine(t, zap_f) + 0.45 * sine(t, 1.5 * zap_f)) * hit(t, 0, 0.35, 0.001)
    crack = norm(hp(white(n, rng), 1800)) * hit(t, 0, 0.02, 0.0005)
    whoosh = norm(svf(pink(n, rng), ramp(t, 0.02, 0.7, 2600, 380, True), 1.2, "bp")) * hit(t, 0.02, 0.3, 0.03)
    shimmer = (sine(t, 330) + sine(t, 333.5) + 0.7 * sine(t, 495) + 0.7 * sine(t, 498)) * (0.7 + 0.3 * np.sin(TAU * 9 * t))
    shimmer = norm(shimmer) * hit(t, 0, 0.7, 0.05)
    dry = drive(1.0 * thoom + 0.55 * norm(zap) + 0.5 * crack + 0.5 * whoosh + 0.18 * shimmer, 1.5)
    return reverb(dry + echoes(dry, ((0.42, 0.35), (0.86, 0.2)), 1200), rng, 2.6, 0.3)


# --------------------------------------------------------- tether and asteroid

@sound(3.4, 3301)
def tether_lock(t, rng):
    """The field closes on the asteroid at 0 s: low gravitational gong, rock crunch, field crackle."""
    n = len(t)
    bend = 1 - 0.07 * smooth(t / 1.4)
    gong = np.zeros(n)
    modes = ((1, 1, 2.6), (1.47, 0.8, 1.9), (2.09, 0.6, 1.4), (2.56, 0.45, 1.1), (3.39, 0.35, 0.8), (4.17, 0.25, 0.6), (5.43, 0.15, 0.45))
    for ratio, amplitude, tau in modes:
        f = 46 * ratio * bend
        gong += amplitude * (sine(t, f) + 0.5 * sine(t, f + 0.4)) * hit(t, 0, tau, 0.004)
        # Same modes two octaves up so the lock still reads on small speakers.
        gong += 0.35 * amplitude * sine(t, 4 * f + 1.1) * hit(t, 0, tau * 0.55, 0.003)
    vwoom = sine(t, 120 + 480 * np.exp(-t / 0.18)) * hit(t, 0, 0.6, 0.01)
    strike = thud(t, 0, 95, 34, 0.45) + 0.6 * norm(lp(white(n, rng), 1800)) * hit(t, 0, 0.08)
    crunch = norm(grains(t, rng, lambda x: 220 * math.exp(-x / 0.35) + 1, 300, 2500, width=0.006))
    grind = norm(lp(brown(n, rng), 600)) * hit(t, 0, 0.4, 0.01)
    field = norm(grains(t, rng, lambda x: 60 * math.exp(-x / 0.8) + 1, 3000, 9000))
    mix = 0.8 * norm(gong) + 0.9 * strike + 0.3 * vwoom + 0.45 * crunch + 0.35 * grind + 0.15 * field
    return reverb(drive(mix, 1.3), rng, 3.2, 0.35)


@sound(2.0, 3302, peak=0.6, loop=True)
def tether_strain_loop(t, rng):
    """Groaning load on the tether; the soundtrack lowers its pitch as the asteroid is reeled in."""
    n = len(t)
    carrier = 55 + 3 * np.sin(TAU * 0.5 * t) + 1.5 * np.sin(TAU * 2 * t)
    index = 1.8 + 1.2 * np.sin(TAU * 0.5 * t) + 0.6 * np.sin(TAU * 1.5 * t + 1)
    groan = np.sin(phase(t, carrier) + index * np.sin(phase(t, 82.5)))
    groan = norm(bp(groan, 90, 700, 2, True)) * (0.75 + 0.25 * np.sin(TAU * 0.5 * t))
    creaks = np.zeros(n)
    for start, length in ((0.18, 0.22), (0.95, 0.14), (1.42, 0.25)):
        x = start
        while x < start + length:
            creaks[int(x * RATE) % n] += rng.uniform(0.5, 1)
            x += 1 / (35 + 35 * (x - start) / length)
    creaks = norm(svf(creaks, 650, 6, "bp", True))
    sub = sine(t, 30) * (0.5 + 0.5 * np.sin(TAU * 0.5 * t)) ** 2
    shimmer = norm(bp(white(n, rng), 3500, 8000, 2, True)) * (0.5 + 0.5 * np.sin(TAU * 4 * t))
    mix = 0.7 * groan + 0.35 * creaks + 0.4 * sub + 0.06 * shimmer
    return reverb(mix, rng, 1.0, 0.2, periodic=True)


@sound(2.4, 3303, peak=0.72)
def tether_snap(t, rng):
    """Tether parts at 0 s: crack, whip chirp, elastic recoil twang and electrical sizzle."""
    n = len(t)
    crack = norm(hp(white(n, rng), 1200)) * hit(t, 0, 0.012, 0.0003)
    whip = sine(t, 3200 * np.exp(-t / 0.05) + 140) * hit(t, 0, 0.22, 0.0005)
    f = 58 + 182 * np.exp(-t / 0.25)
    twang = (sine(t, f) + 0.4 * sine(t, 2 * f) + 0.2 * sine(t, 3 * f)) * hit(t, 0, 0.9, 0.003)
    twang *= 1 + 0.5 * np.exp(-t / 0.4) * np.sin(TAU * 17 * t)
    whoomp = norm(lp(pink(n, rng), 400)) * hit(t, 0, 0.25, 0.01) + thud(t, 0, 80, 35, 0.4)
    sizzle = norm(grains(t, rng, lambda x: 300 * math.exp(-x / 0.35) + 1, 3000, 9000))
    buzz = norm(bp(white(n, rng), 4000, 8000)) * hit(t, 0, 0.5, 0.01) * (lp(white(n, rng), 30) > 0)
    mix = 1.0 * crack + 0.55 * whip + 0.45 * norm(twang) + 0.6 * norm(whoomp) + 0.3 * sizzle + 0.12 * buzz
    return reverb(mix, rng, 2.4, 0.3)


@sound(2.8, 3304)
def asteroid_sonic_boom(t, rng):
    """Double boom (bow shock 0.02 s, fragment shock 0.36 s) and rolling thunder off the terrain."""
    n = len(t)
    shocks = np.zeros(n)
    for start, length, level in ((0.02, 0.11, 1.0), (0.36, 0.09, 0.75)):
        on = (t >= start) & (t < start + length)
        shocks[on] += level * (1 - 2 * (t[on] - start) / length)
    shocks = lp(shocks, 2500)
    boom = thud(t, 0.02, 55, 28, 0.6)
    thunder = norm(lp(brown(n, rng), 220)) * flutter(n, rng, 3, 0.5) * hit(t, 0.1, 1.1, 0.15)
    dry = norm(shocks) + 0.6 * boom + 0.55 * thunder
    rolled = dry + echoes(dry, ((0.55, 0.35), (0.9, 0.25), (1.35, 0.18), (1.8, 0.1)), 900)
    return reverb(rolled, rng, 2.8, 0.3)


# -------------------------------------------------------------- encode/check

def command(arguments):
    return subprocess.run(arguments, check=True, capture_output=True).stdout


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
    with tempfile.TemporaryDirectory(prefix="asteroid-puller-sfx-") as temporary:
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
    print(f"PASS: {len(SYNTHS)} SS-03 sounds, registered, bounded levels, faded tails and seamless loops.")


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
