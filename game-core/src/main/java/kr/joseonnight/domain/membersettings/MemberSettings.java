package kr.joseonnight.domain.membersettings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import kr.joseonnight.domain.shared.AbstractEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Entity
@Table(name = "member_settings")
public class MemberSettings extends AbstractEntity {

    private static final int DEFAULT_VOLUME = 70;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(name = "master_volume", nullable = false)
    private int masterVolume;

    @Column(nullable = false)
    private boolean muted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemberSettings() {
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private MemberSettings(Long memberId, String nickname, Instant now) {
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.nickname = validateNickname(nickname);
        masterVolume = DEFAULT_VOLUME;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public static MemberSettings create(Long memberId, String nickname, Instant now) {
        return new MemberSettings(memberId, nickname, now);
    }

    public void updateAudio(int masterVolume, boolean muted, Instant now) {
        this.masterVolume = validateVolume(masterVolume);
        this.muted = muted;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getNickname() {
        return nickname;
    }

    public int getMasterVolume() {
        return masterVolume;
    }

    public boolean isMuted() {
        return muted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String validateNickname(String value) {
        Objects.requireNonNull(value, "nickname");
        String stripped = value.strip();
        if (!stripped.matches("[\\p{L}\\p{N}_-]{2,20}")) {
            throw new IllegalArgumentException("nickname must contain 2 to 20 letters or numbers");
        }
        return stripped;
    }

    private static int validateVolume(int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("volume must be between 0 and 100");
        }
        return value;
    }
}
