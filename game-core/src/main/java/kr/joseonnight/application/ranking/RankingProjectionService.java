package kr.joseonnight.application.ranking;

import kr.joseonnight.application.ranking.provided.RankingUpdate;
import kr.joseonnight.application.ranking.provided.RankingUpdater;
import kr.joseonnight.application.ranking.required.LeaderboardStore;
import kr.joseonnight.domain.ranking.RankingMetric;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import lombok.RequiredArgsConstructor;

@ValidatedApplicationService
@RequiredArgsConstructor
public final class RankingProjectionService implements RankingUpdater {

    private final LeaderboardStore leaderboardStore;

    @Override
    public void update(RankingUpdate update) {
        if (!update.rankingEligible()) {
            return;
        }
        leaderboardStore.recordBest(
                RankingMetric.SURVIVAL,
                update.memberId(),
                update.survivalMillis()
        );
        leaderboardStore.recordBest(RankingMetric.KILLS, update.memberId(), update.killCount());
    }
}
