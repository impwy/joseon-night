package kr.joseonnight.application.item.provided;

import java.util.List;
import java.util.Objects;

public record ItemCatalog(
        List<ItemCatalogEntry> items,
        List<EvolutionRecipeCatalogEntry> evolutionRecipes
) {
    public ItemCatalog {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        evolutionRecipes = List.copyOf(Objects.requireNonNull(evolutionRecipes, "evolutionRecipes"));
    }
}
