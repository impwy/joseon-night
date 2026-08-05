package kr.joseonnight.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import kr.joseonnight.application.character.provided.CharacterCatalog;
import kr.joseonnight.application.character.provided.CharacterCatalogEntry;
import kr.joseonnight.application.character.provided.CharacterCatalogFinder;
import kr.joseonnight.application.character.provided.SkillCatalogEntry;
import kr.joseonnight.application.member.required.MemberCharacterRepository;
import kr.joseonnight.application.member.required.MemberItemRepository;
import kr.joseonnight.domain.member.MemberCharacter;
import org.junit.jupiter.api.Test;

class MemberProgressionServiceTest {

    @Test
    void exposesOnlyOwnedCharactersStillEnabledInTheCatalog() {
        MemberCharacterRepository characters = mock(MemberCharacterRepository.class);
        MemberItemRepository items = mock(MemberItemRepository.class);
        CharacterCatalogFinder catalogFinder = mock(CharacterCatalogFinder.class);
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        when(catalogFinder.findCatalog()).thenReturn(catalog());
        when(characters.findByMemberIdOrderByUnlockedAtAsc(1L)).thenReturn(List.of(
                MemberCharacter.unlock(1L, "dokkaebi-hunter", now),
                MemberCharacter.unlock(1L, "disabled-character", now.plusSeconds(1))
        ));
        var finder = new MemberProgressionService(
                characters,
                items,
                catalogFinder,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(finder.unlockedCharacterIds(1L)).containsExactly("dokkaebi-hunter");
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
