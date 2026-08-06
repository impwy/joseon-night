package kr.joseonnight.application.member.provided;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record DesktopLoginStart(
        UUID attemptId,
        String pollToken,
        URI authorizationUri,
        Instant expiresAt
) {
}
