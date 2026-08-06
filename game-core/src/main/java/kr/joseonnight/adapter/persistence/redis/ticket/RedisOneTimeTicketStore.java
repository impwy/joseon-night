package kr.joseonnight.adapter.persistence.redis.ticket;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.member.provided.GameSocketTicket;
import kr.joseonnight.application.member.required.GameSocketTicketStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RedisOneTimeTicketStore implements GameSocketTicketStore {

    private static final String KEY_PREFIX = "socket-ticket:v1:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, LocalTicket> fallback = new ConcurrentHashMap<>();
    private final Map<String, Instant> consumed = new ConcurrentHashMap<>();

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed Redis client is an injected collaborator")
    public RedisOneTimeTicketStore(
            StringRedisTemplate redisTemplate,
            Clock clock,
            @Value("${joseon-night.redis.websocket-ticket-seconds:30}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public synchronized GameSocketTicket issue(Long memberId) {
        pruneExpiredState();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now(clock).plus(ttl);
        fallback.put(value, new LocalTicket(memberId, expiresAt, false));
        try {
            redisTemplate.opsForValue().set(key(value), memberId.toString(), ttl);
            fallback.computeIfPresent(value, (ignored, ticket) -> ticket.redisBacked());
        } catch (DataAccessException ignored) {
            // The short-lived local fallback keeps a single-node desktop session available.
        }
        return new GameSocketTicket(value, expiresAt);
    }

    @Override
    public synchronized Optional<Long> consume(String value) {
        pruneExpiredState();
        if (consumed.containsKey(value)) {
            deleteRemoteBestEffort(value);
            fallback.remove(value);
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        try {
            String memberId = redisTemplate.opsForValue().getAndDelete(key(value));
            if (memberId != null) {
                LocalTicket local = fallback.remove(value);
                consumed.put(value, local == null ? now.plus(ttl) : local.expiresAt());
                try {
                    return Optional.of(Long.valueOf(memberId));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
            LocalTicket local = fallback.remove(value);
            if (local == null || !local.expiresAt().isAfter(now)) {
                return Optional.empty();
            }
            if (local.redisStored()) {
                consumed.put(value, local.expiresAt());
                return Optional.empty();
            }
            consumed.put(value, local.expiresAt());
            return Optional.of(local.memberId());
        } catch (DataAccessException ignored) {
            // Redis may still contain the issued value. A tombstone prevents replay after recovery.
        }
        LocalTicket local = fallback.remove(value);
        if (local == null || !local.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        consumed.put(value, local.expiresAt());
        return Optional.of(local.memberId());
    }

    private void deleteRemoteBestEffort(String value) {
        try {
            redisTemplate.delete(key(value));
        } catch (DataAccessException ignored) {
            // Keep the local tombstone until the ticket's original expiration.
        }
    }

    private void pruneExpiredState() {
        Instant now = Instant.now(clock);
        fallback.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        consumed.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private static String key(String value) {
        return KEY_PREFIX + value;
    }

    private record LocalTicket(Long memberId, Instant expiresAt, boolean redisStored) {

        private LocalTicket redisBacked() {
            return new LocalTicket(memberId, expiresAt, true);
        }
    }
}
