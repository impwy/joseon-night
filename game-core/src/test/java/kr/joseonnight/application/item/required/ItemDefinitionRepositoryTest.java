package kr.joseonnight.application.item.required;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.Map;
import kr.joseonnight.domain.item.ItemCategory;
import kr.joseonnight.domain.item.ItemDefinition;
import kr.joseonnight.domain.item.ItemEvolutionRecipe;
import kr.joseonnight.support.test.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RepositoryTest
class ItemDefinitionRepositoryTest {

    @Autowired
    private ItemDefinitionRepository itemRepository;

    @Autowired
    private ItemEvolutionRecipeRepository recipeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsItemsAndEvolutionRecipeThroughRequiredPorts() {
        itemRepository.save(ItemDefinition.define(
                "test-sun-talisman",
                "태양 부적",
                "뜨거운 부적을 발사한다.",
                ItemCategory.WEAPON,
                5,
                Map.of("damage", 10),
                true
        ));
        itemRepository.save(ItemDefinition.define(
                "test-wind-bell",
                "바람 방울",
                "적을 밀어내는 바람을 일으킨다.",
                ItemCategory.WEAPON,
                5,
                Map.of("knockback", 2),
                true
        ));
        entityManager.flush();

        recipeRepository.save(ItemEvolutionRecipe.define(
                "test-sun-storm",
                "일풍신주",
                "test-sun-talisman",
                "test-wind-bell",
                Map.of("projectiles", 3),
                true
        ));
        entityManager.flush();
        entityManager.clear();

        ItemEvolutionRecipe recipe = recipeRepository.findById("test-sun-storm").orElseThrow();
        ItemDefinition firstItem = itemRepository.findById(recipe.getFirstItemId()).orElseThrow();

        assertThat(recipe.getDisplayName()).isEqualTo("일풍신주");
        assertThat(recipe.getConfiguration()).containsEntry("projectiles", 3);
        assertThat(firstItem.getCategory()).isEqualTo(ItemCategory.WEAPON);
        assertThat(firstItem.getConfiguration()).containsEntry("damage", 10);
    }

    @Test
    void migrationDescribesThunderBellAsADirectStrike() {
        entityManager.flush();
        entityManager.clear();

        ItemDefinition thunderBell = itemRepository.findById("thunder-bell").orElseThrow();

        assertThat(thunderBell.getDescription()).isEqualTo("가까운 적의 머리 위에 즉시 낙뢰를 내린다.");
    }
}
