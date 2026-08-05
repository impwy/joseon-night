package kr.joseonnight.domain.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import kr.joseonnight.domain.shared.AbstractEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.util.Assert;

@Entity
@Table(
        name = "oauth_identities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_oauth_identities_provider_subject",
                        columnNames = {"provider", "subject_hmac"}
                ),
                @UniqueConstraint(
                        name = "uk_oauth_identities_member_provider",
                        columnNames = {"member_id", "provider"}
                )
        }
)
public class OAuthIdentity extends AbstractEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "subject_hmac", nullable = false, length = 64)
    private String subjectHmac;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected OAuthIdentity() {
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private OAuthIdentity(
            Long memberId,
            OAuthProvider provider,
            String subjectHmac,
            Instant connectedAt
    ) {
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.subjectHmac = validateSubjectHmac(subjectHmac);
        this.connectedAt = Objects.requireNonNull(connectedAt, "connectedAt");
        lastLoginAt = connectedAt;
    }

    static OAuthIdentity connectGoogle(Long memberId, String subjectHmac, Instant connectedAt) {
        return new OAuthIdentity(memberId, OAuthProvider.GOOGLE, subjectHmac, connectedAt);
    }

    void recordLogin(Instant loggedInAt) {
        Instant validatedLoggedInAt = Objects.requireNonNull(loggedInAt, "loggedInAt");
        Assert.isTrue(
                !validatedLoggedInAt.isBefore(lastLoginAt),
                "Login time cannot precede the previous login"
        );
        lastLoginAt = validatedLoggedInAt;
    }

    public Long getMemberId() { return memberId; }
    public OAuthProvider getProvider() { return provider; }
    public String getSubjectHmac() { return subjectHmac; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }

    private static String validateSubjectHmac(String value) {
        Objects.requireNonNull(value, "subjectHmac");
        Assert.isTrue(
                value.matches("[0-9a-f]{64}"),
                "subjectHmac must be a lowercase SHA-256 HMAC"
        );
        return value;
    }
}
