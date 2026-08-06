package kr.joseonnight.application.character.provided;

import java.util.Objects;

public record CharacterCatalogEntry(
        String id,
        String displayName,
        String description,
        SkillCatalogEntry skill,
        String startingItemId,
        boolean unlockedByDefault
) {
    public CharacterCatalogEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(startingItemId, "startingItemId");
    }
}
