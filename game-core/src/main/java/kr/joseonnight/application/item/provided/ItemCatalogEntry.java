package kr.joseonnight.application.item.provided;

import java.util.Map;
import java.util.Objects;
import kr.joseonnight.domain.item.ItemCategory;

public record ItemCatalogEntry(
        String id,
        String displayName,
        String description,
        ItemCategory category,
        int maxLevel,
        Map<String, Object> configuration
) {
    public ItemCatalogEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        configuration = Map.copyOf(Objects.requireNonNull(configuration, "configuration"));
    }
}
