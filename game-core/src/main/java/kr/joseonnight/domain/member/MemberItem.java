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
@Table(name = "member_items")
@IdClass(MemberItemId.class)
public class MemberItem {

    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Id
    @Column(name = "item_id", nullable = false, length = 64)
    private String itemId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    protected MemberItem() {
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private MemberItem(Long memberId, String itemId, Instant unlockedAt) {
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.itemId = requireId(itemId);
        this.unlockedAt = Objects.requireNonNull(unlockedAt, "unlockedAt");
    }

    static MemberItem unlock(Long memberId, String itemId, Instant unlockedAt) {
        return new MemberItem(memberId, itemId, unlockedAt);
    }

    public Long getMemberId() { return memberId; }
    public String getItemId() { return itemId; }
    public Instant getUnlockedAt() { return unlockedAt; }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "itemId");
        Assert.isTrue(
                !value.isBlank() && value.length() <= 64,
                "itemId must contain between 1 and 64 characters"
        );
        return value;
    }
}
