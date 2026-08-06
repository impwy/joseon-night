package kr.joseonnight.application.playrecord.provided;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import kr.joseonnight.domain.playrecord.PlayOutcome;
import java.util.Map;
import java.util.UUID;

public record PlayRecordInfo(
        @NotNull UUID gameSession,
        @Positive Long memberId,
        @NotBlank @Size(max = 64) String characterId,
        @PositiveOrZero long score,
        @PositiveOrZero int killCount,
        @Min(1) int level,
        @NotNull PlayOutcome outcome,
        @PositiveOrZero long durationMillis,
        @NotNull Map<String, Object> finalBuild,
        boolean rankingEligible
) {
    public PlayRecordInfo {
        finalBuild = Map.copyOf(finalBuild);
    }

    @Override
    public Map<String, Object> finalBuild() {
        return Map.copyOf(finalBuild);
    }
}
