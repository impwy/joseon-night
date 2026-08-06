package kr.joseonnight.application.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.joseonnight.application.item.provided.ItemCatalog;
import kr.joseonnight.application.item.provided.ItemCatalogEntry;
import kr.joseonnight.application.item.required.ItemCatalogCache;
import kr.joseonnight.application.item.required.ItemDefinitionRepository;
import kr.joseonnight.application.item.required.ItemEvolutionRecipeRepository;
import kr.joseonnight.domain.item.ItemCategory;
import kr.joseonnight.domain.item.ItemDefinition;
import kr.joseonnight.domain.item.ItemEvolutionRecipe;
import org.junit.jupiter.api.Test;

class ItemCatalogServiceTest {

    @Test
    void returnsCachedCatalogWithoutReadingPostgreSql() {
        ItemDefinitionRepository items = mock(ItemDefinitionRepository.class);
        ItemEvolutionRecipeRepository recipes = mock(ItemEvolutionRecipeRepository.class);
        ItemCatalogCache cache = mock(ItemCatalogCache.class);
        ItemCatalog cached = new ItemCatalog(List.of(new ItemCatalogEntry(
                "seal-talisman", "봉인 부적", "부적을 발사한다.", ItemCategory.WEAPON, 5, Map.of()
        )), List.of());
        when(cache.find()).thenReturn(Optional.of(cached));

        var finder = new ItemCatalogService(items, recipes, cache);

        assertThat(finder.findCatalog()).isSameAs(cached);
        verifyNoInteractions(items, recipes);
    }

    @Test
    void omitsRecipesWhoseMaterialIsNotEnabledAndPopulatesCache() {
        ItemDefinitionRepository items = mock(ItemDefinitionRepository.class);
        ItemEvolutionRecipeRepository recipes = mock(ItemEvolutionRecipeRepository.class);
        ItemCatalogCache cache = mock(ItemCatalogCache.class);
        when(cache.find()).thenReturn(Optional.empty());
        when(items.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(ItemDefinition.define(
                "seal-talisman", "봉인 부적", "부적을 발사한다.", ItemCategory.WEAPON, 5, Map.of(), true
        )));
        when(recipes.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(ItemEvolutionRecipe.define(
                "invalid-recipe", "사용 불가 진화", "seal-talisman", "disabled-item", Map.of(), true
        )));

        var finder = new ItemCatalogService(items, recipes, cache);
        ItemCatalog loaded = finder.findCatalog();

        assertThat(loaded.items()).extracting(ItemCatalogEntry::id).containsExactly("seal-talisman");
        assertThat(loaded.evolutionRecipes()).isEmpty();
        verify(cache).put(loaded);
    }
}
