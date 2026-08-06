package kr.joseonnight.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import kr.joseonnight.application.playrecord.provided.MemberBestValue;
import kr.joseonnight.application.playrecord.provided.PlayRecordRankingSource;
import kr.joseonnight.application.ranking.provided.RankingEntry;
import kr.joseonnight.application.ranking.provided.RankingFinder;
import kr.joseonnight.application.ranking.required.LeaderboardStore;
import kr.joseonnight.domain.ranking.RankingMetric;
import org.junit.jupiter.api.Test;

class RankingServiceTest {

    @Test
    void rankingFinderReturnsTheRedisProjectionWithoutReadingPostgresqlOnCacheHit() {
        PlayRecordRankingSource source = mock(PlayRecordRankingSource.class);
        LeaderboardStore leaderboard = mock(LeaderboardStore.class);
        List<RankingEntry> cached = List.of(
                new RankingEntry(RankingMetric.KILLS, 1L, 7L, 25L)
        );
        when(leaderboard.top(RankingMetric.KILLS, 10)).thenReturn(cached);
        RankingFinder rankingFinder = new RankingService(source, leaderboard);

        assertThat(rankingFinder.top(RankingMetric.KILLS, 10)).isSameAs(cached);

        verifyNoInteractions(source);
        verify(leaderboard, never()).rebuild(RankingMetric.KILLS, java.util.Map.of());
    }

    @Test
    void rankingFinderRebuildsAndDenseRanksAuthoritativeValuesOnCacheMiss() {
        PlayRecordRankingSource source = mock(PlayRecordRankingSource.class);
        LeaderboardStore leaderboard = mock(LeaderboardStore.class);
        when(leaderboard.top(RankingMetric.SURVIVAL, 10)).thenReturn(List.of());
        when(source.bestSurvivalByMember()).thenReturn(List.of(
                new MemberBestValue(3L, 90L),
                new MemberBestValue(2L, 100L),
                new MemberBestValue(1L, 80L),
                new MemberBestValue(1L, 100L)
        ));
        RankingFinder rankingFinder = new RankingService(source, leaderboard);

        List<RankingEntry> result = rankingFinder.top(RankingMetric.SURVIVAL, 10);

        assertThat(result).extracting(RankingEntry::memberId).containsExactly(1L, 2L, 3L);
        assertThat(result).extracting(RankingEntry::value).containsExactly(100L, 100L, 90L);
        assertThat(result).extracting(RankingEntry::rank).containsExactly(1L, 1L, 2L);
        LinkedHashMap<Long, Long> rebuilt = new LinkedHashMap<>();
        rebuilt.put(1L, 100L);
        rebuilt.put(2L, 100L);
        rebuilt.put(3L, 90L);
        verify(leaderboard).rebuild(RankingMetric.SURVIVAL, rebuilt);
    }
}
