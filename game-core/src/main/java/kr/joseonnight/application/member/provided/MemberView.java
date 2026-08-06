package kr.joseonnight.application.member.provided;

import java.time.Instant;
import java.util.Objects;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.domain.member.MemberRole;
import kr.joseonnight.domain.member.MemberStatus;

public record MemberView(
        Long id,
        MemberRole role,
        MemberStatus status,
        Instant registeredAt,
        Instant lastLoginAt
) {

    public MemberView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt");
    }

    public static MemberView from(Member member) {
        return new MemberView(
                member.getId(),
                member.getRole(),
                member.getStatus(),
                member.getRegisteredAt(),
                member.getLastLoginAt()
        );
    }
}
