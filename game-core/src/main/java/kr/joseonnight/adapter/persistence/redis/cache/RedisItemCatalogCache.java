package kr.joseonnight.adapter.persistence.redis.cache;

import java.time.Clock;
import java.util.Optional;
import kr.joseonnight.application.item.provided.ItemCatalog;
import kr.joseonnight.application.item.required.ItemCatalogCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class RedisItemCatalogCache
        extends RedisJsonCatalogCache<ItemCatalog>
        implements ItemCatalogCache {

    private static final String CACHE_KEY = "catalog:items:v1";

    public RedisItemCatalogCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${joseon-night.redis.catalog-cache-seconds:21600}") long ttlSeconds
    ) {
        super(CACHE_KEY, ItemCatalog.class, redisTemplate, objectMapper, clock, ttlSeconds);
    }

    @Override
    public Optional<ItemCatalog> find() {
        return findValue();
    }

    @Override
    public void put(ItemCatalog catalog) {
        putValue(catalog);
    }
}
