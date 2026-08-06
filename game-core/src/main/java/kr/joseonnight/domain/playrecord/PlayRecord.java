package kr.joseonnight.domain.playrecord;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.joseonnight.domain.shared.AbstractEntity;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.util.Assert;

@Entity
@Table(name = "play_records")
@NoArgsConstructor(access = PROTECTED)
public class PlayRecord extends AbstractEntity {

    @Column(name = "game_session", nullable = false, unique = true)
    private UUID gameSession;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "character_id", nullable = false, length = 64)
    private String characterId;

    @Column(nullable = false)
    private long score;

    @Column(name = "kill_count", nullable = false)
    private int killCount;

    @Column(nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayOutcome outcome;

    @Column(name = "duration_millis", nullable = false)
    private long durationMillis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_build", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> finalBuild;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "ranking_eligible", nullable = false)
    private boolean rankingEligible;

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private PlayRecord(
            UUID gameSession,
            Long memberId,
            String characterId,
            long score,
            int killCount,
            int level,
            PlayOutcome outcome,
            long durationMillis,
            Map<String, Object> finalBuild,
            Instant endedAt,
            boolean rankingEligible
    ) {
        this.gameSession = Objects.requireNonNull(gameSession, "gameSession");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.characterId = requireText(characterId, "characterId");
        Assert.isTrue(
                score >= 0 && killCount >= 0 && level >= 1 && durationMillis >= 0,
                "play record values are outside their valid range"
        );
        this.score = score;
        this.killCount = killCount;
        this.level = level;
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.durationMillis = durationMillis;
        this.finalBuild = Map.copyOf(Objects.requireNonNull(finalBuild, "finalBuild"));
        this.endedAt = Objects.requireNonNull(endedAt, "endedAt");
        Assert.isTrue(
                outcome != PlayOutcome.ABANDONED || !rankingEligible,
                "Abandoned games cannot be ranking eligible"
        );
        this.rankingEligible = rankingEligible;
    }

    public static PlayRecord record(
            UUID gameSession,
            Long memberId,
            String characterId,
            long score,
            int killCount,
            int level,
            PlayOutcome outcome,
            long durationMillis,
            Map<String, Object> finalBuild,
            Instant endedAt,
            boolean rankingEligible
    ) {
        return new PlayRecord(
                gameSession,
                memberId,
                characterId,
                score,
                killCount,
                level,
                outcome,
                durationMillis,
                finalBuild,
                endedAt,
                rankingEligible
        );
    }

    public UUID getGameSession() { return gameSession; }
    public Long getMemberId() { return memberId; }
    public String getCharacterId() { return characterId; }
    public long getScore() { return score; }
    public int getKillCount() { return killCount; }
    public int getLevel() { return level; }
    public PlayOutcome getOutcome() { return outcome; }
    public long getDurationMillis() { return durationMillis; }
    public Map<String, Object> getFinalBuild() { return Map.copyOf(finalBuild); }
    public Instant getEndedAt() { return endedAt; }
    public boolean isRankingEligible() { return rankingEligible; }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String stripped = value.strip();
        Assert.isTrue(
                !stripped.isEmpty() && stripped.length() <= 64,
                name + " must contain between 1 and 64 characters"
        );
        return stripped;
    }
}
