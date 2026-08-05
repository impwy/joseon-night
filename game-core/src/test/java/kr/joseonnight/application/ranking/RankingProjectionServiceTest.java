package kr.joseonnight.application.ranking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import kr.joseonnight.application.ranking.provided.RankingUpdate;
import kr.joseonnight.application.ranking.provided.RankingUpdater;
import kr.joseonnight.application.ranking.required.LeaderboardStore;
import kr.joseonnight.application.ranking.required.LeaderboardUnavailableException;
import kr.joseonnight.domain.ranking.RankingMetric;
import org.junit.jupiter.api.Test;

class RankingProjectionServiceTest {

    @Test
    void retryingAfterTheSecondMetricFailsSafelyReappliesBothMaximumUpdates() {
        LeaderboardStore leaderboard = mock(LeaderboardStore.class);
        LeaderboardUnavailableException unavailable = new LeaderboardUnavailableException(
                "Redis unavailable",
                new IllegalStateException("offline")
        );
        doThrow(unavailable)
                .doNothing()
                .when(leaderboard)
                .recordBest(RankingMetric.KILLS, 7L, 25L);
        RankingUpdater rankingUpdater = new RankingProjectionService(leaderboard);
        RankingUpdate update = new RankingUpdate("event-1", 7L, 300_000L, 25, true);

        assertThatThrownBy(() -> rankingUpdater.update(update)).isSameAs(unavailable);
        assertThatCode(() -> rankingUpdater.update(update)).doesNotThrowAnyException();

        verify(leaderboard, times(2)).recordBest(RankingMetric.SURVIVAL, 7L, 300_000L);
        verify(leaderboard, times(2)).recordBest(RankingMetric.KILLS, 7L, 25L);
    }
}
