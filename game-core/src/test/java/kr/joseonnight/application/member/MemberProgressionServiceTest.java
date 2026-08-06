package kr.joseonnight.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import kr.joseonnight.application.member.required.MemberRepository;
import kr.joseonnight.application.member.required.OAuthIdentityRepository;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.domain.member.MemberCharacter;
import kr.joseonnight.domain.member.MemberItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberProgressionServiceTest {

    @Test
    void exposesOnlyOwnedCharactersStillEnabledInTheCatalog() {
        MemberCharacterRepository characters = mock(MemberCharacterRepository.class);
        MemberItemRepository items = mock(MemberItemRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        OAuthIdentityRepository identities = mock(OAuthIdentityRepository.class);
        CharacterCatalogFinder catalogFinder = mock(CharacterCatalogFinder.class);
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        Member member = persistedMember(1L, now);
        when(catalogFinder.findCatalog()).thenReturn(catalog());
        when(members.findById(1L)).thenReturn(java.util.Optional.of(member));
        when(characters.findByMemberIdOrderByUnlockedAtAsc(1L)).thenReturn(List.of(
                member.unlockCharacter("dokkaebi-hunter", now),
                member.unlockCharacter("disabled-character", now.plusSeconds(1))
        ));
        var finder = new MemberProgressionService(
                characters,
                items,
                catalogFinder,
                new MemberValidationService(members, identities, characters, items),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(finder.unlockedCharacterIds(1L)).containsExactly("dokkaebi-hunter");
    }

    @Test
    void initializesOnlyProgressionEntriesThatDoNotAlreadyExist() {
        MemberCharacterRepository characters = mock(MemberCharacterRepository.class);
        MemberItemRepository items = mock(MemberItemRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        OAuthIdentityRepository identities = mock(OAuthIdentityRepository.class);
        CharacterCatalogFinder catalogFinder = mock(CharacterCatalogFinder.class);
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        Member member = persistedMember(1L, now);
        when(catalogFinder.findCatalog()).thenReturn(catalog());
        when(members.findById(1L)).thenReturn(java.util.Optional.of(member));
        when(characters.existsByMemberIdAndCharacterId(1L, "dokkaebi-hunter"))
                .thenReturn(true);
        when(items.existsByMemberIdAndItemId(1L, "seal-talisman"))
                .thenReturn(false);
        var progression = new MemberProgressionService(
                characters,
                items,
                catalogFinder,
                new MemberValidationService(members, identities, characters, items),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        progression.initializeNewMember(1L);

        verify(characters, never()).save(any(MemberCharacter.class));
        verify(items).save(any(MemberItem.class));
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

    private static Member persistedMember(Long id, Instant now) {
        Member member = Member.register(now);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
