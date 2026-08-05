package kr.joseonnight.application.membersettings;

import kr.joseonnight.application.membersettings.required.MemberSettingsRepository;
import kr.joseonnight.domain.membersettings.MemberSettings;
import kr.joseonnight.support.stereotype.ApplicationService;
import lombok.RequiredArgsConstructor;

@ApplicationService
@RequiredArgsConstructor
public final class MemberSettingsValidationService {

    private final MemberSettingsRepository settingsRepository;

    void rejectDuplicateNickname(String nickname) {
        if (settingsRepository.existsByNickname(nickname.strip())) {
            throw new DuplicateNicknameException(nickname);
        }
    }

    MemberSettings requireSettings(Long memberId) {
        return settingsRepository.findByMemberId(memberId)
                .orElseThrow(() -> new MemberSettingsNotFoundException(memberId));
    }
}
