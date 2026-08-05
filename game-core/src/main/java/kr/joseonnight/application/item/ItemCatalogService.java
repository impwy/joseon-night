package kr.joseonnight.application.item;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Set;
import java.util.stream.Collectors;
import kr.joseonnight.application.item.provided.EvolutionRecipeCatalogEntry;
import kr.joseonnight.application.item.provided.ItemCatalog;
import kr.joseonnight.application.item.provided.ItemCatalogEntry;
import kr.joseonnight.application.item.provided.ItemCatalogFinder;
import kr.joseonnight.application.item.required.ItemCatalogCache;
import kr.joseonnight.application.item.required.ItemDefinitionRepository;
import kr.joseonnight.application.item.required.ItemEvolutionRecipeRepository;
import kr.joseonnight.domain.item.ItemDefinition;
import kr.joseonnight.domain.item.ItemEvolutionRecipe;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.transaction.annotation.Transactional;

@ValidatedApplicationService
public final class ItemCatalogService implements ItemCatalogFinder {

    private final ItemDefinitionRepository itemRepository;
    private final ItemEvolutionRecipeRepository recipeRepository;
    private final ItemCatalogCache cache;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Application port is an injected collaborator")
    public ItemCatalogService(
            ItemDefinitionRepository itemRepository,
            ItemEvolutionRecipeRepository recipeRepository,
            ItemCatalogCache cache
    ) {
        this.itemRepository = itemRepository;
        this.recipeRepository = recipeRepository;
        this.cache = cache;
    }

    @Override
    @Transactional(readOnly = true)
    public ItemCatalog findCatalog() {
        return cache.find().orElseGet(this::loadFromPostgreSql);
    }

    private ItemCatalog loadFromPostgreSql() {
        var items = itemRepository.findAllByEnabledTrueOrderByIdAsc().stream()
                .map(ItemCatalogService::toItemEntry)
                .toList();
        Set<String> enabledItemIds = items.stream()
                .map(ItemCatalogEntry::id)
                .collect(Collectors.toUnmodifiableSet());
        var recipes = recipeRepository.findAllByEnabledTrueOrderByIdAsc().stream()
                .filter(recipe -> enabledItemIds.contains(recipe.getFirstItemId()))
                .filter(recipe -> enabledItemIds.contains(recipe.getSecondItemId()))
                .map(ItemCatalogService::toRecipeEntry)
                .toList();
        ItemCatalog catalog = new ItemCatalog(items, recipes);
        cache.put(catalog);
        return catalog;
    }

    private static ItemCatalogEntry toItemEntry(ItemDefinition item) {
        return new ItemCatalogEntry(
                item.getId(),
                item.getDisplayName(),
                item.getDescription(),
                item.getCategory(),
                item.getMaxLevel(),
                item.getConfiguration()
        );
    }

    private static EvolutionRecipeCatalogEntry toRecipeEntry(ItemEvolutionRecipe recipe) {
        return new EvolutionRecipeCatalogEntry(
                recipe.getId(),
                recipe.getDisplayName(),
                recipe.getFirstItemId(),
                recipe.getSecondItemId(),
                recipe.getConfiguration()
        );
    }
}
