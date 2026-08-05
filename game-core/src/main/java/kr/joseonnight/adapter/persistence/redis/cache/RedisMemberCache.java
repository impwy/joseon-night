package kr.joseonnight.adapter.persistence.redis.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.MemberCache;
import kr.joseonnight.domain.member.MemberRole;
import kr.joseonnight.domain.member.MemberStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RedisMemberCache implements MemberCache {

    private static final String KEY_PREFIX = "member:v1:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Duration ttl;
    private final Map<Long, LocalCacheEntry> fallback = new ConcurrentHashMap<>();

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed Redis client is an injected collaborator")
    public RedisMemberCache(
            StringRedisTemplate redisTemplate,
            Clock clock,
            @Value("${joseon-night.redis.member-cache-seconds:300}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<MemberView> find(Long memberId) {
        try {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(key(memberId));
            if (values.isEmpty()) {
                return local(memberId);
            }
            MemberView member = new MemberView(
                    memberId,
                    MemberRole.valueOf((String) values.get("role")),
                    MemberStatus.valueOf((String) values.get("status")),
                    Instant.parse((String) values.get("registeredAt")),
                    Instant.parse((String) values.get("lastLoginAt"))
            );
            putLocal(member);
            return Optional.of(member);
        } catch (DataAccessException | IllegalArgumentException exception) {
            return local(memberId);
        }
    }

    @Override
    public void put(MemberView member) {
        putLocal(member);
        try {
            String key = key(member.id());
            redisTemplate.opsForHash().putAll(key, Map.of(
                    "role", member.role().name(),
                    "status", member.status().name(),
                    "registeredAt", member.registeredAt().toString(),
                    "lastLoginAt", member.lastLoginAt().toString()
            ));
            redisTemplate.expire(key, ttl);
        } catch (DataAccessException ignored) {
            // PostgreSQL remains the source of truth; the local copy is a temporary fallback.
        }
    }

    @Override
    public void evict(Long memberId) {
        fallback.remove(memberId);
        try {
            redisTemplate.delete(key(memberId));
        } catch (DataAccessException ignored) {
            // A failed cache eviction must not make the member write fail.
        }
    }

    private static String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }

    private void putLocal(MemberView member) {
        fallback.put(member.id(), new LocalCacheEntry(member, Instant.now(clock).plus(ttl)));
    }

    private Optional<MemberView> local(Long memberId) {
        LocalCacheEntry entry = fallback.get(memberId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt.isAfter(Instant.now(clock))) {
            fallback.remove(memberId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.member);
    }

    private record LocalCacheEntry(MemberView member, Instant expiresAt) {
    }
}
