#!/usr/bin/env python3
"""DSP regression checks, independent of ElevenLabs files or Minecraft."""
from array import array
import importlib.util
import math
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("import_supernova_sfx", Path(__file__).with_name("import-supernova-sfx.py"))
audio = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audio)
RATE = audio.RATE


class SupernovaAudioTest(unittest.TestCase):
    def test_wrap_overlap_preserves_duration_and_does_not_insert_silence(self):
        values = array("d", (math.sin(math.tau * 53 * i / RATE) for i in range(round(2.25 * RATE))))
        opened, muffled, overlap = audio.prepare(values, 2, True)
        self.assertEqual(2 * RATE, len(opened))
        self.assertEqual(len(opened), len(muffled))
        self.assertEqual(0.25, overlap)
        self.assertLess(abs(opened[0] - opened[-1]), 0.01)
        self.assertAlmostEqual(0.65, max(map(abs, opened)), places=12)
        for i in range(0, len(opened), RATE // 100):
            self.assertGreater(sum(v * v for v in opened[i:i + RATE // 100]) / (RATE // 100), 0.01)

    def test_filtered_variant_really_removes_high_frequencies_without_renormalizing(self):
        values = array("d", (math.sin(math.tau * 55 * i / RATE) + math.sin(math.tau * 5000 * i / RATE)
                              for i in range(round(2.25 * RATE))))
        opened, muffled, _ = audio.prepare(values, 2, True)
        high = lambda signal: sum((a - b) ** 2 for a, b in zip(signal[1:], signal[:-1])) / len(signal)
        self.assertLess(high(muffled), high(opened) * 0.15)
        self.assertLess(max(map(abs, muffled)), 0.65)
        self.assertLess(abs(sum(muffled) / len(muffled)), 0.000001)

    def test_one_shot_keeps_both_recorded_passages_instead_of_silently_recropping_the_take(self):
        values = array("d", (math.sin(math.tau * 55 * i / RATE) * (1 if i < RATE // 5 or i > RATE * 2 else 0.01)
                              for i in range(round(3.2 * RATE))))
        opened, muffled, overlap = audio.prepare(values, 3.2, False)
        self.assertEqual(round(3.2 * RATE), len(opened))
        self.assertEqual(len(opened), len(muffled))
        self.assertEqual(0, overlap)
        self.assertGreater(max(map(abs, opened[2 * RATE:])), 0.6)
        self.assertLess(abs(opened[0]), 0.0001)
        self.assertLess(abs(opened[-1]), 0.0001)

    def test_treble_measurement_does_not_confuse_bass_energy_with_high_frequencies(self):
        tone = lambda frequency: array("d", (math.sin(math.tau * frequency * i / RATE) for i in range(RATE)))
        bass = audio.sfx.high_frequency_energy(tone(55))
        treble = audio.sfx.high_frequency_energy(tone(5000))
        self.assertGreater(treble, 0.01)
        self.assertLess(bass, treble * 0.000001)

    def test_a_bass_heavy_recording_still_has_measurably_filtered_treble(self):
        values = array("d", (math.sin(math.tau * 55 * i / RATE) + 0.001 * math.sin(math.tau * 5000 * i / RATE)
                             for i in range(round(2.25 * RATE))))
        opened, muffled, _ = audio.prepare(values, 2, True)
        # An all-band first difference is dominated by bass in this fixture, just like the supplied rumble.
        difference_energy = lambda signal: sum((a - b) ** 2 for a, b in zip(signal[1:], signal[:-1])) / len(signal)
        self.assertGreater(difference_energy(muffled), difference_energy(opened) * 0.15)
        self.assertLess(audio.sfx.high_frequency_energy(muffled), audio.sfx.high_frequency_energy(opened) * 0.15)

    def test_a_quiet_filtered_wind_matches_its_reference_without_independent_normalization(self):
        values = array("d", (math.sin(math.tau * 1350 * i / RATE) + 0.015 * math.sin(math.tau * 75 * i / RATE)
                             for i in range(round(2.25 * RATE))))
        opened, muffled, _ = audio.prepare(values, 2, True)
        self.assertLess(max(map(abs, muffled)), 0.08)
        _, gain, correlation = audio.sfx.validate_filtered_pair(opened, muffled, True)
        self.assertAlmostEqual(1, gain, places=10)
        self.assertAlmostEqual(1, correlation, places=10)
        boost = 0.65 / max(map(abs, muffled))
        boosted = array("d", (value * boost for value in muffled))
        with self.assertRaises(AssertionError):
            audio.sfx.validate_filtered_pair(opened, boosted, True)

    def test_hot_float_source_is_scaled_before_pcm_quantization(self):
        values = array("d", (1.7 * math.sin(math.tau * 77 * i / RATE) for i in range(round(3.2 * RATE))))
        opened, muffled, _ = audio.prepare(values, 3.2, False)
        self.assertAlmostEqual(0.65, max(map(abs, opened)), places=12)
        self.assertTrue(all(abs(v) < 1 for v in opened + muffled))

    def test_invalid_or_missing_audio_cannot_be_installed(self):
        for values, seconds, loop in (([], 3.2, False), ([0] * RATE, 3.2, False),
                                      ([0] * round(3.2 * RATE), 3.2, False),
                                      ([float("nan")] * round(3.2 * RATE), 3.2, False),
                                      ([0.1] * (2 * RATE), 2, True)):
            with self.assertRaises(ValueError):
                audio.prepare(values, seconds, loop)


if __name__ == "__main__":
    unittest.main()
