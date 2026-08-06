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
BGM_CROSSFADE_SECONDS = 0.75
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
    style: str
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
    EffectSpec("sfx-attack-seal-talisman.wav", "paper", 0.30, 920.0, 430.0, 0.08, -0.25, 0.35, 11),
    EffectSpec("sfx-attack-flame-fan.wav", "fire", 0.42, 240.0, 1_180.0, 0.16, 0.30, 0.20, 23),
    EffectSpec("sfx-attack-exorcist-sword.wav", "sword", 0.28, 1_520.0, 190.0, 0.24, -0.15, 0.45, 37),
    EffectSpec("sfx-attack-returning-boomerang.wav", "boomerang", 0.46, 360.0, 760.0, 0.04, 0.35, 0.55, 41),
    EffectSpec("sfx-attack-thunder-bell.wav", "lightning", 0.60, 1_060.0, 310.0, 0.28, -0.05, 0.85, 53),
    EffectSpec("sfx-attack-spirit-gourd.wav", "spirit", 0.50, 170.0, 540.0, 0.10, 0.20, 0.65, 67),
    EffectSpec("sfx-attack-ten-thousand-seal-array.wav", "paper", 0.72, 520.0, 1_380.0, 0.14, -0.30, 0.70, 79),
    EffectSpec("sfx-attack-heavenly-thunder-seal.wav", "lightning", 0.68, 1_760.0, 260.0, 0.30, 0.25, 0.75, 83),
    EffectSpec("sfx-attack-inferno-returning-wheel.wav", "fire", 0.78, 260.0, 980.0, 0.20, 0.35, 0.50, 97),
    EffectSpec("sfx-attack-blue-flame-spirit-gourd.wav", "spirit", 0.76, 190.0, 720.0, 0.12, -0.20, 0.80, 101),
    EffectSpec("sfx-attack-lunar-eclipse-twin-blades.wav", "sword", 0.64, 1_280.0, 240.0, 0.18, -0.35, 0.60, 113),
    EffectSpec("sfx-attack-thunder-flame-divine-orb.wav", "lightning", 0.82, 420.0, 1_620.0, 0.34, 0.10, 0.90, 127),
)

BGM_SPECS = {
    "bgm-lobby.wav": 36,
    "bgm-combat.wav": 48,
}

PENTATONIC_RATIOS = (1.0, 9.0 / 8.0, 6.0 / 5.0, 3.0 / 2.0, 5.0 / 3.0)

# Each integer is a pentatonic degree. Values 5 and above move into the next
# octave, while -1 deliberately leaves breathing room between phrases.
LOBBY_MELODY = (
    0, -1, 2, 3, -1, 2, 1, -1, 0, 1, 2, -1, 4, 3, 2, -1,
    1, -1, 3, 4, 5, -1, 4, 2, -1, 3, 2, 1, 0, -1, 1, -1,
    2, 3, 4, -1, 7, 5, 4, -1, 3, 2, 1, -1, 2, 1, 0, -1,
)
COMBAT_MELODY = (
    0, 2, 3, 5, 4, 3, 2, -1, 1, 3, 4, 7, 5, 4, 3, 2,
    2, 4, 5, 7, 9, 7, 5, 4, 3, 5, 7, 6, 5, 3, 2, -1,
    0, 3, 4, 5, 3, 2, 1, 3, 4, 7, 9, 7, 5, 4, 2, -1,
    1, 2, 4, 6, 5, 4, 3, 1, 2, 5, 7, 5, 4, 3, 2, 0,
    3, 4, 7, 9, 8, 7, 5, 4, 2, 3, 5, 7, 6, 5, 3, -1,
    0, 2, 3, 4, 7, 5, 4, 2, 1, 3, 4, 5, 3, 2, 1, -1,
)
LOBBY_BASS = (0, -1, 3, -1, 1, -1, 4, -1, 0, -1, 2, -1)
COMBAT_BASS = (0, 0, 3, 3, 1, 1, 4, 3, 0, 2, 3, 4)


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


def pentatonic_frequency(root: float, degree: int) -> float:
    octave, scale_degree = divmod(degree, len(PENTATONIC_RATIOS))
    return root * (2.0**octave) * PENTATONIC_RATIOS[scale_degree]


def raw_bgm_frames(duration: int, combat: bool, overlap_frames: int):
    total_frames = duration * SAMPLE_RATE + overlap_frames
    beats_per_second = (120.0 if combat else 80.0) / 60.0
    root = 82.4069 if combat else 110.0
    root = round(root * duration) / duration
    melody = COMBAT_MELODY if combat else LOBBY_MELODY
    bass_pattern = COMBAT_BASS if combat else LOBBY_BASS
    noise = DeterministicNoise(2_026 if combat else 1_392)
    wind_left = 0.0
    wind_right = 0.0

    for frame in range(total_frames):
        time = (frame - overlap_frames) / SAMPLE_RATE
        progress = time / duration
        beat = time * beats_per_second
        beat_index = math.floor(beat)
        beat_phase = beat - beat_index
        note_time = beat_phase / beats_per_second
        raw_noise = noise.next()
        wind_left = 0.994 * wind_left + 0.006 * raw_noise
        wind_right = 0.992 * wind_right + 0.008 * raw_noise

        # A quiet bowed-string bed holds the tonal centre while the fifth moves
        # at a different rate, preventing the old single-tone-loop impression.
        drone = 0.075 * math.sin(2.0 * math.pi * root * time)
        drone += 0.040 * math.sin(2.0 * math.pi * root * 1.5 * time + 0.35)
        drone += 0.018 * math.sin(2.0 * math.pi * root * 2.0 * time + 0.10)

        bass_step = math.floor(beat / 2.0)
        bass_phase = (beat / 2.0) - bass_step
        bass_degree = bass_pattern[bass_step % len(bass_pattern)]
        bass = 0.0
        if bass_degree >= 0:
            bass_frequency = pentatonic_frequency(root, bass_degree)
            bass_time = bass_phase * 2.0 / beats_per_second
            bass_envelope = smoothstep(min(1.0, bass_phase * 18.0)) * math.exp(
                -2.8 * bass_phase
            )
            bass_envelope *= smoothstep(min(1.0, (1.0 - bass_phase) * 8.0))
            bass = 0.105 * bass_envelope * (
                math.sin(2.0 * math.pi * bass_frequency * bass_time)
                + 0.24 * math.sin(4.0 * math.pi * bass_frequency * bass_time + 0.2)
            )

        degree = melody[beat_index % len(melody)]
        melodic_left = 0.0
        melodic_right = 0.0
        if degree >= 0:
            melody_root = root * (4.0 if combat else 2.0)
            note_frequency = pentatonic_frequency(melody_root, degree)
            note_envelope = smoothstep(min(1.0, beat_phase * 14.0))
            note_envelope *= smoothstep(min(1.0, (1.0 - beat_phase) * 5.0))
            flute = math.sin(2.0 * math.pi * note_frequency * note_time)
            flute += 0.23 * math.sin(4.0 * math.pi * note_frequency * note_time + 0.18)
            flute += 0.07 * math.sin(6.0 * math.pi * note_frequency * note_time + 0.42)
            breath = raw_noise * (0.018 if combat else 0.012)
            melody_gain = 0.115 if combat else 0.095
            melodic_left = melody_gain * note_envelope * (flute + breath)
            melodic_right = melody_gain * note_envelope * (
                math.sin(2.0 * math.pi * note_frequency * note_time + 0.055)
                + 0.20 * math.sin(4.0 * math.pi * note_frequency * note_time + 0.27)
                - breath
            )

        # A short zither-like pluck answers the main phrase every second beat.
        answer_degree = melody[(beat_index + (5 if combat else 7)) % len(melody)]
        answer = 0.0
        if beat_index % 2 == 1 and answer_degree >= 0:
            answer_frequency = pentatonic_frequency(root * 2.0, answer_degree)
            answer_envelope = smoothstep(min(1.0, beat_phase * 30.0)) * math.exp(
                -6.5 * beat_phase
            )
            answer = 0.065 * answer_envelope * (
                math.sin(2.0 * math.pi * answer_frequency * note_time)
                + 0.42 * math.sin(4.0 * math.pi * answer_frequency * note_time)
            )

        rhythm_left = 0.0
        rhythm_right = 0.0
        if combat:
            subdivision = beat * 2.0
            step = math.floor(subdivision)
            step_phase = subdivision - step
            accents = (1.0, 0.0, 0.28, 0.62, 0.0, 0.42, 0.20, 0.72,
                       0.92, 0.0, 0.35, 0.56, 0.0, 0.52, 0.22, 0.78)
            accent = accents[step % len(accents)]
            drum_envelope = accent * smoothstep(min(1.0, step_phase * 80.0))
            drum_envelope *= math.exp(-15.0 * step_phase)
            drum_time = step_phase / (beats_per_second * 2.0)
            drum_frequency = 92.0 - 38.0 * smoothstep(step_phase)
            drum = drum_envelope * math.sin(2.0 * math.pi * drum_frequency * drum_time)
            rim_attack = smoothstep(min(1.0, step_phase * 80.0))
            rim = accent * rim_attack * raw_noise * math.exp(-32.0 * step_phase)
            rhythm_left = 0.17 * drum + 0.065 * rim
            rhythm_right = 0.17 * drum - 0.045 * rim
        else:
            subdivision = beat * 2.0
            step = math.floor(subdivision)
            step_phase = subdivision - step
            soft_accents = (0.62, 0.0, 0.0, 0.18, 0.0, 0.0, 0.38, 0.0,
                            0.46, 0.0, 0.0, 0.22, 0.0, 0.0, 0.30, 0.0)
            accent = soft_accents[step % len(soft_accents)]
            drum_envelope = accent * smoothstep(min(1.0, step_phase * 80.0))
            drum_envelope *= math.exp(-18.0 * step_phase)
            drum_time = step_phase / (beats_per_second * 2.0)
            soft_drum = drum_envelope * math.sin(
                2.0 * math.pi * (76.0 - 24.0 * step_phase) * drum_time
            )
            rhythm_left = 0.070 * soft_drum + 0.018 * raw_noise * drum_envelope
            rhythm_right = 0.070 * soft_drum - 0.012 * raw_noise * drum_envelope

        bell = 0.0
        if beat_index % 16 in ((7, 15) if combat else (14,)):
            bell_envelope = smoothstep(min(1.0, beat_phase * 24.0)) * math.exp(
                -4.0 * beat_phase
            )
            bell_envelope *= smoothstep(min(1.0, (1.0 - beat_phase) * 5.0))
            bell_frequency = root * (7.5 if combat else 6.0)
            bell = 0.035 * bell_envelope * (
                math.sin(2.0 * math.pi * bell_frequency * note_time)
                + 0.46 * math.sin(2.0 * math.pi * bell_frequency * 2.73 * note_time)
            )

        air = 0.020 if combat else 0.030
        slow_motion = 0.012 * math.sin(2.0 * math.pi * 3.0 * progress)
        left = (
            drone + bass + melodic_left + answer + rhythm_left + bell
            + air * wind_left + slow_motion
        )
        right = (
            drone + bass + melodic_right - 0.6 * answer + rhythm_right + bell
            + air * wind_right - slow_motion
        )
        yield encode_sample(left), encode_sample(right)


def bgm_frames(duration: int, combat: bool):
    total_frames = duration * SAMPLE_RATE
    overlap_frames = round(BGM_CROSSFADE_SECONDS * SAMPLE_RATE)
    source = iter(raw_bgm_frames(duration, combat, overlap_frames))
    previous_cycle_tail = [next(source) for _ in range(overlap_frames)]

    # Start at musical time zero. At the end, overlap the continued final
    # phrase with the same phrase from the previous cycle. The raised-cosine
    # blend preserves level and makes the final sample lead directly into the
    # first sample instead of fading both sides to silence.
    for _ in range(total_frames - overlap_frames):
        yield next(source)
    for index, earlier in enumerate(previous_cycle_tail):
        continued = next(source)
        position = index / (overlap_frames - 1)
        head_weight = 0.5 - 0.5 * math.cos(math.pi * position)
        tail_weight = 1.0 - head_weight
        yield (
            round(continued[0] * tail_weight + earlier[0] * head_weight),
            round(continued[1] * tail_weight + earlier[1] * head_weight),
        )


def effect_frames(spec: EffectSpec):
    noise = DeterministicNoise(spec.seed)
    phase = 0.0
    second_phase = 0.0
    frames = spec.frames
    previous_noise = 0.0
    low_noise = 0.0

    for frame in range(frames):
        progress = frame / max(1, frames - 1)
        time = frame / SAMPLE_RATE
        curved = smoothstep(progress)
        frequency = spec.start_frequency + (spec.end_frequency - spec.start_frequency) * curved
        frequency *= 1.0 + 0.045 * math.sin(2.0 * math.pi * progress * 3.0)
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        second_phase += 2.0 * math.pi * (frequency * 0.505) / SAMPLE_RATE

        raw_noise = noise.next()
        high_noise = raw_noise - previous_noise
        previous_noise = raw_noise
        low_noise = 0.92 * low_noise + 0.08 * raw_noise

        attack = smoothstep(min(1.0, frame / (SAMPLE_RATE * 0.006)))
        tail = smoothstep(1.0 - progress)
        decay = math.exp(-2.0 * progress) * tail
        envelope = attack * decay
        body = math.sin(phase)
        body += spec.harmonic * 0.42 * math.sin(phase * 2.0 + 0.25)
        body += spec.harmonic * 0.22 * math.sin(second_phase + 0.7)

        if spec.style == "paper":
            # Several very short high-passed impulses create a dry paper/card
            # flick without embedding or transforming any recorded sample.
            centres = (0.015, 0.055, 0.12, 0.24, 0.38)
            count = 2 if spec.duration < 0.5 else len(centres)
            impulse = sum(
                math.exp(-((progress - centre) / 0.022) ** 2)
                * (1.0 - index * 0.13)
                for index, centre in enumerate(centres[:count])
            )
            paper_noise = (0.72 * high_noise + 0.18 * raw_noise) * impulse
            paper_tone = math.sin(phase * 1.7) * math.exp(-15.0 * progress)
            mono = tail * (0.34 * paper_noise + 0.15 * paper_tone)
        elif spec.style == "sword":
            # A broad air rush carries the fast swing, followed by a quiet,
            # inharmonic metal ring. Longer evolved blades receive two swipes.
            swipe = math.sin(math.pi * min(1.0, progress * 1.35)) ** 0.65
            if spec.duration >= 0.5:
                second_swipe = math.sin(math.pi * max(0.0, min(1.0, (progress - 0.28) / 0.55)))
                swipe += 0.65 * max(0.0, second_swipe) ** 0.65
            air_rush = (0.64 * high_noise + 0.22 * low_noise) * swipe * tail
            metal_envelope = smoothstep(min(1.0, progress * 18.0)) * math.exp(
                -5.5 * progress
            ) * tail
            metal = math.sin(phase * 2.37) + 0.44 * math.sin(phase * 3.91 + 0.6)
            mono = 0.31 * air_rush + 0.17 * metal_envelope * metal
        elif spec.style == "lightning":
            # The energy is front-loaded at the target position: a sharp
            # branching crack, a low impact and an electric/bell decay.
            crack = math.exp(-95.0 * progress)
            crack += 0.72 * math.exp(-120.0 * max(0.0, progress - 0.035)) * (
                1.0 if progress >= 0.035 else 0.0
            )
            if spec.duration > 0.65:
                crack += 0.46 * math.exp(-90.0 * max(0.0, progress - 0.22)) * (
                    1.0 if progress >= 0.22 else 0.0
                )
            electric = (0.72 * high_noise + 0.28 * raw_noise) * crack
            impact_frequency = 105.0 - 42.0 * curved
            impact = math.sin(2.0 * math.pi * impact_frequency * time) * math.exp(
                -11.0 * progress
            )
            ring = (
                math.sin(phase * 1.91) + 0.38 * math.sin(phase * 3.17 + 0.4)
            ) * math.exp(-5.0 * progress)
            mono = tail * (0.29 * electric + 0.22 * impact + 0.14 * ring)
        elif spec.style == "fire":
            flame_envelope = smoothstep(min(1.0, progress * 10.0)) * tail
            flare = math.sin(math.pi * progress) ** 0.7
            mono = flame_envelope * (
                0.22 * body + 0.26 * spec.noise * high_noise * flare
                + 0.10 * low_noise
            )
        elif spec.style == "boomerang":
            spin = 0.55 + 0.45 * math.sin(2.0 * math.pi * 6.0 * progress)
            mono = envelope * (0.31 * body + 0.17 * high_noise * spin)
        elif spec.style == "spirit":
            spectral_swell = math.sin(math.pi * progress) ** 0.8 * tail
            mono = spectral_swell * (
                0.25 * body + 0.18 * low_noise
                + 0.08 * math.sin(second_phase * 0.63)
            )
        else:
            raise ValueError(f"unsupported effect style: {spec.style}")

        pan_motion = 0.18 * math.sin(2.0 * math.pi * progress)
        pan = max(-1.0, min(1.0, spec.pan + pan_motion))
        left_gain = 1.0 - max(0.0, pan) * 0.45
        right_gain = 1.0 + min(0.0, pan) * 0.45
        stereo_offset = 0.018 * envelope * math.sin(phase + math.pi / 3.0)
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
        maximum_discontinuity = 512
        for channel in range(CHANNELS):
            first = samples[channel]
            last = samples[-CHANNELS + channel]
            if abs(first - last) > maximum_discontinuity:
                raise AssertionError(
                    f"{path.name}: loop discontinuity {abs(first - last)} exceeds "
                    f"{maximum_discontinuity} on channel {channel}"
                )

        window_samples = SAMPLE_RATE // 20 * CHANNELS
        minimum_boundary_rms = max(350.0, rms * 0.40)
        start_rms = math.sqrt(
            sum(sample * sample for sample in samples[:window_samples]) / window_samples
        )
        end_rms = math.sqrt(
            sum(sample * sample for sample in samples[-window_samples:]) / window_samples
        )
        if min(start_rms, end_rms) < minimum_boundary_rms:
            raise AssertionError(
                f"{path.name}: loop boundary energy is too low "
                f"(start RMS {start_rms:.1f}, end RMS {end_rms:.1f}, "
                f"minimum {minimum_boundary_rms:.1f})"
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
