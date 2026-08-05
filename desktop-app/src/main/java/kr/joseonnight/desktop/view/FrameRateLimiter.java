package kr.joseonnight.desktop.view;

import java.util.Objects;
import kr.joseonnight.desktop.settings.TargetFps;

/** Decides whether the current JavaFX pulse should repaint the combat view. */
final class FrameRateLimiter {
    private TargetFps targetFps;
    private long lastRenderNanos = Long.MIN_VALUE;

    FrameRateLimiter(TargetFps targetFps) {
        this.targetFps = Objects.requireNonNull(targetFps, "targetFps");
    }

    void setTargetFps(TargetFps targetFps) {
        this.targetFps = Objects.requireNonNull(targetFps, "targetFps");
        reset();
    }

    void reset() {
        lastRenderNanos = Long.MIN_VALUE;
    }

    boolean shouldRender(long nowNanos) {
        if (targetFps.isUnlimited() || lastRenderNanos == Long.MIN_VALUE) {
            lastRenderNanos = nowNanos;
            return true;
        }
        if (nowNanos - lastRenderNanos < targetFps.frameIntervalNanos()) {
            return false;
        }
        lastRenderNanos = nowNanos;
        return true;
    }
}
