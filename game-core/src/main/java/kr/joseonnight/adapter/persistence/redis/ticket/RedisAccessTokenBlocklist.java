package kr.joseonnight.adapter.persistence.redis.ticket;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.joseonnight.application.member.required.AccessTokenBlocklist;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RedisAccessTokenBlocklist implements AccessTokenBlocklist {

    private static final String KEY_PREFIX = "jwt:blocklist:v1:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Map<String, Instant> fallback = new ConcurrentHashMap<>();

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed Redis client is an injected collaborator")
    public RedisAccessTokenBlocklist(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public void block(String jwtId, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(clock), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        fallback.put(jwtId, expiresAt);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jwtId, "blocked", remaining);
        } catch (DataAccessException ignored) {
            // The local fallback keeps logout effective for this process.
        }
    }

    @Override
    public boolean isBlocked(String jwtId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jwtId))) {
                return true;
            }
        } catch (DataAccessException ignored) {
            // Check the local fallback below.
        }
        Instant expiresAt = fallback.get(jwtId);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(Instant.now(clock))) {
            fallback.remove(jwtId, expiresAt);
            return false;
        }
        return true;
    }
}
