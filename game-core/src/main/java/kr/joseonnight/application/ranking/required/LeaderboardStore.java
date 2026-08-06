package kr.joseonnight.application.ranking.required;

import java.util.List;
import java.util.Map;
import kr.joseonnight.application.ranking.provided.RankingEntry;
import kr.joseonnight.domain.ranking.RankingMetric;

public interface LeaderboardStore {

    void recordBest(RankingMetric metric, Long memberId, long value);

    void rebuild(RankingMetric metric, Map<Long, Long> bestByMember);

    List<RankingEntry> top(RankingMetric metric, int limit);
}
