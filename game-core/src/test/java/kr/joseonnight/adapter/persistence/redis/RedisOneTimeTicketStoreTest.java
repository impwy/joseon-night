package kr.joseonnight.adapter.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.joseonnight.adapter.persistence.redis.ticket.RedisOneTimeTicketStore;
import kr.joseonnight.application.member.provided.GameSocketTicket;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisOneTimeTicketStoreTest {

    @Test
    void fallbackConsumptionCannotBeReplayedFromStaleRedisValueAfterRecovery() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString()))
                .thenThrow(new DataAccessResourceFailureException("offline"))
                .thenReturn("7");
        when(redis.delete(anyString())).thenReturn(true);
        RedisOneTimeTicketStore store = new RedisOneTimeTicketStore(
                redis,
                Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC),
                30
        );

        GameSocketTicket ticket = store.issue(7L);

        assertThat(store.consume(ticket.value())).contains(7L);
        assertThat(store.consume(ticket.value())).isEmpty();
        verify(values, times(1)).getAndDelete(anyString());
        verify(redis).delete("socket-ticket:v1:" + ticket.value());
    }

    @Test
    void redisBackedTicketIsNotReplayedLocallyWhenAnotherConsumerRemovedRedisValue() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn(null);
        RedisOneTimeTicketStore store = new RedisOneTimeTicketStore(
                redis,
                Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC),
                30
        );

        GameSocketTicket ticket = store.issue(9L);

        assertThat(store.consume(ticket.value())).isEmpty();
    }
}
