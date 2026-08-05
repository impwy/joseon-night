package kr.joseonnight.desktop.settings;

/** Persisted sound preferences. Master volume is expressed as a whole-number percentage. */
public record AudioSettings(boolean muted, int masterVolume) {
    public AudioSettings {
        if (masterVolume < 0 || masterVolume > 100) {
            throw new IllegalArgumentException("masterVolume must be between 0 and 100");
        }
    }

    public static AudioSettings defaults() {
        return new AudioSettings(false, 70);
    }

    public double effectiveVolume() {
        return muted ? 0.0 : masterVolume / 100.0;
    }
}
