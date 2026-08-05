package kr.joseonnight.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemDefinitionTest {

    @Test
    void definesWeaponAndEvolutionUsingOnlyIds() {
        ItemDefinition item = ItemDefinition.define(
                "seal-talisman",
                "봉인 부적",
                "가장 가까운 적을 공격한다.",
                ItemCategory.WEAPON,
                5,
                Map.of("damage", 1),
                true
        );
        ItemEvolutionRecipe recipe = ItemEvolutionRecipe.define(
                "all-direction-seal",
                "만방봉인진",
                "seal-talisman",
                "exorcist-sword",
                Map.of("damage", 3),
                true
        );

        assertThat(item.getMaxLevel()).isEqualTo(5);
        assertThat(item.getConfiguration()).containsEntry("damage", 1);
        assertThat(recipe.getFirstItemId()).isEqualTo("seal-talisman");
        assertThat(recipe.getSecondItemId()).isEqualTo("exorcist-sword");
    }

    @Test
    void itemMustHavePositiveMaxLevel() {
        assertThatThrownBy(() -> ItemDefinition.define(
                "seal-talisman",
                "봉인 부적",
                "설명",
                ItemCategory.WEAPON,
                0,
                Map.of(),
                true
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evolutionMaterialsMustBeDifferent() {
        assertThatThrownBy(() -> ItemEvolutionRecipe.define(
                "invalid-evolution",
                "잘못된 진화",
                "seal-talisman",
                "seal-talisman",
                Map.of(),
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");
    }
}
