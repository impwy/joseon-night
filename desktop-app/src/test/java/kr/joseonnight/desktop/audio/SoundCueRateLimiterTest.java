package kr.joseonnight.desktop.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import kr.joseonnight.desktop.gameplay.SoundCue;
import org.junit.jupiter.api.Test;

class SoundCueRateLimiterTest {

    @Test
    void throttlesOnlyTheSameCueUntilEightyMillisecondsHaveElapsed() {
        AtomicLong clock = new AtomicLong();
        SoundCueRateLimiter limiter = new SoundCueRateLimiter(Duration.ofMillis(80L), clock::get);

        assertThat(limiter.tryAcquire(SoundCue.SEAL_TALISMAN_ATTACK)).isTrue();
        assertThat(limiter.tryAcquire(SoundCue.FLAME_FAN_ATTACK)).isTrue();

        clock.set(Duration.ofMillis(79L).toNanos());
        assertThat(limiter.tryAcquire(SoundCue.SEAL_TALISMAN_ATTACK)).isFalse();

        clock.set(Duration.ofMillis(80L).toNanos());
        assertThat(limiter.tryAcquire(SoundCue.SEAL_TALISMAN_ATTACK)).isTrue();
    }

    @Test
    void resetAllowsTheCueImmediately() {
        AtomicLong clock = new AtomicLong();
        SoundCueRateLimiter limiter = new SoundCueRateLimiter(Duration.ofMillis(80L), clock::get);
        limiter.tryAcquire(SoundCue.GUARD);

        limiter.reset();

        assertThat(limiter.tryAcquire(SoundCue.GUARD)).isTrue();
    }
}
