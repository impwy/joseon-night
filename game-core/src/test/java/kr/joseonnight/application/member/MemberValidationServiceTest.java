package kr.joseonnight.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import kr.joseonnight.application.member.required.MemberCharacterRepository;
import kr.joseonnight.application.member.required.MemberItemRepository;
import kr.joseonnight.application.member.required.MemberRepository;
import kr.joseonnight.application.member.required.OAuthIdentityRepository;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.domain.member.OAuthProvider;
import org.junit.jupiter.api.Test;

class MemberValidationServiceTest {

    private static final String SUBJECT_HMAC = "a".repeat(64);

    @Test
    void rejectsAlreadyRegisteredGoogleIdentity() {
        MemberRepository members = mock(MemberRepository.class);
        OAuthIdentityRepository identities = mock(OAuthIdentityRepository.class);
        MemberCharacterRepository characters = mock(MemberCharacterRepository.class);
        MemberItemRepository items = mock(MemberItemRepository.class);
        when(identities.existsByProviderAndSubjectHmac(OAuthProvider.GOOGLE, SUBJECT_HMAC))
                .thenReturn(true);
        MemberValidationService validation =
                new MemberValidationService(members, identities, characters, items);

        assertThatThrownBy(() -> validation.rejectRegisteredGoogleIdentity(SUBJECT_HMAC))
                .isInstanceOf(DuplicateMemberException.class);
    }

    @Test
    void returnsExistingMemberAndRejectsMissingMember() {
        MemberRepository members = mock(MemberRepository.class);
        OAuthIdentityRepository identities = mock(OAuthIdentityRepository.class);
        MemberCharacterRepository characters = mock(MemberCharacterRepository.class);
        MemberItemRepository items = mock(MemberItemRepository.class);
        Member member = Member.register(Instant.parse("2026-08-05T12:00:00Z"));
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(members.findById(2L)).thenReturn(Optional.empty());
        MemberValidationService validation =
                new MemberValidationService(members, identities, characters, items);

        assertThat(validation.requireMember(1L)).isSameAs(member);
        assertThatThrownBy(() -> validation.requireMember(2L))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessageContaining("2");
    }

    @Test
    void reportsOnlyMissingCharacterAndItemUnlocksAsRequired() {
        MemberRepository members = mock(MemberRepository.class);
        OAuthIdentityRepository identities = mock(OAuthIdentityRepository.class);
        MemberCharacterRepository characters = mock(MemberCharacterRepository.class);
        MemberItemRepository items = mock(MemberItemRepository.class);
        when(characters.existsByMemberIdAndCharacterId(1L, "dokkaebi-hunter"))
                .thenReturn(true);
        when(items.existsByMemberIdAndItemId(1L, "seal-talisman"))
                .thenReturn(false);
        MemberValidationService validation =
                new MemberValidationService(members, identities, characters, items);

        assertThat(validation.requiresCharacterUnlock(1L, "dokkaebi-hunter")).isFalse();
        assertThat(validation.requiresItemUnlock(1L, "seal-talisman")).isTrue();
    }
}
