package kr.joseonnight.application.playrecord.provided;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.joseonnight.domain.playrecord.PlayOutcome;
import kr.joseonnight.domain.playrecord.PlayRecord;

public record PlayRecordView(
        Long id,
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

    public PlayRecordView {
        finalBuild = Map.copyOf(finalBuild);
    }

    @Override
    public Map<String, Object> finalBuild() {
        return Map.copyOf(finalBuild);
    }

    public static PlayRecordView from(PlayRecord record) {
        return new PlayRecordView(
                record.getId(),
                record.getGameSession(),
                record.getMemberId(),
                record.getCharacterId(),
                record.getScore(),
                record.getKillCount(),
                record.getLevel(),
                record.getOutcome(),
                record.getDurationMillis(),
                record.getFinalBuild(),
                record.getEndedAt(),
                record.isRankingEligible()
        );
    }
}
