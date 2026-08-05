package kr.joseonnight.application.ranking.provided;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record RankingUpdate(
        @NotBlank String eventId,
        @Positive Long memberId,
        @PositiveOrZero long survivalMillis,
        @PositiveOrZero int killCount,
        boolean rankingEligible
) {
}
