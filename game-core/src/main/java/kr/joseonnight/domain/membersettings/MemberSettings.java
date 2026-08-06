package kr.joseonnight.domain.membersettings;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import kr.joseonnight.domain.shared.AbstractEntity;
import lombok.NoArgsConstructor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.util.Assert;

@Entity
@Table(name = "member_settings")
@NoArgsConstructor(access = PROTECTED)
public class MemberSettings extends AbstractEntity {

    private static final int DEFAULT_MUSIC_VOLUME = 60;
    private static final int DEFAULT_EFFECTS_VOLUME = 70;
    private static final TargetFps DEFAULT_TARGET_FPS = TargetFps.FPS_60;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(nullable = false)
    private boolean muted;

    @Column(name = "music_volume", nullable = false)
    private int musicVolume;

    @Column(name = "effects_volume", nullable = false)
    private int effectsVolume;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_fps", nullable = false, length = 20)
    private TargetFps targetFps;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private MemberSettings(Long memberId, String nickname, Instant now) {
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.nickname = validateNickname(nickname);
        musicVolume = DEFAULT_MUSIC_VOLUME;
        effectsVolume = DEFAULT_EFFECTS_VOLUME;
        targetFps = DEFAULT_TARGET_FPS;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public static MemberSettings create(Long memberId, String nickname, Instant now) {
        return new MemberSettings(memberId, nickname, now);
    }

    public void updatePreferences(
            boolean muted,
            int musicVolume,
            int effectsVolume,
            TargetFps targetFps,
            Instant now
    ) {
        int validatedMusicVolume = validateVolume(musicVolume);
        int validatedEffectsVolume = validateVolume(effectsVolume);
        TargetFps validatedTargetFps = Objects.requireNonNull(targetFps, "targetFps");
        Instant validatedNow = Objects.requireNonNull(now, "now");
        this.muted = muted;
        this.musicVolume = validatedMusicVolume;
        this.effectsVolume = validatedEffectsVolume;
        this.targetFps = validatedTargetFps;
        updatedAt = validatedNow;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isMuted() {
        return muted;
    }

    public int getMusicVolume() {
        return musicVolume;
    }

    public int getEffectsVolume() {
        return effectsVolume;
    }

    public TargetFps getTargetFps() {
        return targetFps;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String validateNickname(String value) {
        Objects.requireNonNull(value, "nickname");
        String stripped = value.strip();
        Assert.isTrue(
                stripped.matches("[\\p{L}\\p{N}_-]{2,20}"),
                "nickname must contain 2 to 20 letters or numbers"
        );
        return stripped;
    }

    private static int validateVolume(int value) {
        Assert.isTrue(value >= 0 && value <= 100, "volume must be between 0 and 100");
        return value;
    }
}
