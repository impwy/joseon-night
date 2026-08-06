package kr.joseonnight.application.character.provided;

import java.util.Map;
import java.util.Objects;

public record SkillCatalogEntry(
        String id,
        String displayName,
        String description,
        Map<String, Object> configuration
) {
    public SkillCatalogEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        configuration = Map.copyOf(Objects.requireNonNull(configuration, "configuration"));
    }
}
