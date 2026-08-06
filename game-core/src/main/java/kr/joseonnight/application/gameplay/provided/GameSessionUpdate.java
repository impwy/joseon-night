package kr.joseonnight.application.gameplay.provided;

import java.util.Objects;

/**
 * A periodic state copy or the final state of a managed game session.
 */
public record GameSessionUpdate(
        String sessionId,
        GameSnapshot snapshot,
        boolean result
) {

    public GameSessionUpdate {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
