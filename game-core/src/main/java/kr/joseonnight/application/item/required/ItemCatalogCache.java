package kr.joseonnight.application.item.required;

import java.util.Optional;
import kr.joseonnight.application.item.provided.ItemCatalog;

public interface ItemCatalogCache {

    Optional<ItemCatalog> find();

    void put(ItemCatalog catalog);
}
