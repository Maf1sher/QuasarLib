#!/usr/bin/env python3
"""Synthesise the QuasarLib black-hole sound set.

Original assets only: every sound is generated from oscillators, noise, and a
synthetic convolution reverb. Run from the repository root:

    python3 tools/audio/gen_blackhole_audio.py

The script writes OGG Vorbis files under
``src/main/resources/assets/quasar_content/sounds/black_hole/`` and prints
objective checks (peak, RMS, loop seam) for each file.
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


def reverb(signal: np.ndarray, mix: float, decay: float, seed: int,
           low: float = 6000.0, high: float = 120.0) -> np.ndarray:
    rng = np.random.default_rng(seed)
    length = int(round(decay * RATE))
    impulse = rng.standard_normal(length)
    impulse *= np.exp(-np.arange(length) / (decay * RATE / 3.5))
    impulse = fft_filter(impulse, low=low, high=high)
    impulse[: int(0.018 * RATE)] = 0.0
    impulse /= max(np.max(np.abs(impulse)), 1e-9)

    size = 1 << (len(signal) + length - 1).bit_length()
    wet = np.fft.irfft(
        np.fft.rfft(signal, size) * np.fft.rfft(impulse, size), size
    )[: len(signal)]
    return (1.0 - mix) * signal + mix * wet


def stereo(left: np.ndarray, right: np.ndarray, width: float = 1.0) -> np.ndarray:
    mid = 0.5 * (left + right)
    side = 0.5 * (left - right) * width
    return np.stack([mid + side, mid - side], axis=1)


def widen(signal: np.ndarray, delay_ms: float = 14.0, width: float = 1.0) -> np.ndarray:
    delay = int(round(delay_ms * RATE / 1000.0))
    delayed = np.concatenate([np.zeros(delay), signal[:-delay]]) if delay else signal
    return stereo(signal, delayed, width)


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


def report(name: str, signal: np.ndarray) -> None:
    mono = signal.mean(axis=1) if signal.ndim == 2 else signal
    peak = 20.0 * math.log10(max(np.max(np.abs(mono)), 1e-9))
    rms = 20.0 * math.log10(max(np.sqrt(np.mean(mono ** 2)), 1e-9))
    seam = abs(mono[0] - mono[-1])
    window = max(1, int(0.02 * RATE))
    seam_rms = abs(
        float(np.sqrt(np.mean(mono[:window] ** 2)))
        - float(np.sqrt(np.mean(mono[-window:] ** 2)))
    )
    print(f"{name:10s} {len(mono) / RATE:6.2f}s  peak {peak:6.2f} dBFS  "
          f"rms {rms:6.2f} dBFS  seam {seam:.4f}/{seam_rms:.4f}")


def write_ogg(name: str, signal: np.ndarray) -> None:
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
    report(name, signal)


def dread() -> np.ndarray:
    seconds = 6.0
    rng = np.random.default_rng(SEED + 1)
    t = t_axis(seconds)
    frequency = 24.0 * np.exp(t / seconds * math.log(55.0 / 24.0))
    sub = sine(frequency, seconds) * smoothstep(0.0, 4.5, t) * (0.55 + 0.45 * t / seconds)

    breath = fft_filter(rng.standard_normal(len(t)), low=2600.0, high=180.0)
    breath *= smoothstep(0.0, 1.2, t) * np.clip((seconds - t) / 1.5, 0.0, 1.0) * 0.32

    shimmer = np.zeros_like(t)
    for index, freq in enumerate((1180.0, 1770.0, 2630.0)):
        vibrato = 1.0 + 0.006 * np.sin(TWO_PI * (0.11 + index * 0.03) * t)
        shimmer += np.sin(TWO_PI * freq * vibrato * t + index) * (0.10 / (index + 1))
    shimmer *= smoothstep(0.4, 5.0, t) * 0.5

    mix = sub + breath + shimmer
    return master(reverb(mix, mix=0.34, decay=3.0, seed=SEED + 2, low=4500.0, high=90.0), -20.0)


def drone() -> np.ndarray:
    seconds = 30.0
    rng = np.random.default_rng(SEED + 3)
    t = t_axis(seconds)
    beat = np.zeros_like(t)
    for base, gain in ((32.7, 0.55), (49.0, 0.34), (65.4, 0.24)):
        for detune in (-0.11, 0.11):
            beat += np.sin(TWO_PI * (base + detune) * t + rng.uniform(0.0, TWO_PI)) * gain
    beat *= 0.6 * (0.82 + 0.18 * np.sin(TWO_PI * (2.0 / seconds) * t))

    saw = np.zeros_like(t)
    for harmonic in range(1, 9):
        saw += np.sin(TWO_PI * 32.7 * harmonic * t) / harmonic
    saw = fft_filter(saw, low=420.0, order=2) * 0.22

    choir = np.zeros_like(t)
    chord = (130.8, 196.0, 261.6, 311.1)
    for index, freq in enumerate(chord):
        choir += np.sin(TWO_PI * freq * (1.0 + 0.004 * np.sin(TWO_PI * 0.05 * t + index)) * t
                        + index * 1.7)
    choir = fft_filter(choir, low=2600.0, high=260.0) * 0.14
    choir *= 0.8 + 0.2 * np.sin(TWO_PI * (3.0 / seconds) * t + 1.1)

    crackle = np.zeros_like(t)
    for _ in range(170):
        start = rng.integers(0, len(t) - 400)
        length = rng.integers(120, 500)
        burst = rng.standard_normal(length) * np.exp(-np.arange(length) / 90.0)
        crackle[start:start + length] += fft_filter(burst, high=1800.0) * rng.uniform(0.01, 0.05)

    core = beat + saw + choir + crackle
    core = reverb(core, mix=0.28, decay=3.5, seed=SEED + 4, low=3800.0, high=70.0)
    core = loop_crossfade(core, 0.8)
    return master(core, -20.0, -3.0)


def ignition() -> np.ndarray:
    seconds = 5.0
    rng = np.random.default_rng(SEED + 5)
    t = t_axis(seconds)
    riser = fft_filter(rng.standard_normal(len(t)), low=3200.0, high=220.0)
    riser *= smoothstep(0.0, 0.46, t) * np.clip((0.56 - t) / 0.12, 0.0, 1.0) * 0.5

    impact_time = 0.48
    shifted = np.clip(t - impact_time, 0.0, None)
    sub_frequency = 90.0 * np.exp(-shifted / 0.55) + 22.0
    sub = sine(sub_frequency, seconds) * exp_env(seconds, impact_time + 0.005, 0.9)
    sub *= (t >= impact_time)

    crack = fft_filter(rng.standard_normal(len(t)), low=14000.0, high=90.0)
    crack *= exp_env(seconds, impact_time + 0.001, 0.35) * 0.8

    metal = np.zeros_like(t)
    for index, ratio in enumerate((1.0, 2.76, 5.4, 8.9)):
        metal += np.sin(TWO_PI * 54.0 * ratio * (t - impact_time) + index) / (index + 1.5)
    metal *= exp_env(seconds, impact_time + 0.005, 1.4) * 0.35

    mix = riser + sub + crack + metal
    mix = reverb(mix, mix=0.24, decay=2.6, seed=SEED + 6, low=5200.0, high=80.0)
    return master(widen(mix, 11.0, 0.7), -12.0)


def shockwave() -> np.ndarray:
    seconds = 4.0
    rng = np.random.default_rng(SEED + 7)
    t = t_axis(seconds)
    source = rng.standard_normal(len(t))
    sweep = np.zeros_like(t)
    for low, gain in ((180.0, 0.55), (900.0, 0.35), (4000.0, 0.25)):
        band = fft_filter(source, low=low, high=low * 0.25)
        weight = np.exp(-((t - (0.15 + low / 9000.0)) / 0.55) ** 2)
        sweep += band * weight * gain
    sweep *= exp_env(seconds, 0.008, 1.1) * 0.75

    boom = sine(45.0 * np.exp(-t / 1.4) + 30.0, seconds) * exp_env(seconds, 0.01, 0.7) * 0.5
    mix = sweep + boom
    mix = reverb(mix, mix=0.20, decay=1.8, seed=SEED + 8, low=7000.0, high=90.0)
    return master(widen(mix, 18.0, 1.0), -13.0)


def accretion() -> np.ndarray:
    seconds = 24.0
    rng = np.random.default_rng(SEED + 9)
    t = t_axis(seconds)
    hiss = fft_filter(rng.standard_normal(len(t)), low=900.0, high=90.0)
    hiss *= 0.35 + 0.65 * (0.5 + 0.5 * np.sin(TWO_PI * (3.0 / seconds) * t))
    hiss *= 0.45

    sparks = np.zeros_like(t)
    for _ in range(420):
        start = rng.integers(0, len(t) - 600)
        length = rng.integers(80, 600)
        burst = rng.standard_normal(length) * np.exp(-np.arange(length) / 120.0)
        sparks[start:start + length] += fft_filter(burst, high=2400.0) * rng.uniform(0.01, 0.045)

    mix = hiss + sparks
    mix = reverb(mix, mix=0.20, decay=2.4, seed=SEED + 10, low=6000.0, high=100.0)
    mix = loop_crossfade(mix, 0.6)
    return master(mix, -26.0, -5.0)


def pulse() -> np.ndarray:
    seconds = 2.5
    rng = np.random.default_rng(SEED + 11)
    t = t_axis(seconds)
    sub = sine(60.0 * np.exp(-t / 0.35) + 34.0, seconds) * exp_env(seconds, 0.008, 0.55) * 0.7
    thump = fft_filter(rng.standard_normal(len(t)), low=180.0) * exp_env(seconds, 0.005, 0.30) * 0.5
    tail = np.zeros_like(t)
    for index, freq in enumerate((180.0, 460.0, 900.0)):
        tail += np.sin(TWO_PI * freq * t + index) * np.exp(-t / 0.7) / (index + 1.5)
    tail *= 0.22
    mix = reverb(sub + thump + tail, mix=0.26, decay=1.5, seed=SEED + 12)
    return master(mix, -17.0)


def collapse() -> np.ndarray:
    seconds = 5.0
    rng = np.random.default_rng(SEED + 13)
    t = t_axis(seconds)
    progress = np.clip(t / seconds, 0.0, 1.0)

    # Shepard-style endless rise: each octave glides through a log-frequency
    # bell, so the wrapping component is inaudible when it returns to the bottom.
    shepard = np.zeros_like(t)
    for octave in range(8):
        position = (progress * 3.0 + octave) % 8.0
        amplitude = np.exp(-((position - 4.0) ** 2) / 1.5)
        frequency = 32.0 * (2.0 ** position)
        phase = np.cumsum(TWO_PI * frequency / RATE)
        shepard += np.sin(phase) * amplitude
    shepard *= smoothstep(0.0, 3.5, t) * 0.22

    cluster = (np.sin(TWO_PI * 55.0 * t) + np.sin(TWO_PI * 58.3 * t)
               + np.sin(TWO_PI * 82.4 * t)) * 0.20 * smoothstep(0.5, 4.2, t)

    suction = fft_filter(rng.standard_normal(len(t)), low=2200.0, high=120.0)
    suction *= smoothstep(0.0, 1.0, t) * (0.35 + 0.65 * progress) * 0.35

    mix = (shepard + cluster + suction) * (1.0 - smoothstep(4.6, 5.0, t))
    mix = reverb(mix, mix=0.28, decay=2.4, seed=SEED + 14, low=5000.0, high=90.0)
    return master(widen(mix, 9.0, 0.6), -14.0)


def implosion() -> np.ndarray:
    seconds = 2.4
    rng = np.random.default_rng(SEED + 15)
    t = t_axis(seconds)
    impact_time = 0.30

    riser = fft_filter(rng.standard_normal(len(t)), low=9000.0, high=400.0)
    riser *= smoothstep(0.0, impact_time, t) * np.clip((impact_time + 0.01 - t) / 0.02, 0.0, 1.0)
    riser *= 0.35

    shifted = np.clip(t - impact_time, 0.0, None)
    sub = sine(30.0 * np.exp(-shifted / 0.9) + 17.0, seconds) * exp_env(seconds, impact_time + 0.004, 0.85)
    sub *= (t >= impact_time) * 0.85

    crack = fft_filter(rng.standard_normal(len(t)), low=16000.0, high=60.0)
    crack *= exp_env(seconds, impact_time + 0.001, 0.22) * 0.60

    shriek = np.sin(TWO_PI * 320.0 * (t - impact_time)
                    + 2.2 * np.exp(-shifted / 0.3) * np.sin(TWO_PI * 720.0 * (t - impact_time)))
    shriek *= exp_env(seconds, impact_time + 0.003, 0.55) * 0.20 * (t >= impact_time)

    gap = 1.0 - 0.85 * np.exp(-((t - (impact_time + 0.42)) / 0.10) ** 2)
    mix = fft_filter(riser + sub + crack + shriek, low=12000.0)
    mix *= gap
    mix = reverb(mix, mix=0.30, decay=2.0, seed=SEED + 16, low=6000.0, high=60.0)
    return master(widen(mix, 7.0, 0.8), -10.0)


def echo() -> np.ndarray:
    seconds = 1.6
    rng = np.random.default_rng(SEED + 17)
    t = t_axis(seconds)
    choir = np.zeros_like(t)
    for index, freq in enumerate((130.8, 196.0, 261.6, 311.1)):
        choir += np.sin(TWO_PI * freq * (1.0 + 0.003 * np.sin(TWO_PI * 0.4 * t + index)) * t
                        + index * 2.1)
    choir = fft_filter(choir, low=2400.0, high=220.0)
    choir *= smoothstep(0.0, 0.5, t) * np.exp(-t / 0.85) * 0.5

    rumble = fft_filter(rng.standard_normal(len(t)), low=120.0, high=30.0)
    rumble *= np.exp(-t / 0.7) * 0.25

    mix = reverb(choir + rumble, mix=0.40, decay=1.6, seed=SEED + 18, low=4000.0, high=80.0)
    return master(widen(mix, 21.0, 1.0), -18.0)


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
    for name, signal in sounds.items():
        write_ogg(name, signal)


if __name__ == "__main__":
    main()
