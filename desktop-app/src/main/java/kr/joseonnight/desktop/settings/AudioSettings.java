package kr.joseonnight.desktop.settings;

/** Persisted sound and rendering preferences. Volumes are whole-number percentages. */
public record AudioSettings(
        boolean muted,
        int musicVolume,
        int effectsVolume,
        TargetFps targetFps) {
    public AudioSettings {
        validateVolume("musicVolume", musicVolume);
        validateVolume("effectsVolume", effectsVolume);
        targetFps = java.util.Objects.requireNonNull(targetFps, "targetFps");
    }

    public static AudioSettings defaults() {
        return new AudioSettings(false, 60, 70, TargetFps.FPS_60);
    }

    public double effectiveMusicVolume() {
        return muted ? 0.0 : musicVolume / 100.0;
    }

    public double effectiveEffectsVolume() {
        return muted ? 0.0 : effectsVolume / 100.0;
    }

    private static void validateVolume(String name, int volume) {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }
}
