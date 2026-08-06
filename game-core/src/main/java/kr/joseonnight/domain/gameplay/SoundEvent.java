package kr.joseonnight.domain.gameplay;

import java.util.Objects;
import org.springframework.util.Assert;

/**
 * One-shot sound cue whose stable id lets clients ignore a replayed snapshot.
 */
public record SoundEvent(long id, SoundCue type) {

    public SoundEvent {
        Assert.isTrue(id > 0L, "id must be positive");
        Objects.requireNonNull(type, "type");
    }
}
