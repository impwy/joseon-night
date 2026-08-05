package kr.joseonnight.desktop.audio;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import kr.joseonnight.desktop.gameplay.SoundCue;

/** Prevents the same cue from stacking during a short burst while keeping different cues independent. */
final class SoundCueRateLimiter {
    static final Duration DEFAULT_INTERVAL = Duration.ofMillis(80L);

    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private final Map<SoundCue, Long> lastPlayedNanos = new EnumMap<>(SoundCue.class);

    SoundCueRateLimiter() {
        this(DEFAULT_INTERVAL, System::nanoTime);
    }

    SoundCueRateLimiter(Duration interval, LongSupplier nanoTime) {
        Objects.requireNonNull(interval, "interval");
        if (interval.isNegative()) {
            throw new IllegalArgumentException("interval must not be negative");
        }
        intervalNanos = interval.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    synchronized boolean tryAcquire(SoundCue cue) {
        Objects.requireNonNull(cue, "cue");
        long now = nanoTime.getAsLong();
        Long lastPlayed = lastPlayedNanos.get(cue);
        if (lastPlayed != null && now - lastPlayed < intervalNanos) {
            return false;
        }
        lastPlayedNanos.put(cue, now);
        return true;
    }

    synchronized void reset() {
        lastPlayedNanos.clear();
    }
}
