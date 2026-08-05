package kr.joseonnight.application.character.required;

import java.util.Optional;
import kr.joseonnight.application.character.provided.CharacterCatalog;

public interface CharacterCatalogCache {

    Optional<CharacterCatalog> find();

    void put(CharacterCatalog catalog);
}
