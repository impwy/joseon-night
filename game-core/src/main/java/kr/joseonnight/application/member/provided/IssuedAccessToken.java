package kr.joseonnight.application.member.provided;

import java.time.Instant;

public record IssuedAccessToken(String value, String tokenType, Instant expiresAt) {
}
