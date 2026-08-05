package kr.joseonnight.adapter.persistence.redis.ticket;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.joseonnight.application.member.required.DesktopLoginAttempt;
import kr.joseonnight.application.member.required.DesktopLoginAttemptStore;
import kr.joseonnight.application.member.required.DesktopLoginStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RedisDesktopLoginAttemptStore implements DesktopLoginAttemptStore {

    private static final String ATTEMPT_PREFIX = "desktop-login:v1:attempt:";
    private static final String LAUNCH_PREFIX = "desktop-login:v1:launch:";
    private static final String REGISTRATION_PREFIX = "desktop-login:v1:registration:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Duration ttl;
    private final Map<UUID, DesktopLoginAttempt> fallback = new ConcurrentHashMap<>();

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed Redis client is an injected collaborator")
    public RedisDesktopLoginAttemptStore(
            StringRedisTemplate redisTemplate,
            Clock clock,
            @Value("${joseon-night.redis.login-attempt-seconds:300}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public synchronized void save(DesktopLoginAttempt attempt) {
        pruneExpiredFallback();
        DesktopLoginAttempt previous = fallback.put(attempt.id(), attempt);
        Duration remaining = Duration.between(Instant.now(clock), attempt.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) {
            fallback.remove(attempt.id(), attempt);
            return;
        }
        try {
            removeIndexes(previous);
            String attemptKey = attemptKey(attempt.id());
            redisTemplate.delete(attemptKey);
            redisTemplate.opsForHash().putAll(attemptKey, toMap(attempt));
            redisTemplate.expire(attemptKey, remaining);
            if (attempt.browserLaunchTokenHash() != null) {
                redisTemplate.opsForValue().set(
                        launchKey(attempt.browserLaunchTokenHash()),
                        attempt.id().toString(),
                        remaining
                );
            }
            if (attempt.registrationToken() != null) {
                redisTemplate.opsForValue().set(
                        registrationKey(hash(attempt.registrationToken())),
                        attempt.id().toString(),
                        remaining
                );
            }
        } catch (DataAccessException ignored) {
            // The bounded process-local copy supports a single-node desktop flow.
        }
    }

    @Override
    public Optional<DesktopLoginAttempt> find(UUID attemptId) {
        pruneExpiredFallback();
        DesktopLoginAttempt local = fallback.get(attemptId);
        if (local != null) {
            return nonExpired(local);
        }
        try {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(attemptKey(attemptId));
            if (!values.isEmpty()) {
                DesktopLoginAttempt attempt = fromMap(attemptId, values);
                fallback.put(attemptId, attempt);
                return nonExpired(attempt);
            }
        } catch (DataAccessException | IllegalArgumentException ignored) {
            // Fall through to the process-local copy.
        }
        return nonExpired(fallback.get(attemptId));
    }

    @Override
    public synchronized Optional<DesktopLoginAttempt> consumeBrowserLaunchTokenHash(String tokenHash) {
        pruneExpiredFallback();
        try {
            String id = redisTemplate.opsForValue().getAndDelete(launchKey(tokenHash));
            if (id != null) {
                return consumeFound(find(UUID.fromString(id)), tokenHash);
            }
            return Optional.empty();
        } catch (DataAccessException | IllegalArgumentException ignored) {
            // Consume the process-local copy below.
        }
        return fallback.values().stream()
                .filter(value -> tokenHash.equals(value.browserLaunchTokenHash()))
                .findFirst()
                .flatMap(this::nonExpired)
                .flatMap(value -> consumeFound(Optional.of(value), tokenHash));
    }

    @Override
    public Optional<DesktopLoginAttempt> findByRegistrationToken(String token) {
        pruneExpiredFallback();
        return findByRegistrationIndex(registrationKey(hash(token)));
    }

    @Override
    public Duration ttl() {
        return ttl;
    }

    private Optional<DesktopLoginAttempt> consumeFound(
            Optional<DesktopLoginAttempt> found,
            String tokenHash
    ) {
        if (found.isEmpty()
                || !tokenHash.equals(found.orElseThrow().browserLaunchTokenHash())) {
            return Optional.empty();
        }
        DesktopLoginAttempt consumed = found.orElseThrow().withoutBrowserLaunchToken();
        save(consumed);
        return Optional.of(consumed);
    }

    private Optional<DesktopLoginAttempt> findByRegistrationIndex(String indexKey) {
        try {
            String id = redisTemplate.opsForValue().get(indexKey);
            if (id != null) {
                return find(UUID.fromString(id));
            }
        } catch (DataAccessException | IllegalArgumentException ignored) {
            // Scan the small local fallback below.
        }
        String registrationHash = indexKey.substring(REGISTRATION_PREFIX.length());
        return fallback.values().stream()
                .filter(value -> value.registrationToken() != null)
                .filter(value -> hash(value.registrationToken()).equals(registrationHash))
                .findFirst()
                .flatMap(this::nonExpired);
    }

    private Optional<DesktopLoginAttempt> nonExpired(DesktopLoginAttempt attempt) {
        if (attempt == null || !attempt.expiresAt().isAfter(Instant.now(clock))) {
            if (attempt != null) {
                fallback.remove(attempt.id(), attempt);
            }
            return Optional.empty();
        }
        return Optional.of(attempt);
    }

    private void pruneExpiredFallback() {
        Instant now = Instant.now(clock);
        fallback.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private void removeIndexes(DesktopLoginAttempt attempt) {
        if (attempt == null) {
            return;
        }
        if (attempt.browserLaunchTokenHash() != null) {
            redisTemplate.delete(launchKey(attempt.browserLaunchTokenHash()));
        }
        if (attempt.registrationToken() != null) {
            redisTemplate.delete(registrationKey(hash(attempt.registrationToken())));
        }
    }

    private static Map<String, String> toMap(DesktopLoginAttempt attempt) {
        Map<String, String> values = new HashMap<>();
        values.put("pollTokenHash", attempt.pollTokenHash());
        putIfNotNull(values, "browserLaunchTokenHash", attempt.browserLaunchTokenHash());
        values.put("expiresAt", attempt.expiresAt().toString());
        values.put("status", attempt.status().name());
        putIfNotNull(values, "providerSubjectHmac", attempt.providerSubjectHmac());
        putIfNotNull(values, "registrationToken", attempt.registrationToken());
        putIfNotNull(values, "memberId", attempt.memberId() == null ? null : attempt.memberId().toString());
        putIfNotNull(values, "nickname", attempt.nickname());
        return values;
    }

    private static DesktopLoginAttempt fromMap(UUID id, Map<Object, Object> values) {
        String memberId = stringValue(values, "memberId");
        return new DesktopLoginAttempt(
                id,
                requiredValue(values, "pollTokenHash"),
                stringValue(values, "browserLaunchTokenHash"),
                Instant.parse(requiredValue(values, "expiresAt")),
                DesktopLoginStatus.valueOf(requiredValue(values, "status")),
                stringValue(values, "providerSubjectHmac"),
                stringValue(values, "registrationToken"),
                memberId == null ? null : Long.valueOf(memberId),
                stringValue(values, "nickname")
        );
    }

    private static String requiredValue(Map<Object, Object> values, String name) {
        String value = stringValue(values, name);
        if (value == null) {
            throw new IllegalArgumentException("Missing login attempt field: " + name);
        }
        return value;
    }

    private static String stringValue(Map<Object, Object> values, String name) {
        Object value = values.get(name);
        return value instanceof String string ? string : null;
    }

    private static void putIfNotNull(Map<String, String> values, String name, String value) {
        if (value != null) {
            values.put(name, value);
        }
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String attemptKey(UUID id) { return ATTEMPT_PREFIX + id; }
    private static String launchKey(String hash) { return LAUNCH_PREFIX + hash; }
    private static String registrationKey(String hash) { return REGISTRATION_PREFIX + hash; }
}
