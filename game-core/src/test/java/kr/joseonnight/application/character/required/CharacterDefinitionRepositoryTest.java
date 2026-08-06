package kr.joseonnight.application.character.required;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.Map;
import kr.joseonnight.application.item.required.ItemDefinitionRepository;
import kr.joseonnight.domain.character.CharacterDefinition;
import kr.joseonnight.domain.character.SkillDefinition;
import kr.joseonnight.domain.item.ItemCategory;
import kr.joseonnight.domain.item.ItemDefinition;
import kr.joseonnight.support.test.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RepositoryTest
class CharacterDefinitionRepositoryTest {

    @Autowired
    private CharacterDefinitionRepository characterRepository;

    @Autowired
    private SkillDefinitionRepository skillRepository;

    @Autowired
    private ItemDefinitionRepository itemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsACharacterCatalogThroughRequiredPorts() {
        skillRepository.save(SkillDefinition.define(
                "test-moon-shield",
                "월광 방패",
                "달빛으로 한 번의 충돌을 막는다.",
                Map.of("invincibleMillis", 1500),
                true
        ));
        itemRepository.save(ItemDefinition.define(
                "test-moon-blade",
                "월광도",
                "달빛 칼날을 발사한다.",
                ItemCategory.WEAPON,
                5,
                Map.of("damage", 12),
                true
        ));
        entityManager.flush();

        characterRepository.save(CharacterDefinition.define(
                "test-moon-hunter",
                "월광 사냥꾼",
                "월광도와 월광 방패를 사용한다.",
                "test-moon-shield",
                "test-moon-blade",
                false,
                true
        ));
        entityManager.flush();
        entityManager.clear();

        CharacterDefinition reloaded = characterRepository.findById("test-moon-hunter").orElseThrow();
        SkillDefinition skill = skillRepository.findById(reloaded.getSkillId()).orElseThrow();

        assertThat(reloaded.getDisplayName()).isEqualTo("월광 사냥꾼");
        assertThat(reloaded.getStartingItemId()).isEqualTo("test-moon-blade");
        assertThat(reloaded.isUnlockedByDefault()).isFalse();
        assertThat(skill.getConfiguration()).containsEntry("invincibleMillis", 1500);
    }
}
