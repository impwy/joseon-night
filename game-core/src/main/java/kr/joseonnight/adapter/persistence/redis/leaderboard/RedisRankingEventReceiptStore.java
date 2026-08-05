package kr.joseonnight.adapter.persistence.redis.leaderboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.ranking.required.RankingEventReceiptStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RedisRankingEventReceiptStore implements RankingEventReceiptStore {

    private static final String KEY_PREFIX = "ranking:event-receipt:v1:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Duration ttl;
    private final Map<String, Instant> fallback = new ConcurrentHashMap<>();

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed Redis client is an injected collaborator")
    public RedisRankingEventReceiptStore(
            StringRedisTemplate redisTemplate,
            Clock clock,
            @Value("${joseon-night.redis.ranking-event-receipt-seconds:604800}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public boolean isProcessed(String eventId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key(eventId)))) {
                return true;
            }
        } catch (DataAccessException ignored) {
            // Fall through to the single-process receipt projection.
        }
        Instant expiresAt = fallback.get(eventId);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(Instant.now(clock))) {
            fallback.remove(eventId, expiresAt);
            return false;
        }
        return true;
    }

    @Override
    public void markProcessed(String eventId) {
        fallback.put(eventId, Instant.now(clock).plus(ttl));
        try {
            redisTemplate.opsForValue().set(key(eventId), "processed", ttl);
        } catch (DataAccessException ignored) {
            // The local receipt still prevents duplicates in this single application instance.
        }
    }

    private static String key(String eventId) {
        return KEY_PREFIX + eventId;
    }
}
