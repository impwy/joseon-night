package kr.joseonnight.application.character.required;

import java.util.List;
import kr.joseonnight.domain.character.SkillDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, String> {

    List<SkillDefinition> findAllByEnabledTrueOrderByIdAsc();
}
