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
import org.springframework.util.Assert;

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

    public OAuthIdentity connectGoogle(String subjectHmac, Instant connectedAt) {
        return OAuthIdentity.connectGoogle(requirePersistedId(), subjectHmac, connectedAt);
    }

    public MemberCharacter unlockCharacter(String characterId, Instant unlockedAt) {
        return MemberCharacter.unlock(requirePersistedId(), characterId, unlockedAt);
    }

    public MemberItem unlockItem(String itemId, Instant unlockedAt) {
        return MemberItem.unlock(requirePersistedId(), itemId, unlockedAt);
    }

    public void recordLogin(OAuthIdentity identity, Instant loggedInAt) {
        Objects.requireNonNull(identity, "identity");
        Instant validatedLoggedInAt = Objects.requireNonNull(loggedInAt, "loggedInAt");
        Assert.state(status == MemberStatus.ACTIVE, "Disabled members cannot log in");
        Assert.isTrue(
                Objects.equals(getId(), identity.getMemberId()),
                "OAuth identity must belong to this member"
        );
        Assert.isTrue(
                !validatedLoggedInAt.isBefore(lastLoginAt),
                "Login time cannot precede the previous login"
        );
        identity.recordLogin(validatedLoggedInAt);
        lastLoginAt = validatedLoggedInAt;
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

    private Long requirePersistedId() {
        Long memberId = getId();
        Assert.state(memberId != null, "Member must be persisted before creating internal components");
        return memberId;
    }

}
