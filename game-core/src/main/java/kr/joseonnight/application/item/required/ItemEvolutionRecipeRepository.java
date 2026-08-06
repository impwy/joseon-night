package kr.joseonnight.application.item.required;

import java.util.List;
import kr.joseonnight.domain.item.ItemEvolutionRecipe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemEvolutionRecipeRepository extends JpaRepository<ItemEvolutionRecipe, String> {

    List<ItemEvolutionRecipe> findAllByEnabledTrueOrderByIdAsc();
}
