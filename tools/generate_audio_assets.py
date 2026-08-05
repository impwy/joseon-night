#!/usr/bin/env python3
"""Generate and verify Joseon Night audio without external samples or dependencies."""

from __future__ import annotations

import argparse
import math
import struct
import sys
import wave
from array import array
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


SAMPLE_RATE = 22_050
CHANNELS = 2
SAMPLE_WIDTH = 2
MAX_AMPLITUDE = 0.92
ASSET_DIRECTORY = (
    Path(__file__).resolve().parents[1]
    / "desktop-app"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "audio"
)


@dataclass(frozen=True)
class EffectSpec:
    filename: str
    duration: float
    start_frequency: float
    end_frequency: float
    noise: float
    pan: float
    harmonic: float
    seed: int

    @property
    def frames(self) -> int:
        return round(self.duration * SAMPLE_RATE)


EFFECTS = (
    EffectSpec("sfx-attack-seal-talisman.wav", 0.30, 920.0, 430.0, 0.01, -0.25, 0.35, 11),
    EffectSpec("sfx-attack-flame-fan.wav", 0.42, 240.0, 1_180.0, 0.16, 0.30, 0.20, 23),
    EffectSpec("sfx-attack-exorcist-sword.wav", 0.28, 1_520.0, 190.0, 0.08, -0.15, 0.45, 37),
    EffectSpec("sfx-attack-returning-boomerang.wav", 0.46, 360.0, 760.0, 0.04, 0.35, 0.55, 41),
    EffectSpec("sfx-attack-thunder-bell.wav", 0.60, 1_060.0, 310.0, 0.03, -0.05, 0.85, 53),
    EffectSpec("sfx-attack-spirit-gourd.wav", 0.50, 170.0, 540.0, 0.10, 0.20, 0.65, 67),
    EffectSpec("sfx-attack-ten-thousand-seal-array.wav", 0.72, 520.0, 1_380.0, 0.05, -0.30, 0.70, 79),
    EffectSpec("sfx-attack-heavenly-thunder-seal.wav", 0.68, 1_760.0, 260.0, 0.18, 0.25, 0.75, 83),
    EffectSpec("sfx-attack-inferno-returning-wheel.wav", 0.78, 260.0, 980.0, 0.20, 0.35, 0.50, 97),
    EffectSpec("sfx-attack-blue-flame-spirit-gourd.wav", 0.76, 190.0, 720.0, 0.12, -0.20, 0.80, 101),
    EffectSpec("sfx-attack-lunar-eclipse-twin-blades.wav", 0.64, 1_280.0, 240.0, 0.07, -0.35, 0.60, 113),
    EffectSpec("sfx-attack-thunder-flame-divine-orb.wav", 0.82, 420.0, 1_620.0, 0.22, 0.10, 0.90, 127),
)

BGM_SPECS = {
    "bgm-lobby.wav": 36,
    "bgm-combat.wav": 48,
}


class DeterministicNoise:
    def __init__(self, seed: int) -> None:
        self._state = seed & 0xFFFFFFFF

    def next(self) -> float:
        self._state = (1_664_525 * self._state + 1_013_904_223) & 0xFFFFFFFF
        return (self._state / 0xFFFFFFFF) * 2.0 - 1.0


def smoothstep(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return value * value * (3.0 - 2.0 * value)


def encode_sample(value: float) -> int:
    limited = max(-MAX_AMPLITUDE, min(MAX_AMPLITUDE, value))
    return round(limited * 32_767.0)


def write_frames(path: Path, frames: Iterable[tuple[int, int]]) -> None:
    with wave.open(str(path), "wb") as output:
        output.setnchannels(CHANNELS)
        output.setsampwidth(SAMPLE_WIDTH)
        output.setframerate(SAMPLE_RATE)
        chunk = bytearray()
        for left, right in frames:
            chunk.extend(struct.pack("<hh", left, right))
            if len(chunk) >= 16_384:
                output.writeframesraw(chunk)
                chunk.clear()
        if chunk:
            output.writeframesraw(chunk)


def bgm_frames(duration: int, combat: bool):
    total_frames = duration * SAMPLE_RATE
    beats_per_second = (120.0 if combat else 80.0) / 60.0
    root = 82.4069 if combat else 110.0
    scale = (1.0, 9.0 / 8.0, 6.0 / 5.0, 3.0 / 2.0, 5.0 / 3.0)
    pattern = (0, 2, 4, 2, 1, 3, 4, 3) if combat else (0, 2, 3, 2, 1, 2, 4, 2)
    noise = DeterministicNoise(2_026 if combat else 1_392)
    edge_seconds = 0.05

    for frame in range(total_frames):
        time = frame / SAMPLE_RATE
        progress = time / duration
        beat = time * beats_per_second
        beat_index = int(beat)
        beat_phase = beat - beat_index
        note_frequency = root * 2.0 * scale[pattern[beat_index % len(pattern)]]
        pluck_envelope = min(1.0, beat_phase * 35.0) * math.exp(-4.8 * beat_phase)

        drone = 0.13 * math.sin(2.0 * math.pi * root * time)
        drone += 0.07 * math.sin(2.0 * math.pi * root * 1.5 * time + 0.3)
        pluck_left = 0.20 * pluck_envelope * math.sin(2.0 * math.pi * note_frequency * time)
        pluck_right = 0.20 * pluck_envelope * math.sin(
            2.0 * math.pi * note_frequency * time + 0.08
        )
        shimmer = 0.035 * math.sin(2.0 * math.pi * root * 4.0 * time + 0.4)

        rhythm = 0.0
        if combat:
            kick_frequency = 72.0 - 34.0 * beat_phase
            kick = 0.22 * math.exp(-9.0 * beat_phase) * math.sin(
                2.0 * math.pi * kick_frequency * time
            )
            half_beat = (beat * 2.0) % 1.0
            noise_envelope = math.exp(-18.0 * half_beat)
            rhythm = kick + 0.055 * noise_envelope * noise.next()

        edge = smoothstep(min(time / edge_seconds, (duration - time) / edge_seconds))
        motion = 0.02 * math.sin(2.0 * math.pi * 4.0 * progress)
        left = edge * (drone + pluck_left + shimmer + rhythm + motion)
        right = edge * (drone + pluck_right - shimmer + rhythm - motion)
        yield encode_sample(left), encode_sample(right)


def effect_frames(spec: EffectSpec):
    noise = DeterministicNoise(spec.seed)
    phase = 0.0
    second_phase = 0.0
    frames = spec.frames

    for frame in range(frames):
        progress = frame / max(1, frames - 1)
        curved = smoothstep(progress)
        frequency = spec.start_frequency + (spec.end_frequency - spec.start_frequency) * curved
        frequency *= 1.0 + 0.045 * math.sin(2.0 * math.pi * progress * 3.0)
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        second_phase += 2.0 * math.pi * (frequency * 0.505) / SAMPLE_RATE

        attack = smoothstep(min(1.0, frame / (SAMPLE_RATE * 0.008)))
        tail = smoothstep(1.0 - progress)
        decay = math.exp(-2.0 * progress) * tail
        envelope = attack * decay
        body = math.sin(phase)
        body += spec.harmonic * 0.42 * math.sin(phase * 2.0 + 0.25)
        body += spec.harmonic * 0.22 * math.sin(second_phase + 0.7)
        transient = spec.noise * noise.next() * math.exp(-8.0 * progress)
        pulse = 0.08 * math.sin(2.0 * math.pi * (5.0 + spec.seed % 4) * progress)
        mono = 0.48 * envelope * (body + transient + pulse)

        pan_motion = 0.18 * math.sin(2.0 * math.pi * progress)
        pan = max(-1.0, min(1.0, spec.pan + pan_motion))
        left_gain = 1.0 - max(0.0, pan) * 0.45
        right_gain = 1.0 + min(0.0, pan) * 0.45
        stereo_offset = 0.025 * envelope * math.sin(phase + math.pi / 3.0)
        yield encode_sample(mono * left_gain + stereo_offset), encode_sample(
            mono * right_gain - stereo_offset
        )


def generate_assets() -> None:
    ASSET_DIRECTORY.mkdir(parents=True, exist_ok=True)
    write_frames(ASSET_DIRECTORY / "bgm-lobby.wav", bgm_frames(36, combat=False))
    write_frames(ASSET_DIRECTORY / "bgm-combat.wav", bgm_frames(48, combat=True))
    for effect in EFFECTS:
        write_frames(ASSET_DIRECTORY / effect.filename, effect_frames(effect))


def load_samples(path: Path) -> tuple[wave._wave_params, array]:
    with wave.open(str(path), "rb") as source:
        parameters = source.getparams()
        samples = array("h")
        samples.frombytes(source.readframes(parameters.nframes))
    if sys.byteorder == "big":
        samples.byteswap()
    return parameters, samples


def verify_asset(path: Path, expected_frames: int, looped: bool) -> str:
    if not path.is_file():
        raise AssertionError(f"missing asset: {path}")
    parameters, samples = load_samples(path)
    if parameters.nchannels != CHANNELS:
        raise AssertionError(f"{path.name}: expected stereo, got {parameters.nchannels} channels")
    if parameters.sampwidth != SAMPLE_WIDTH:
        raise AssertionError(f"{path.name}: expected 16-bit PCM")
    if parameters.framerate != SAMPLE_RATE:
        raise AssertionError(f"{path.name}: expected {SAMPLE_RATE} Hz")
    if parameters.comptype != "NONE":
        raise AssertionError(f"{path.name}: expected uncompressed PCM")
    if parameters.nframes != expected_frames:
        raise AssertionError(
            f"{path.name}: expected {expected_frames} frames, got {parameters.nframes}"
        )
    if len(samples) != expected_frames * CHANNELS:
        raise AssertionError(f"{path.name}: incomplete sample data")

    peak = max(abs(sample) for sample in samples)
    rms = math.sqrt(sum(sample * sample for sample in samples) / len(samples))
    if rms < 100.0:
        raise AssertionError(f"{path.name}: audio is silent or too quiet (RMS {rms:.1f})")
    if peak >= 32_000:
        raise AssertionError(f"{path.name}: clipping margin is too small (peak {peak})")

    if looped:
        for channel in range(CHANNELS):
            first = samples[channel]
            last = samples[-CHANNELS + channel]
            if abs(first) > 256 or abs(last) > 256 or abs(first - last) > 256:
                raise AssertionError(
                    f"{path.name}: discontinuous loop boundary on channel {channel}"
                )
        window_samples = SAMPLE_RATE // 100 * CHANNELS
        boundary_mean = (
            sum(abs(sample) for sample in samples[:window_samples])
            + sum(abs(sample) for sample in samples[-window_samples:])
        ) / (window_samples * 2)
        if boundary_mean > 2_500.0:
            raise AssertionError(
                f"{path.name}: loop boundary energy is too high ({boundary_mean:.1f})"
            )

    duration = parameters.nframes / parameters.framerate
    return f"{path.name}: {duration:.2f}s, RMS={rms:.0f}, peak={peak}"


def verify_assets() -> None:
    reports = []
    for filename, duration in BGM_SPECS.items():
        reports.append(
            verify_asset(ASSET_DIRECTORY / filename, duration * SAMPLE_RATE, looped=True)
        )
    for effect in EFFECTS:
        reports.append(
            verify_asset(ASSET_DIRECTORY / effect.filename, effect.frames, looped=False)
        )
    print("Verified deterministic 22.05 kHz, 16-bit stereo WAV assets:")
    for report in reports:
        print(f"  {report}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check-only",
        action="store_true",
        help="verify existing assets without regenerating them",
    )
    arguments = parser.parse_args()
    if not arguments.check_only:
        generate_assets()
    verify_assets()


if __name__ == "__main__":
    main()
