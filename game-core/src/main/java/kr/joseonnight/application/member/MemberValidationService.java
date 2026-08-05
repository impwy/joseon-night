package kr.joseonnight.application.member;

import kr.joseonnight.application.member.required.MemberCharacterRepository;
import kr.joseonnight.application.member.required.MemberItemRepository;
import kr.joseonnight.application.member.required.MemberRepository;
import kr.joseonnight.application.member.required.OAuthIdentityRepository;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.domain.member.OAuthProvider;
import kr.joseonnight.support.stereotype.ApplicationService;
import lombok.RequiredArgsConstructor;

@ApplicationService
@RequiredArgsConstructor
public final class MemberValidationService {

    private final MemberRepository memberRepository;
    private final OAuthIdentityRepository identityRepository;
    private final MemberCharacterRepository characterRepository;
    private final MemberItemRepository itemRepository;

    void rejectRegisteredGoogleIdentity(String providerSubjectHmac) {
        if (identityRepository.existsByProviderAndSubjectHmac(
                OAuthProvider.GOOGLE,
                providerSubjectHmac
        )) {
            throw new DuplicateMemberException();
        }
    }

    Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }

    boolean requiresCharacterUnlock(Long memberId, String characterId) {
        return !characterRepository.existsByMemberIdAndCharacterId(memberId, characterId);
    }

    boolean requiresItemUnlock(Long memberId, String itemId) {
        return !itemRepository.existsByMemberIdAndItemId(memberId, itemId);
    }
}
