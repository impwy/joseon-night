package kr.joseonnight.adapter.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.joseonnight.adapter.persistence.redis.ticket.RedisAccessTokenBlocklist;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisAccessTokenBlocklistTest {

    @Test
    void localFallbackBlocksJwtIdOnlyUntilTokenExpiration() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        RedisAccessTokenBlocklist blocklist = new RedisAccessTokenBlocklist(
                redis,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        blocklist.block("jwt-id", now.plusSeconds(30));

        assertThat(blocklist.isBlocked("jwt-id")).isTrue();
        blocklist.block("already-expired", now.minusSeconds(1));
        assertThat(blocklist.isBlocked("already-expired")).isFalse();
    }
}
