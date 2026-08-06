package kr.joseonnight.application.character.provided;

import java.util.List;
import java.util.Objects;

public record CharacterCatalog(List<CharacterCatalogEntry> characters) {
    public CharacterCatalog {
        characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
    }
}
