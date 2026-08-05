package kr.joseonnight.adapter.persistence.redis.cache;

import java.time.Clock;
import java.util.Optional;
import kr.joseonnight.application.character.provided.CharacterCatalog;
import kr.joseonnight.application.character.required.CharacterCatalogCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class RedisCharacterCatalogCache
        extends RedisJsonCatalogCache<CharacterCatalog>
        implements CharacterCatalogCache {

    private static final String CACHE_KEY = "catalog:characters:v1";

    public RedisCharacterCatalogCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${joseon-night.redis.catalog-cache-seconds:21600}") long ttlSeconds
    ) {
        super(CACHE_KEY, CharacterCatalog.class, redisTemplate, objectMapper, clock, ttlSeconds);
    }

    @Override
    public Optional<CharacterCatalog> find() {
        return findValue();
    }

    @Override
    public void put(CharacterCatalog catalog) {
        putValue(catalog);
    }
}
