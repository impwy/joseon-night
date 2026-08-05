package kr.joseonnight.domain.gameplay;

/**
 * One-shot sound cue whose stable id lets clients ignore a replayed snapshot.
 */
public record SoundEvent(long id, String type) {

    public SoundEvent {
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
    }
}
