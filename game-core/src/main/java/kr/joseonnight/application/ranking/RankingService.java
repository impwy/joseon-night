package kr.joseonnight.application.ranking;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.joseonnight.application.playrecord.provided.MemberBestValue;
import kr.joseonnight.application.playrecord.provided.PlayRecordRankingSource;
import kr.joseonnight.application.ranking.provided.RankingEntry;
import kr.joseonnight.application.ranking.provided.RankingFinder;
import kr.joseonnight.application.ranking.required.LeaderboardStore;
import kr.joseonnight.application.ranking.required.LeaderboardUnavailableException;
import kr.joseonnight.domain.ranking.RankingMetric;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;

@ValidatedApplicationService
public final class RankingService implements RankingFinder {

    private final PlayRecordRankingSource playRecordRankingSource;
    private final LeaderboardStore leaderboardStore;

    public RankingService(
            PlayRecordRankingSource playRecordRankingSource,
            LeaderboardStore leaderboardStore
    ) {
        this.playRecordRankingSource = playRecordRankingSource;
        this.leaderboardStore = leaderboardStore;
    }

    @Override
    public List<RankingEntry> top(RankingMetric metric, int limit) {
        try {
            List<RankingEntry> cached = leaderboardStore.top(metric, limit);
            if (!cached.isEmpty()) {
                return cached;
            }
        } catch (LeaderboardUnavailableException ignored) {
            // PostgreSQL is authoritative and remains available when Redis is not.
        }

        Map<Long, Long> bestByMember = authoritativeBest(metric);
        try {
            leaderboardStore.rebuild(metric, bestByMember);
        } catch (LeaderboardUnavailableException ignored) {
            // A best-effort Redis rebuild must not fail the authoritative read.
        }
        return denseRank(metric, bestByMember, limit);
    }

    private Map<Long, Long> authoritativeBest(RankingMetric metric) {
        List<MemberBestValue> values = switch (metric) {
            case SURVIVAL -> playRecordRankingSource.bestSurvivalByMember();
            case KILLS -> playRecordRankingSource.bestKillsByMember();
        };
        Map<Long, Long> bestByMember = new LinkedHashMap<>();
        values.stream()
                .sorted(Comparator.comparingLong(MemberBestValue::value)
                        .reversed()
                        .thenComparing(MemberBestValue::memberId))
                .forEach(value -> bestByMember.putIfAbsent(value.memberId(), value.value()));
        return bestByMember;
    }

    private static List<RankingEntry> denseRank(
            RankingMetric metric,
            Map<Long, Long> bestByMember,
            int limit
    ) {
        long[] rank = {0L};
        long[] previous = {Long.MIN_VALUE};
        return bestByMember.entrySet().stream().limit(limit).map(entry -> {
            if (rank[0] == 0L || entry.getValue() != previous[0]) {
                rank[0]++;
                previous[0] = entry.getValue();
            }
            return new RankingEntry(metric, rank[0], entry.getKey(), entry.getValue());
        }).toList();
    }
}
