package kr.joseonnight.application.ranking.provided;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import kr.joseonnight.domain.ranking.RankingMetric;

public interface RankingFinder {

    List<RankingEntry> top(
            @NotNull RankingMetric metric,
            @Min(1) @Max(100) int limit
    );
}
