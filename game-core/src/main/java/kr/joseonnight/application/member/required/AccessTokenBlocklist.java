package kr.joseonnight.application.member.required;

import java.time.Instant;

public interface AccessTokenBlocklist {

    void block(String jwtId, Instant expiresAt);

    boolean isBlocked(String jwtId);
}
