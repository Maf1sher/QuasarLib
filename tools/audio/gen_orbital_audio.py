#!/usr/bin/env python3
"""Synthesise the QuasarLib orbital-laser sound set.

The score is built from the same oscillator/noise/reverb helpers as the
black-hole set, but with a mechanical descent and impact palette. All assets
are original and remain content-owned under the ``quasar_content`` namespace.

Run from the repository root with ``numpy`` and ``ffmpeg`` available:

    python3 tools/audio/gen_orbital_audio.py
    python3 tools/audio/gen_orbital_audio.py --only arrival,charge,fire

The script writes OGG Vorbis files under
``src/main/resources/assets/quasar_content/sounds/orbital_laser/`` and fails
when a stem is too bright or too noisy for its role. ``--only`` keeps an
existing stem byte-identical when another encoder version is installed.
"""

from __future__ import annotations

import argparse
import math
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

from gen_blackhole_audio import (
    RATE,
    TWO_PI,
    add_at,
    exp_env,
    fft_filter,
    loop_crossfade,
    master,
    metal_groan,
    metal_ring,
    reverb,
    smoothstep,
    spectral_stats,
    sub_sweep,
    t_axis,
)

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/assets/quasar_content/sounds/orbital_laser"

TARGETS = {
    "warning": (900.0, 0.020, 0.20, 0.12),
    "arrival": (1400.0, 0.060, 0.080, 0.18),
    "charge": (1800.0, 0.100, 0.050, 0.30),
    "descent": (1400.0, 0.050, 0.10, 0.15),
    "fire": (900.0, 0.040, 0.30, 0.10),
    "impact": (900.0, 0.060, 0.25, 0.08),
    "drone": (500.0, 0.005, 0.45, 0.05),
    "crackle": (2600.0, 0.150, 0.05, 0.25),
    "collapse": (1200.0, 0.040, 0.20, 0.12),
    "aftermath": (800.0, 0.020, 0.20, 0.10),
}

# Stems that must match a phase length exactly: arrival and charge are one-shot
# cues that hand the show to the next phase, so their length is part of the timing.
EXPECTED_SECONDS = {
    "arrival": 2.0,
    "charge": 2.0,
    "fire": 2.5,
}

SEED = 0x0A11B


def report(name: str, signal: np.ndarray) -> bool:
    mono = signal.mean(axis=1) if signal.ndim == 2 else signal
    peak = 20.0 * math.log10(max(float(np.max(np.abs(mono))), 1e-9))
    rms = 20.0 * math.log10(max(float(np.sqrt(np.mean(mono ** 2))), 1e-9))
    seam = abs(float(mono[0] - mono[-1]))
    window = max(1, int(0.02 * RATE))
    seam_rms = abs(
        float(np.sqrt(np.mean(mono[:window] ** 2)))
        - float(np.sqrt(np.mean(mono[-window:] ** 2)))
    )
    centroid, low, high, flatness = spectral_stats(signal)
    max_centroid, max_high, min_low, max_flatness = TARGETS[name]
    expected = EXPECTED_SECONDS.get(name)
    length_ok = expected is None or abs(len(mono) / RATE - expected) < 1.0e-6
    passed = (length_ok and centroid <= max_centroid and high <= max_high
              and low >= min_low and flatness <= max_flatness)
    status = "ok" if passed else "FAIL"
    print(f"{name:10s} {len(mono) / RATE:6.2f}s  peak {peak:6.2f}  rms {rms:6.2f}  "
          f"centroid {centroid:6.0f}Hz  low<100 {low * 100:5.1f}%  "
          f"high>4k {high * 100:4.1f}%  flat {flatness:.3f}  "
          f"seam {seam:.4f}/{seam_rms:.4f}  {status}")
    return passed


def write_ogg(name: str, signal: np.ndarray) -> bool:
    signal = np.clip(signal, -1.0, 1.0)
    pcm = (signal * 32767.0).astype(np.int16)
    channels = 2 if signal.ndim == 2 else 1
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temp:
        wav_path = Path(temp) / f"{name}.wav"
        with wave.open(str(wav_path), "wb") as handle:
            handle.setnchannels(channels)
            handle.setsampwidth(2)
            handle.setframerate(RATE)
            handle.writeframes(pcm.tobytes())
        ogg_path = OUTPUT / f"{name}.ogg"
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav_path),
             "-c:a", "libvorbis", "-q:a", "5", "-ar", str(RATE), str(ogg_path)],
            check=True,
        )
    return report(name, signal)


def warning() -> np.ndarray:
    seconds = 3.0
    rng = np.random.default_rng(SEED + 1)
    t = t_axis(seconds)
    pulse = (0.5 + 0.5 * np.sin(TWO_PI * 1.33 * t)) ** 6
    sub = np.sin(TWO_PI * 48.0 * t) * (0.22 + 0.78 * pulse)
    metal = metal_groan(seconds, rng, 58.0, 1.0 / seconds, 0.30)
    metal *= 0.35 + 0.65 * pulse
    air = fft_filter(rng.standard_normal(len(t)), low=900.0, high=60.0) * pulse * 0.05
    mix = fft_filter(sub * 0.52 + metal + air, low=850.0)
    mix = reverb(mix, mix=0.24, decay=1.6, seed=SEED + 2)
    mix = loop_crossfade(mix, 0.4)
    return master(mix, -20.0, -4.0)


def arrival() -> np.ndarray:
    """Bottom-up warp-in: rising air, sub swell, metal body, and a lock click."""
    seconds = EXPECTED_SECONDS["arrival"]
    rng = np.random.default_rng(SEED + 15)
    t = t_axis(seconds)
    frequency = 140.0 * (640.0 / 140.0) ** (t / seconds)
    whine = np.sin(np.cumsum(TWO_PI * frequency / RATE))
    whine *= smoothstep(0.0, 0.7, t) * (1.0 - smoothstep(1.6, 2.0, t)) * 0.22
    air = fft_filter(rng.standard_normal(len(t)), low=3200.0, high=200.0) * 0.16
    air *= smoothstep(0.0, 0.7, t) * (0.65 + 0.35 * smoothstep(0.7, 1.5, t))
    sub = sub_sweep(seconds, 36.0, 68.0, 0.6, 1.1, 0.8) * 0.55
    bed = np.sin(TWO_PI * 31.0 * t) * smoothstep(0.0, 0.8, t) * 0.12
    metal = metal_groan(seconds, rng, 54.0, 0.5, 0.30)
    metal *= smoothstep(0.15, 1.1, t) * (1.0 - smoothstep(1.6, 2.0, t))
    lock = np.zeros_like(t)
    add_at(lock, metal_ring(0.45, 260.0, 0.30, 0.16), 1.45)
    mix = fft_filter(whine + air + sub + bed + metal + lock, low=2600.0)
    mix *= 1.0 - smoothstep(1.82, 2.0, t)
    mix = reverb(mix, mix=0.28, decay=1.4, seed=SEED + 16)
    return master(mix, -18.0, -3.0)


def charge() -> np.ndarray:
    """Mechanical transform: rising whine, accelerating ratchets, and a final lock."""
    seconds = EXPECTED_SECONDS["charge"]
    rng = np.random.default_rng(SEED + 17)
    t = t_axis(seconds)
    frequency = 90.0 * (900.0 / 90.0) ** (t / seconds)
    whine = np.sin(np.cumsum(TWO_PI * frequency / RATE))
    whine *= (0.15 + 0.85 * (t / seconds) ** 1.6) * 0.26
    spin = fft_filter(rng.standard_normal(len(t)), low=2600.0, high=240.0)
    spin *= (0.18 + 0.82 * (t / seconds) ** 1.4) * 0.16
    ticks = np.zeros_like(t)
    for index, start in enumerate(np.linspace(0.06, 1.62, 12)):
        pitch = 120.0 + 11.0 * index
        length = 0.06 + 0.03 * (index / 11.0)
        add_at(ticks, metal_ring(length, pitch, 0.16, 0.16), float(start))
    add_at(ticks, metal_ring(0.26, 320.0, 0.22, 0.24), 1.72)
    sub = np.sin(TWO_PI * 33.0 * t) * (0.16 + 0.30 * (t / seconds))
    sub += np.sin(TWO_PI * 44.0 * t + 0.5) * (0.06 + 0.10 * (t / seconds))
    mix = fft_filter(whine + spin + ticks + sub, low=2600.0)
    mix = reverb(mix, mix=0.22, decay=1.1, seed=SEED + 18)
    return master(mix, -16.0, -2.5)


def fire() -> np.ndarray:
    """Cannon blast from the platform muzzle: crack, sub drop, and pressure tail."""
    seconds = EXPECTED_SECONDS["fire"]
    rng = np.random.default_rng(SEED + 19)
    t = t_axis(seconds)
    sub = sub_sweep(seconds, 120.0, 18.0, 0.008, 1.5) * 0.95
    body = sub_sweep(seconds, 180.0, 46.0, 0.010, 0.9) * 0.45
    crack = fft_filter(rng.standard_normal(len(t)), low=2600.0, high=90.0)
    crack *= exp_env(seconds, 0.004, 0.07) * 0.55
    pressure = fft_filter(rng.standard_normal(len(t)), low=700.0, high=30.0)
    pressure *= exp_env(seconds, 0.010, 1.0) * 0.70
    metal = metal_ring(seconds, 95.0, 1.4, 0.28)
    thump = np.sin(TWO_PI * 70.0 * t) * exp_env(seconds, 0.010, 0.55) * 0.12
    mix = fft_filter(sub + body + crack + pressure + metal + thump, low=1400.0)
    return master(reverb(mix, mix=0.24, decay=2.2, seed=SEED + 20), -12.0)


def descent() -> np.ndarray:
    seconds = 1.5
    rng = np.random.default_rng(SEED + 3)
    t = t_axis(seconds)
    air = fft_filter(rng.standard_normal(len(t)), low=2400.0, high=90.0)
    air *= 0.70 + 0.30 * np.sin(TWO_PI * (1.0 / seconds) * t)
    rumble = np.sin(TWO_PI * 36.0 * t) * 0.24 + np.sin(TWO_PI * 54.0 * t + 0.7) * 0.10
    whoosh = fft_filter(rng.standard_normal(len(t)), low=1200.0, high=120.0)
    whoosh *= 0.35
    mix = fft_filter(air * 0.34 + rumble + whoosh, low=1700.0)
    mix = reverb(mix, mix=0.20, decay=1.2, seed=SEED + 4)
    mix = loop_crossfade(mix, 0.3)
    return master(mix, -19.0, -4.0)


def impact() -> np.ndarray:
    seconds = 5.0
    rng = np.random.default_rng(SEED + 5)
    t = t_axis(seconds)
    sub = sub_sweep(seconds, 90.0, 18.0, 0.03, 1.6) * 0.90
    body = sub_sweep(seconds, 140.0, 42.0, 0.02, 1.0) * 0.42
    pressure = fft_filter(rng.standard_normal(len(t)), low=500.0, high=25.0)
    pressure *= exp_env(seconds, 0.02, 1.2) * 0.70
    metal = metal_ring(seconds, 80.0, 1.5, 0.20)
    thump = np.sin(TWO_PI * 110.0 * t) * exp_env(seconds, 0.02, 0.55) * 0.10
    mix = fft_filter(sub + body + pressure + metal + thump, low=1200.0)
    return master(reverb(mix, mix=0.22, decay=2.5, seed=SEED + 6), -12.0)


def drone() -> np.ndarray:
    seconds = 16.0
    rng = np.random.default_rng(SEED + 7)
    t = t_axis(seconds)
    sub = np.zeros_like(t)
    for base, gain in ((24.0, 0.34), (36.0, 0.20), (48.0, 0.12)):
        for detune in (-0.08, 0.08):
            sub += np.sin(TWO_PI * (base + detune) * t + rng.uniform(0.0, TWO_PI)) * gain
    sub *= 0.90 + 0.10 * np.sin(TWO_PI * (1.0 / seconds) * t)
    electrical = np.sin(TWO_PI * 50.0 * t) * (0.75 + 0.25 * np.sin(TWO_PI * 0.1 * t))
    metal = metal_groan(seconds, rng, 48.0, 1.0 / seconds, 0.55)
    mix = fft_filter(sub + electrical * 0.04 + metal, low=850.0)
    mix = reverb(mix, mix=0.28, decay=3.0, seed=SEED + 8)
    mix = loop_crossfade(mix, 0.8)
    return master(mix, -20.0, -3.0)


def crackle() -> np.ndarray:
    seconds = 1.5
    rng = np.random.default_rng(SEED + 9)
    t = t_axis(seconds)
    signal = np.zeros_like(t)
    for index in range(18):
        start = rng.uniform(0.0, seconds - 0.08)
        length = int(rng.uniform(0.008, 0.045) * RATE)
        offset = int(start * RATE)
        burst = rng.standard_normal(length) * np.exp(
            -np.arange(length) / max(1.0, length * 0.22)
        )
        signal[offset:offset + length] += burst * rng.uniform(0.12, 0.45)
    bed = fft_filter(rng.standard_normal(len(t)), low=3000.0, high=180.0) * 0.08
    low = (np.sin(TWO_PI * 55.0 * t) * 0.045
           + np.sin(TWO_PI * 90.0 * t + 0.4) * 0.025)
    tone = metal_ring(seconds, 420.0, 0.25, 0.10)
    mix = fft_filter(signal * 0.7 + bed + tone, low=3000.0) + low
    mix = reverb(mix, mix=0.16, decay=0.9, seed=SEED + 10)
    mix = fft_filter(mix, low=2800.0)
    mix = loop_crossfade(mix, 0.2)
    return master(mix, -23.0, -5.0)


def collapse() -> np.ndarray:
    seconds = 2.4
    rng = np.random.default_rng(SEED + 11)
    t = t_axis(seconds)
    frequency = 300.0 * (40.0 / 300.0) ** (t / seconds)
    whine = np.sin(np.cumsum(TWO_PI * frequency / RATE))
    whine *= smoothstep(0.0, 0.4, t) * (1.0 - smoothstep(1.8, 2.4, t)) * 0.22
    sub = sub_sweep(seconds, 70.0, 20.0, 0.02, 0.9) * 0.75
    snap = metal_ring(seconds, 100.0, 0.5, 0.30)
    pressure = fft_filter(rng.standard_normal(len(t)), low=800.0, high=40.0)
    pressure *= exp_env(seconds, 0.02, 0.7) * 0.35
    mix = fft_filter(sub + whine + snap + pressure, low=1500.0)
    mix *= 1.0 - smoothstep(2.2, 2.4, t)
    return master(reverb(mix, mix=0.26, decay=1.6, seed=SEED + 12), -14.0)


def aftermath() -> np.ndarray:
    seconds = 1.2
    rng = np.random.default_rng(SEED + 13)
    t = t_axis(seconds)
    embers = np.zeros_like(t)
    for index in range(10):
        start = rng.uniform(0.0, seconds - 0.05)
        length = int(rng.uniform(0.006, 0.025) * RATE)
        offset = int(start * RATE)
        burst = rng.standard_normal(length) * np.exp(
            -np.arange(length) / max(1.0, length * 0.25)
        )
        embers[offset:offset + length] += burst * rng.uniform(0.05, 0.18)
    metal = metal_groan(seconds, rng, 70.0, 0.6, 0.12)
    metal *= smoothstep(0.0, 0.25, t) * np.exp(-t / 0.55)
    sub = np.sin(TWO_PI * 32.0 * t) * np.exp(-t / 0.5) * 0.55
    mix = fft_filter(embers + metal + sub, low=900.0)
    return master(reverb(mix, mix=0.35, decay=0.8, seed=SEED + 14), -20.0)


BUILDERS = {
    "warning": warning,
    "arrival": arrival,
    "charge": charge,
    "descent": descent,
    "fire": fire,
    "impact": impact,
    "drone": drone,
    "crackle": crackle,
    "collapse": collapse,
    "aftermath": aftermath,
}


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="Generate the orbital-laser sound set.")
    parser.add_argument(
        "--only",
        help="comma-separated stems to render; omit to rebuild the whole set",
    )
    args = parser.parse_args(argv)

    names = list(BUILDERS)
    if args.only:
        names = [name.strip() for name in args.only.split(",") if name.strip()]
        unknown = [name for name in names if name not in BUILDERS]
        if unknown:
            parser.error("unknown stems: " + ", ".join(unknown))
        if not names:
            parser.error("--only needs at least one stem")
    failed = [name for name in names if not write_ogg(name, BUILDERS[name]())]
    if failed:
        raise SystemExit("Bright or noisy stems: " + ", ".join(failed))


if __name__ == "__main__":
    main()
