package kr.joseonnight.application.item.provided;

import java.util.Map;
import java.util.Objects;

public record EvolutionRecipeCatalogEntry(
        String id,
        String displayName,
        String firstItemId,
        String secondItemId,
        Map<String, Object> configuration
) {
    public EvolutionRecipeCatalogEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(firstItemId, "firstItemId");
        Objects.requireNonNull(secondItemId, "secondItemId");
        configuration = Map.copyOf(Objects.requireNonNull(configuration, "configuration"));
    }
}
