#!/usr/bin/env python3
"""Synthesise the QuasarLib black-hole sound set.

Original assets only: every sound is generated from oscillators, noise, and a
synthetic convolution reverb. The palette is deliberately dark and mechanical:
industrial pulse drone, electrical hum, low sub-bass, and metal grinding. There
are no samples from the reference tracks; only their character is reproduced.

Run from the repository root:

    python3 tools/audio/gen_blackhole_audio.py

The script writes OGG Vorbis files under
``src/main/resources/assets/quasar_content/sounds/black_hole/``, prints spectral
checks, and exits non-zero when a stem becomes too bright or too sharp.
"""

from __future__ import annotations

import math
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

RATE = 44_100
ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/assets/quasar_content/sounds/black_hole"
SEED = 0x0B1AC

TWO_PI = 2.0 * math.pi

# Maximum spectral centroid, maximum energy above 4 kHz, minimum energy below
# 100 Hz. The black hole must feel heavy, never sharp.
TARGETS = {
    "dread": (650.0, 0.020, 0.30, 0.08),
    "drone": (500.0, 0.005, 0.45, 0.05),
    "ignition": (600.0, 0.010, 0.35, 0.15),
    "shockwave": (450.0, 0.005, 0.45, 0.25),
    "accretion": (700.0, 0.010, 0.15, 0.10),
    "pulse": (400.0, 0.005, 0.45, 0.10),
    "collapse": (650.0, 0.010, 0.22, 0.12),
    "implosion": (600.0, 0.010, 0.30, 0.15),
    "echo": (600.0, 0.010, 0.18, 0.10),
}


def t_axis(seconds: float) -> np.ndarray:
    return np.arange(int(round(seconds * RATE)), dtype=np.float64) / RATE


def smoothstep(edge0: float, edge1: float, value: np.ndarray) -> np.ndarray:
    if edge1 <= edge0:
        return (value >= edge1).astype(np.float64)
    x = np.clip((value - edge0) / (edge1 - edge0), 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def sine(frequency: np.ndarray | float, seconds: float, phase: float = 0.0) -> np.ndarray:
    t = t_axis(seconds)
    if np.isscalar(frequency):
        return np.sin(TWO_PI * float(frequency) * t + phase)
    phase_curve = TWO_PI * np.cumsum(np.asarray(frequency, dtype=np.float64)) / RATE
    return np.sin(phase_curve + phase)


def noise(seconds: float, rng: np.random.Generator) -> np.ndarray:
    return rng.standard_normal(int(round(seconds * RATE)))


def exp_env(seconds: float, attack: float, decay: float) -> np.ndarray:
    t = t_axis(seconds)
    attack_curve = np.clip(t / max(attack, 1e-4), 0.0, 1.0)
    return attack_curve * np.exp(-np.maximum(t - attack, 0.0) / max(decay, 1e-4))


def fft_filter(signal: np.ndarray, low: float | None = None, high: float | None = None,
               order: int = 2) -> np.ndarray:
    spectrum = np.fft.rfft(signal)
    frequencies = np.fft.rfftfreq(len(signal), 1.0 / RATE)
    gain = np.ones_like(frequencies)
    if low is not None:
        gain *= 1.0 / np.sqrt(1.0 + (frequencies / low) ** (2 * order))
    if high is not None:
        gain *= 1.0 / np.sqrt(1.0 + (high / np.maximum(frequencies, 1e-6)) ** (2 * order))
    return np.fft.irfft(spectrum * gain, n=len(signal))


def sub_sweep(seconds: float, start_hz: float, end_hz: float, attack: float,
              decay: float, curve: float = 1.0) -> np.ndarray:
    t = t_axis(seconds)
    progress = np.clip(t / seconds, 0.0, 1.0) ** curve
    frequency = start_hz * (end_hz / start_hz) ** progress
    phase = np.cumsum(TWO_PI * frequency / RATE)
    return np.sin(phase) * exp_env(seconds, attack, decay)


def add_at(target: np.ndarray, source: np.ndarray, start_seconds: float) -> None:
    start = int(round(start_seconds * RATE))
    end = min(len(target), start + len(source))
    if start < len(target):
        target[start:end] += source[: end - start]


def metal_groan(seconds: float, rng: np.random.Generator, base: float,
                rate: float, amount: float = 0.5, rough_hz: float = 7.0) -> np.ndarray:
    """Low inharmonic metal groan: tonal, swelling, and deliberately not noisy."""
    t = t_axis(seconds)
    tone = np.zeros_like(t)
    ratios = (1.0, 1.51, 2.32, 3.45)
    gains = (1.0, 0.55, 0.30, 0.18)
    for index, (ratio, gain) in enumerate(zip(ratios, gains)):
        wobble = 1.0 + 0.004 * np.sin(
            TWO_PI * (0.07 + index * 0.013) * t + index * 1.7
        )
        tone += np.sin(TWO_PI * base * ratio * wobble * t
                       + rng.uniform(0.0, TWO_PI)) * gain
    swell = 0.58 + 0.42 * np.sin(
        TWO_PI * rate * t + 0.6 * np.sin(TWO_PI * rate * 0.37 * t)
    )
    rough = 0.78 + 0.22 * np.sin(
        TWO_PI * rough_hz * t + 0.4 * np.sin(TWO_PI * 0.53 * t)
    )
    body = np.tanh(tone * 1.35) / np.tanh(1.35)
    return body * swell * rough * amount


def metal_ring(seconds: float, base: float, decay: float,
               amount: float = 0.2) -> np.ndarray:
    """Short inharmonic metal strike or tail."""
    t = t_axis(seconds)
    tone = np.zeros_like(t)
    for index, (ratio, gain) in enumerate(zip((1.0, 1.51, 2.32, 3.45),
                                              (1.0, 0.55, 0.30, 0.18))):
        tone += np.sin(TWO_PI * base * ratio * t + index * 1.1) * gain
    return (np.tanh(tone * 1.2) / np.tanh(1.2)) * np.exp(-t / max(decay, 1e-4)) * amount


def hum(seconds: float, base: float = 50.0) -> np.ndarray:
    t = t_axis(seconds)
    signal = np.sin(TWO_PI * base * t) + 0.25 * np.sin(TWO_PI * base * 2.0 * t)
    return signal * (0.75 + 0.25 * np.sin(TWO_PI * 0.1 * t))


def reverb(signal: np.ndarray, mix: float, decay: float, seed: int,
           low: float = 4200.0, high: float = 90.0) -> np.ndarray:
    rng = np.random.default_rng(seed)
    length = int(round(decay * RATE))
    impulse = rng.standard_normal(length)
    impulse *= np.exp(-np.arange(length) / (decay * RATE / 3.5))
    impulse = fft_filter(impulse, low=low, high=high)
    impulse[: int(0.020 * RATE)] = 0.0
    impulse /= max(np.max(np.abs(impulse)), 1e-9)

    size = 1 << (len(signal) + length - 1).bit_length()
    wet = np.fft.irfft(
        np.fft.rfft(signal, size) * np.fft.rfft(impulse, size), size
    )[: len(signal)]
    return (1.0 - mix) * signal + mix * wet


def widen(signal: np.ndarray, delay_ms: float = 14.0, width: float = 1.0) -> np.ndarray:
    delay = int(round(delay_ms * RATE / 1000.0))
    delayed = np.concatenate([np.zeros(delay), signal[:-delay]]) if delay else signal
    mid = 0.5 * (signal + delayed)
    side = 0.5 * (signal - delayed) * width
    return np.stack([mid + side, mid - side], axis=1)


def loop_crossfade(signal: np.ndarray, seconds: float) -> np.ndarray:
    fade = int(round(seconds * RATE))
    if fade <= 0 or len(signal) <= fade * 2:
        return signal
    head = signal[:fade].copy()
    tail = signal[len(signal) - fade:]
    ramp = np.linspace(0.0, 1.0, fade)
    if signal.ndim == 2:
        ramp = ramp[:, None]
    signal = signal[: len(signal) - fade].copy()
    signal[:fade] = head * ramp + tail * (1.0 - ramp)
    return signal


def normalise(signal: np.ndarray, peak_db: float = -1.5) -> np.ndarray:
    peak = np.max(np.abs(signal))
    target = 10.0 ** (peak_db / 20.0)
    if peak < 1e-9:
        return signal
    return signal * (target / peak)


def master(signal: np.ndarray, rms_db: float, peak_db: float = -1.5) -> np.ndarray:
    """Lift a stem to a target loudness, then softly limit and peak-normalise."""
    mono = signal.mean(axis=1) if signal.ndim == 2 else signal
    rms = math.sqrt(float(np.mean(mono ** 2)))
    gain = 10.0 ** (rms_db / 20.0) / max(rms, 1e-9)
    signal = np.tanh(signal * gain * 1.15) / math.tanh(1.15)
    return normalise(signal, peak_db)


def spectral_stats(signal: np.ndarray) -> tuple[float, float, float, float]:
    mono = signal.mean(axis=1) if signal.ndim == 2 else signal
    spectrum = np.abs(np.fft.rfft(mono))
    frequencies = np.fft.rfftfreq(len(mono), 1.0 / RATE)
    power = spectrum ** 2
    total = max(float(power.sum()), 1e-12)
    centroid = float((power * frequencies).sum() / total)
    low = float(power[frequencies < 100.0].sum() / total)
    high = float(power[frequencies > 4000.0].sum() / total)
    band = power[(frequencies >= 100.0) & (frequencies <= 4000.0)]
    floor = max(float(np.max(band)) * 1.0e-6, 1.0e-12) if len(band) else 1.0e-12
    flatness = float(np.exp(np.mean(np.log(band + floor)))
                     / max(float(np.mean(band + floor)), 1e-12))
    return centroid, low, high, flatness


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
    passed = (centroid <= max_centroid and high <= max_high
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


def dread() -> np.ndarray:
    seconds = 6.0
    rng = np.random.default_rng(SEED + 1)
    t = t_axis(seconds)
    sub = sub_sweep(seconds, 20.0, 45.0, 0.8, 3.5, 1.4) * 0.58
    metal = metal_groan(seconds, rng, 45.0, 0.5, 0.38)
    metal *= smoothstep(0.0, 3.5, t)
    air = fft_filter(rng.standard_normal(len(t)), low=300.0, high=80.0)
    air *= smoothstep(0.0, 3.0, t) * (1.0 - smoothstep(5.0, 6.0, t)) * 0.06
    mix = fft_filter(sub + metal + air, low=1000.0)
    return master(reverb(mix, mix=0.30, decay=2.8, seed=SEED + 2), -20.0)


def drone() -> np.ndarray:
    seconds = 30.0
    rng = np.random.default_rng(SEED + 3)
    t = t_axis(seconds)

    sub = np.zeros_like(t)
    for base, gain in ((24.0, 0.34), (36.0, 0.20), (48.0, 0.12)):
        for detune in (-0.08, 0.08):
            sub += np.sin(TWO_PI * (base + detune) * t + rng.uniform(0.0, TWO_PI)) * gain
    sub *= 0.90 + 0.10 * np.sin(TWO_PI * (1.0 / seconds) * t)

    electrical = hum(seconds, 50.0) * 0.05
    metal = metal_groan(seconds, rng, 48.0, 1.0 / seconds, 0.65)
    mix = fft_filter(sub + electrical + metal, low=900.0)
    mix = reverb(mix, mix=0.30, decay=3.2, seed=SEED + 4)
    mix = loop_crossfade(mix, 0.8)
    return master(mix, -20.0, -3.0)


def ignition() -> np.ndarray:
    seconds = 5.0
    rng = np.random.default_rng(SEED + 5)
    t = t_axis(seconds)
    sub = sub_sweep(seconds, 78.0, 18.0, 0.045, 1.4) * 0.95
    body = sub_sweep(seconds, 96.0, 54.0, 0.04, 1.2) * 0.45
    pressure = fft_filter(rng.standard_normal(len(t)), low=420.0, high=35.0)
    pressure *= exp_env(seconds, 0.03, 1.8) * 0.65
    metal = metal_ring(seconds, 72.0, 1.2, 0.16)
    thump = np.sin(TWO_PI * 110.0 * t) * exp_env(seconds, 0.03, 0.55) * 0.12
    mix = fft_filter(sub + body + pressure + metal + thump, low=1200.0)
    return master(widen(reverb(mix, mix=0.25, decay=2.4, seed=SEED + 6), 10.0, 0.7), -12.0)


def shockwave() -> np.ndarray:
    seconds = 4.0
    rng = np.random.default_rng(SEED + 7)
    pressure = fft_filter(rng.standard_normal(len(t_axis(seconds))), low=380.0, high=25.0)
    pressure *= exp_env(seconds, 0.02, 1.4) * 0.8
    sub = sub_sweep(seconds, 52.0, 28.0, 0.02, 0.9) * 0.5
    mix = fft_filter(pressure + sub, low=800.0)
    return master(widen(reverb(mix, mix=0.20, decay=1.8, seed=SEED + 8), 16.0, 1.0), -13.0)


def accretion() -> np.ndarray:
    seconds = 24.0
    rng = np.random.default_rng(SEED + 9)
    t = t_axis(seconds)
    metal = metal_groan(seconds, rng, 60.0, 3.0 / seconds, 0.40)
    sub = np.sin(TWO_PI * 32.0 * t) * 0.10
    mix = fft_filter(metal + sub, low=700.0)
    mix = reverb(mix, mix=0.22, decay=2.2, seed=SEED + 10)
    mix = loop_crossfade(mix, 0.6)
    return master(mix, -26.0, -5.0)


def pulse() -> np.ndarray:
    seconds = 2.5
    rng = np.random.default_rng(SEED + 11)
    t = t_axis(seconds)
    sub = sub_sweep(seconds, 55.0, 28.0, 0.010, 0.55) * 0.85
    metal = metal_ring(seconds, 90.0, 0.8, 0.18)
    body = fft_filter(rng.standard_normal(len(t)), low=200.0)
    body *= exp_env(seconds, 0.005, 0.30) * 0.22
    mix = fft_filter(sub + metal + body, low=900.0)
    return master(reverb(mix, mix=0.24, decay=1.4, seed=SEED + 12), -18.0)


def collapse() -> np.ndarray:
    seconds = 5.0
    rng = np.random.default_rng(SEED + 13)
    t = t_axis(seconds)
    sub = sub_sweep(seconds, 42.0, 18.0, 0.5, 4.0, 1.2) * 0.50
    metal = metal_groan(seconds, rng, 60.0, 0.8, 0.50)
    metal *= smoothstep(0.0, 4.0, t) * (0.30 + 0.70 * t / seconds)
    suction = fft_filter(rng.standard_normal(len(t)), low=500.0, high=80.0)
    suction *= smoothstep(0.0, 3.0, t) * 0.10
    mix = fft_filter(sub + metal + suction, low=1000.0)
    mix *= 1.0 - smoothstep(4.6, 5.0, t)
    return master(widen(reverb(mix, mix=0.28, decay=2.2, seed=SEED + 14), 9.0, 0.6), -14.0)


def implosion() -> np.ndarray:
    seconds = 2.4
    rng = np.random.default_rng(SEED + 15)
    t = t_axis(seconds)
    sub = sub_sweep(seconds, 34.0, 17.0, 0.040, 0.8) * 0.9
    pressure = fft_filter(rng.standard_normal(len(t)), low=300.0, high=30.0)
    pressure *= exp_env(seconds, 0.03, 0.9) * 0.55
    metal = metal_ring(seconds, 57.0, 1.0, 0.16)
    gap = 1.0 - 0.8 * np.exp(-((t - 0.5) / 0.12) ** 2)
    mix = fft_filter(sub + pressure + metal, low=1000.0) * gap
    return master(widen(reverb(mix, mix=0.30, decay=2.0, seed=SEED + 16), 7.0, 0.8), -10.0)


def echo() -> np.ndarray:
    seconds = 1.6
    rng = np.random.default_rng(SEED + 17)
    t = t_axis(seconds)
    metal = metal_groan(seconds, rng, 70.0, 0.6, 0.25)
    metal *= smoothstep(0.0, 0.4, t) * np.exp(-t / 0.8)
    sub = np.sin(TWO_PI * 32.0 * t) * np.exp(-t / 0.7) * 1.10
    mix = fft_filter(metal + sub, low=700.0)
    mix = reverb(mix, mix=0.40, decay=1.5, seed=SEED + 18)
    return master(widen(mix, 19.0, 1.0), -18.0)


def main() -> None:
    sounds = {
        "dread": dread(),
        "drone": drone(),
        "ignition": ignition(),
        "shockwave": shockwave(),
        "accretion": accretion(),
        "pulse": pulse(),
        "collapse": collapse(),
        "implosion": implosion(),
        "echo": echo(),
    }
    failed = [name for name, signal in sounds.items() if not write_ogg(name, signal)]
    if failed:
        raise SystemExit("Bright or sharp stems: " + ", ".join(failed))


if __name__ == "__main__":
    main()
