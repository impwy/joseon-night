package kr.joseonnight.adapter.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import kr.joseonnight.adapter.persistence.redis.ticket.RedisDesktopLoginAttemptStore;
import kr.joseonnight.application.member.required.DesktopLoginAttempt;
import kr.joseonnight.application.member.required.DesktopLoginStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisDesktopLoginAttemptStoreTest {

    @Test
    void localLaunchAndRegistrationIndexesRejectExpiredAttempts() {
        RedisFixture fixture = fixture(Instant.parse("2026-08-05T12:00:00Z"));
        DesktopLoginAttempt expired = attempt(
                fixture.now.minusSeconds(1),
                "launch-hash",
                DesktopLoginStatus.NICKNAME_REQUIRED,
                "registration-ticket"
        );
        fixture.store.save(expired);

        assertThat(fixture.store.consumeBrowserLaunchTokenHash("launch-hash")).isEmpty();
        assertThat(fixture.store.findByRegistrationToken("registration-ticket")).isEmpty();
    }

    @Test
    void localFallbackConsumesBrowserLaunchTokenExactlyOnceWhenRedisIsDown() {
        RedisFixture fixture = fixture(Instant.parse("2026-08-05T12:00:00Z"));
        DesktopLoginAttempt pending = attempt(
                fixture.now.plusSeconds(300),
                "launch-hash",
                DesktopLoginStatus.PENDING,
                null
        );
        fixture.store.save(pending);

        assertThat(fixture.store.consumeBrowserLaunchTokenHash("launch-hash")).isPresent();
        assertThat(fixture.store.consumeBrowserLaunchTokenHash("launch-hash")).isEmpty();
        assertThat(fixture.store.find(pending.id()).orElseThrow().browserLaunchTokenHash()).isNull();
    }

    private static DesktopLoginAttempt attempt(
            Instant expiresAt,
            String launchHash,
            DesktopLoginStatus status,
            String registrationToken
    ) {
        return new DesktopLoginAttempt(
                UUID.randomUUID(),
                "a".repeat(64),
                launchHash,
                expiresAt,
                status,
                "b".repeat(64),
                registrationToken,
                null,
                null
        );
    }

    private static RedisFixture fixture(Instant now) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashes);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(hashes.entries(anyString())).thenReturn(Map.of());
        when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
        when(values.getAndDelete(anyString()))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        RedisDesktopLoginAttemptStore store = new RedisDesktopLoginAttemptStore(
                redisTemplate,
                Clock.fixed(now, ZoneOffset.UTC),
                300
        );
        return new RedisFixture(store, now);
    }

    private record RedisFixture(RedisDesktopLoginAttemptStore store, Instant now) {
    }
}
