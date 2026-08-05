package kr.joseonnight.desktop.authentication;

import java.time.Instant;
import java.util.Objects;

/** The JWT is deliberately memory-only and is discarded when the desktop process exits. */
public record AuthSession(String accessToken, Instant expiresAt) {
    public AuthSession {
        if (Objects.requireNonNull(accessToken, "accessToken").isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
    }
}
