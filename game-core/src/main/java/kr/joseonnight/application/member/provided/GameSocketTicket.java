package kr.joseonnight.application.member.provided;

import java.time.Instant;

public record GameSocketTicket(String value, Instant expiresAt) {
}
