package kr.joseonnight.adapter.persistence.redis.cache;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

abstract class RedisJsonCatalogCache<T> {

    private final String key;
    private final Class<T> valueType;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ttl;
    private final AtomicReference<LocalCacheEntry<T>> local = new AtomicReference<>();

    @SuppressFBWarnings(
            value = "CT_CONSTRUCTOR_THROW",
            justification = "Fail-fast TTL validation happens before the Spring bean can be published"
    )
    RedisJsonCatalogCache(
            String key,
            Class<T> valueType,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            long ttlSeconds
    ) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Catalog cache TTL must be positive");
        }
        this.key = key;
        this.valueType = valueType;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        ttl = Duration.ofSeconds(ttlSeconds);
    }

    final Optional<T> findValue() {
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null) {
                return findLocal();
            }
            T value = objectMapper.readValue(payload, valueType);
            putLocal(value);
            return Optional.of(value);
        } catch (DataAccessException | JacksonException | IllegalArgumentException exception) {
            return findLocal();
        }
    }

    final void putValue(T value) {
        putLocal(value);
        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, payload, ttl);
        } catch (DataAccessException | JacksonException exception) {
            // PostgreSQL remains authoritative; one bounded local entry covers a short Redis outage.
        }
    }

    private void putLocal(T value) {
        local.set(new LocalCacheEntry<>(value, Instant.now(clock).plus(ttl)));
    }

    private Optional<T> findLocal() {
        LocalCacheEntry<T> entry = local.get();
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(Instant.now(clock))) {
            local.compareAndSet(entry, null);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    private record LocalCacheEntry<T>(T value, Instant expiresAt) {
    }
}
