package kr.joseonnight.application.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.joseonnight.application.character.provided.CharacterCatalog;
import kr.joseonnight.application.character.provided.CharacterCatalogEntry;
import kr.joseonnight.application.character.provided.SkillCatalogEntry;
import kr.joseonnight.application.character.required.CharacterCatalogCache;
import kr.joseonnight.application.character.required.CharacterDefinitionRepository;
import kr.joseonnight.application.character.required.SkillDefinitionRepository;
import kr.joseonnight.domain.character.CharacterDefinition;
import kr.joseonnight.domain.character.SkillDefinition;
import org.junit.jupiter.api.Test;

class CharacterCatalogServiceTest {

    @Test
    void returnsCachedCatalogWithoutReadingPostgreSql() {
        CharacterDefinitionRepository characters = mock(CharacterDefinitionRepository.class);
        SkillDefinitionRepository skills = mock(SkillDefinitionRepository.class);
        CharacterCatalogCache cache = mock(CharacterCatalogCache.class);
        CharacterCatalog cached = catalog();
        when(cache.find()).thenReturn(Optional.of(cached));

        var finder = new CharacterCatalogService(characters, skills, cache);

        assertThat(finder.findCatalog()).isSameAs(cached);
        verifyNoInteractions(characters, skills);
    }

    @Test
    void loadsPostgreSqlAndPopulatesCacheOnMiss() {
        CharacterDefinitionRepository characters = mock(CharacterDefinitionRepository.class);
        SkillDefinitionRepository skills = mock(SkillDefinitionRepository.class);
        CharacterCatalogCache cache = mock(CharacterCatalogCache.class);
        when(cache.find()).thenReturn(Optional.empty());
        when(skills.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(SkillDefinition.define(
                "protective-barrier",
                "호신결계",
                "한 번 충돌을 막는다.",
                Map.of("invincibleMillis", 2000),
                true
        )));
        when(characters.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(CharacterDefinition.define(
                "dokkaebi-hunter",
                "도깨비 사냥꾼",
                "봉인 부적으로 싸운다.",
                "protective-barrier",
                "seal-talisman",
                true,
                true
        )));

        var finder = new CharacterCatalogService(characters, skills, cache);
        CharacterCatalog loaded = finder.findCatalog();

        assertThat(loaded.characters()).singleElement().satisfies(character -> {
            assertThat(character.id()).isEqualTo("dokkaebi-hunter");
            assertThat(character.skill().configuration()).containsEntry("invincibleMillis", 2000);
        });
        verify(cache).put(loaded);
    }

    private static CharacterCatalog catalog() {
        return new CharacterCatalog(List.of(new CharacterCatalogEntry(
                "dokkaebi-hunter",
                "도깨비 사냥꾼",
                "봉인 부적으로 싸운다.",
                new SkillCatalogEntry("protective-barrier", "호신결계", "한 번 막는다.", Map.of()),
                "seal-talisman",
                true
        )));
    }
}
