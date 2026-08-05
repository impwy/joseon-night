package kr.joseonnight.domain.gameplay;

import java.util.Objects;

/**
 * One-shot sound cue whose stable id lets clients ignore a replayed snapshot.
 */
public record SoundEvent(long id, SoundCue type) {

    public SoundEvent {
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be positive");
        }
        Objects.requireNonNull(type, "type");
    }
}
