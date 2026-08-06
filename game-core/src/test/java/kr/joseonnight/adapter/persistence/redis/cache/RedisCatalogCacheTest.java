package kr.joseonnight.adapter.persistence.redis.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import kr.joseonnight.application.character.provided.CharacterCatalog;
import kr.joseonnight.application.character.provided.CharacterCatalogEntry;
import kr.joseonnight.application.character.provided.SkillCatalogEntry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class RedisCatalogCacheTest {

    @Test
    void fallsBackToOneBoundedLocalEntryAndExpiresItWhenRedisIsDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T12:00:00Z"));
        RedisCharacterCatalogCache cache = new RedisCharacterCatalogCache(
                redis,
                new ObjectMapper(),
                clock,
                10
        );
        CharacterCatalog catalog = catalog();

        assertThat(cache.find()).isEmpty();
        cache.put(catalog);
        assertThat(cache.find()).containsSame(catalog);

        clock.advance(Duration.ofSeconds(11));

        assertThat(cache.find()).isEmpty();
    }

    private static CharacterCatalog catalog() {
        return new CharacterCatalog(List.of(new CharacterCatalogEntry(
                "dokkaebi-hunter",
                "도깨비 사냥꾼",
                "봉인 부적으로 싸운다.",
                new SkillCatalogEntry("protective-barrier", "호신결계", "한 번 막는다.", Map.of()),
                "seal-talisman",
                true
        )));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
