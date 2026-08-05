package kr.joseonnight.application.ranking.provided;

import kr.joseonnight.domain.ranking.RankingMetric;

public record RankingEntry(RankingMetric metric, long rank, Long memberId, long value) {
}
