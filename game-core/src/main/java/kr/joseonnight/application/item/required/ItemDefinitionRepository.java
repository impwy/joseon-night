package kr.joseonnight.application.item.required;

import java.util.List;
import kr.joseonnight.domain.item.ItemDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemDefinitionRepository extends JpaRepository<ItemDefinition, String> {

    List<ItemDefinition> findAllByEnabledTrueOrderByIdAsc();
}
