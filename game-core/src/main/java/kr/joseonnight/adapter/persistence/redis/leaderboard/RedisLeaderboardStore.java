package kr.joseonnight.adapter.persistence.redis.leaderboard;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.ranking.provided.RankingEntry;
import kr.joseonnight.application.ranking.required.LeaderboardStore;
import kr.joseonnight.application.ranking.required.LeaderboardUnavailableException;
import kr.joseonnight.domain.ranking.RankingMetric;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public final class RedisLeaderboardStore implements LeaderboardStore {

    private static final String SURVIVAL_KEY = "ranking:survival:all:v1";
    private static final String KILLS_KEY = "ranking:kills:all:v1";
    private static final DefaultRedisScript<Long> RECORD_BEST_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if (not current) or (tonumber(ARGV[2]) > tonumber(current)) then
                redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
                return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> REBUILD_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            for index = 1, #ARGV, 2 do
                redis.call('ZADD', KEYS[1], ARGV[index + 1], ARGV[index])
            end
            return #ARGV / 2
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed Redis client is an injected collaborator")
    public RedisLeaderboardStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void recordBest(RankingMetric metric, Long memberId, long value) {
        executeWrite(
                RECORD_BEST_SCRIPT,
                metric,
                memberId.toString(),
                Long.toString(value)
        );
    }

    @Override
    public void rebuild(RankingMetric metric, Map<Long, Long> bestByMember) {
        Object[] arguments = bestByMember.entrySet().stream()
                .flatMap(entry -> java.util.stream.Stream.of(
                        entry.getKey().toString(),
                        entry.getValue().toString()
                ))
                .toArray();
        executeWrite(REBUILD_SCRIPT, metric, arguments);
    }

    private void executeWrite(
            DefaultRedisScript<Long> script,
            RankingMetric metric,
            Object... arguments
    ) {
        try {
            redisTemplate.execute(
                    script,
                    List.of(key(metric)),
                    arguments
            );
        } catch (DataAccessException exception) {
            throw new LeaderboardUnavailableException(
                    "Could not update the Redis leaderboard projection",
                    exception
            );
        }
    }

    @Override
    public List<RankingEntry> top(RankingMetric metric, int limit) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(key(metric), 0, limit - 1L);
            if (tuples != null && !tuples.isEmpty()) {
                return denseRank(metric, tuples.stream()
                        .map(RedisLeaderboardStore::tupleEntry)
                        .toList());
            }
        } catch (DataAccessException | IllegalArgumentException ignored) {
            // An empty result tells the application service to query authoritative PostgreSQL.
        }
        return List.of();
    }

    private static List<RankingEntry> denseRank(
            RankingMetric metric,
            List<Map.Entry<Long, Long>> values
    ) {
        long[] rank = {0L};
        long[] previous = {Long.MIN_VALUE};
        return values.stream().map(entry -> {
            if (rank[0] == 0L || entry.getValue() != previous[0]) {
                rank[0]++;
                previous[0] = entry.getValue();
            }
            return new RankingEntry(metric, rank[0], entry.getKey(), entry.getValue());
        }).toList();
    }

    private static String key(RankingMetric metric) {
        return switch (metric) {
            case SURVIVAL -> SURVIVAL_KEY;
            case KILLS -> KILLS_KEY;
        };
    }

    private static Map.Entry<Long, Long> tupleEntry(ZSetOperations.TypedTuple<String> tuple) {
        String memberId = Objects.requireNonNull(tuple.getValue(), "Redis ranking memberId");
        Double value = Objects.requireNonNull(tuple.getScore(), "Redis ranking value");
        return Map.entry(Long.valueOf(memberId), value.longValue());
    }
}
