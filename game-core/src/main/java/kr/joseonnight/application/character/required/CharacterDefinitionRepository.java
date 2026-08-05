package kr.joseonnight.application.character.required;

import java.util.List;
import kr.joseonnight.domain.character.CharacterDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterDefinitionRepository extends JpaRepository<CharacterDefinition, String> {

    List<CharacterDefinition> findAllByEnabledTrueOrderByIdAsc();
}
