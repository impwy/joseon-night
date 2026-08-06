package kr.joseonnight.domain.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CharacterDefinitionTest {

    @Test
    void definesCharacterWithOtherAggregatesReferencedById() {
        CharacterDefinition character = CharacterDefinition.define(
                "dokkaebi-hunter",
                "도깨비 사냥꾼",
                "봉인 부적으로 싸운다.",
                "protective-barrier",
                "seal-talisman",
                true,
                true
        );

        assertThat(character.getSkillId()).isEqualTo("protective-barrier");
        assertThat(character.getStartingItemId()).isEqualTo("seal-talisman");
        assertThat(character.isUnlockedByDefault()).isTrue();
        assertThat(character.isEnabled()).isTrue();
    }

    @Test
    void rejectsBlankOrOversizedDefinitionFields() {
        assertThatThrownBy(() -> CharacterDefinition.define(
                " ", "이름", "설명", "skill", "item", false, true
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CharacterDefinition.define(
                "id", "이름", "x".repeat(501), "skill", "item", false, true
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skillDefinitionCopiesConfiguration() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("barrierCount", 1);

        SkillDefinition skill = SkillDefinition.define(
                "protective-barrier",
                "호신결계",
                "한 번 충돌을 막는다.",
                configuration,
                true
        );
        configuration.put("barrierCount", 99);

        assertThat(skill.getConfiguration()).containsEntry("barrierCount", 1);
        assertThatThrownBy(() -> skill.getConfiguration().put("duration", 2))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
