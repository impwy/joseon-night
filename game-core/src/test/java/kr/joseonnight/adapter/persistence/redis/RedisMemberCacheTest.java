package kr.joseonnight.adapter.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import kr.joseonnight.adapter.persistence.redis.cache.RedisMemberCache;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.domain.member.MemberRole;
import kr.joseonnight.domain.member.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisMemberCacheTest {

    @Test
    void localFallbackExpiresWithTheSameTtlWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString()))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T12:00:00Z"));
        RedisMemberCache cache = new RedisMemberCache(redis, clock, 300L);
        MemberView member = new MemberView(
                7L,
                MemberRole.PLAYER,
                MemberStatus.ACTIVE,
                clock.instant(),
                clock.instant()
        );

        cache.put(member);

        assertThat(cache.find(member.id())).contains(member);
        clock.advanceSeconds(301L);
        assertThat(cache.find(member.id())).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported in this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
