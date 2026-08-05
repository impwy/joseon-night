package kr.joseonnight.application.gameplay.provided;

import java.util.Objects;

/**
 * Identifies one authenticated connection to an authoritative game session.
 */
public record GameSessionHandle(
        String sessionId,
        String connectionId,
        GameSnapshot snapshot
) {

    public GameSessionHandle {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("connectionId must not be blank");
        }
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
