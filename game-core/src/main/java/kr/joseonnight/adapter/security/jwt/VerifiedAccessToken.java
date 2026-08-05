package kr.joseonnight.adapter.security.jwt;

import java.time.Instant;

public record VerifiedAccessToken(
        MemberPrincipal principal,
        String jwtId,
        Instant expiresAt
) {
}
