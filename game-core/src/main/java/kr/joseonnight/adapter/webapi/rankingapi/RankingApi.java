package kr.joseonnight.adapter.webapi.rankingapi;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import kr.joseonnight.application.ranking.provided.RankingEntry;
import kr.joseonnight.application.ranking.provided.RankingFinder;
import kr.joseonnight.domain.ranking.RankingMetric;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingApi {

    private final RankingFinder rankingFinder;

    @GetMapping("/{metric}")
    public List<RankingEntry> rankings(
            @org.springframework.web.bind.annotation.PathVariable String metric,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return rankingFinder.top(RankingMetric.fromPath(metric), limit);
    }
}
