package kr.joseonnight.desktop.view;

import static org.assertj.core.api.Assertions.assertThat;

import kr.joseonnight.desktop.settings.TargetFps;
import org.junit.jupiter.api.Test;

class FrameRateLimiterTest {
    @Test
    void autoRendersEveryJavaFxPulse() {
        FrameRateLimiter limiter = new FrameRateLimiter(TargetFps.AUTO);

        assertThat(limiter.shouldRender(1_000L)).isTrue();
        assertThat(limiter.shouldRender(1_001L)).isTrue();
    }

    @Test
    void limitsRenderingToThirtyFramesPerSecondUsingVirtualTime() {
        FrameRateLimiter limiter = new FrameRateLimiter(TargetFps.FPS_30);

        assertThat(limiter.shouldRender(1_000_000_000L)).isTrue();
        assertThat(limiter.shouldRender(1_033_333_332L)).isFalse();
        assertThat(limiter.shouldRender(1_033_333_333L)).isTrue();
    }

    @Test
    void limitsRenderingToSixtyFramesPerSecondUsingVirtualTime() {
        FrameRateLimiter limiter = new FrameRateLimiter(TargetFps.FPS_60);

        assertThat(limiter.shouldRender(2_000_000_000L)).isTrue();
        assertThat(limiter.shouldRender(2_016_666_665L)).isFalse();
        assertThat(limiter.shouldRender(2_016_666_666L)).isTrue();
    }

    @Test
    void changingTargetAllowsAnImmediateRender() {
        FrameRateLimiter limiter = new FrameRateLimiter(TargetFps.FPS_60);
        assertThat(limiter.shouldRender(1_000_000_000L)).isTrue();
        assertThat(limiter.shouldRender(1_001_000_000L)).isFalse();

        limiter.setTargetFps(TargetFps.FPS_30);

        assertThat(limiter.shouldRender(1_001_000_000L)).isTrue();
    }
}
