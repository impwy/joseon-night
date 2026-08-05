package kr.joseonnight.domain.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import kr.joseonnight.domain.shared.AbstractEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Entity
@Table(name = "members")
public class Member extends AbstractEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected Member() {
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private Member(Instant registeredAt) {
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        lastLoginAt = registeredAt;
        role = MemberRole.PLAYER;
        status = MemberStatus.ACTIVE;
    }

    public static Member register(Instant registeredAt) {
        return new Member(registeredAt);
    }

    public void recordLogin(Instant loggedInAt) {
        if (status != MemberStatus.ACTIVE) {
            throw new IllegalStateException("Disabled members cannot log in");
        }
        lastLoginAt = Objects.requireNonNull(loggedInAt, "loggedInAt");
    }

    public void disable() {
        status = MemberStatus.DISABLED;
    }

    public MemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

}
