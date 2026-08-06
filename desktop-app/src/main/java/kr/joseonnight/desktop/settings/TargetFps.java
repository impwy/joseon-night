package kr.joseonnight.desktop.settings;

/** User-selected upper limit for JavaFX rendering. Server simulation remains fixed at 60 Hz. */
public enum TargetFps {
    AUTO("자동", 0),
    FPS_30("30 FPS", 30),
    FPS_60("60 FPS", 60);

    private final String displayName;
    private final int framesPerSecond;

    TargetFps(String displayName, int framesPerSecond) {
        this.displayName = displayName;
        this.framesPerSecond = framesPerSecond;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isUnlimited() {
        return framesPerSecond == 0;
    }

    public long frameIntervalNanos() {
        return isUnlimited() ? 0L : 1_000_000_000L / framesPerSecond;
    }
}
