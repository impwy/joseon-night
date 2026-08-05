package kr.joseonnight.adapter.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import kr.joseonnight.adapter.persistence.redis.leaderboard.RedisLeaderboardStore;
import kr.joseonnight.application.ranking.required.LeaderboardUnavailableException;
import kr.joseonnight.domain.ranking.RankingMetric;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisLeaderboardStoreTest {

    @Test
    void redisProjectionUsesDenseRanksForEqualValues() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> values = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(values);
        var first = tuple("1", 100.0);
        var second = tuple("2", 100.0);
        var third = tuple("3", 90.0);
        when(values.reverseRangeWithScores("ranking:survival:all:v1", 0, 9))
                .thenReturn(new LinkedHashSet<>(java.util.List.of(first, second, third)));
        RedisLeaderboardStore store = new RedisLeaderboardStore(redisTemplate);

        var ranking = store.top(RankingMetric.SURVIVAL, 10);

        assertThat(ranking).extracting(entry -> entry.rank()).containsExactly(1L, 1L, 2L);
        assertThat(ranking).extracting(entry -> entry.value()).containsExactly(100L, 100L, 90L);
    }

    @Test
    void writeFailureIsPropagatedSoKafkaCanRetryBeforeRecordingAReceipt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new DataAccessResourceFailureException("offline"))
                .when(redisTemplate)
                .execute(
                        org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                        anyList(),
                        any(Object[].class)
                );
        RedisLeaderboardStore store = new RedisLeaderboardStore(redisTemplate);

        assertThatThrownBy(() -> store.recordBest(RankingMetric.KILLS, 7L, 25L))
                .isInstanceOf(LeaderboardUnavailableException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void rebuildAtomicallyReplacesTheWholeProjectionInOneLuaScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisLeaderboardStore store = new RedisLeaderboardStore(redisTemplate);
        LinkedHashMap<Long, Long> bestByMember = new LinkedHashMap<>();
        bestByMember.put(7L, 300_000L);
        bestByMember.put(8L, 250_000L);

        store.rebuild(RankingMetric.SURVIVAL, bestByMember);

        verify(redisTemplate).execute(
                argThat(script -> script.getScriptAsString().contains("redis.call('DEL', KEYS[1])")
                        && script.getScriptAsString().contains("redis.call('ZADD', KEYS[1]")),
                eq(List.of("ranking:survival:all:v1")),
                any(Object[].class)
        );
    }

    private static ZSetOperations.TypedTuple<String> tuple(String memberId, double score) {
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(memberId);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }
}
