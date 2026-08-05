package kr.joseonnight.domain.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.util.Assert;

@Entity
@Table(name = "member_characters")
@IdClass(MemberCharacterId.class)
public class MemberCharacter {

    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Id
    @Column(name = "character_id", nullable = false, length = 64)
    private String characterId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    protected MemberCharacter() {
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private MemberCharacter(Long memberId, String characterId, Instant unlockedAt) {
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.characterId = requireId(characterId);
        this.unlockedAt = Objects.requireNonNull(unlockedAt, "unlockedAt");
    }

    static MemberCharacter unlock(Long memberId, String characterId, Instant unlockedAt) {
        return new MemberCharacter(memberId, characterId, unlockedAt);
    }

    public Long getMemberId() { return memberId; }
    public String getCharacterId() { return characterId; }
    public Instant getUnlockedAt() { return unlockedAt; }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "characterId");
        Assert.isTrue(
                !value.isBlank() && value.length() <= 64,
                "characterId must contain between 1 and 64 characters"
        );
        return value;
    }
}
