package kr.joseonnight.application.membersettings;

import java.time.Clock;
import java.time.Instant;
import kr.joseonnight.application.membersettings.provided.MemberSettingsCreator;
import kr.joseonnight.application.membersettings.provided.MemberSettingsFinder;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifyInfo;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifier;
import kr.joseonnight.application.membersettings.provided.MemberSettingsView;
import kr.joseonnight.application.membersettings.required.MemberSettingsRepository;
import kr.joseonnight.domain.membersettings.MemberSettings;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.transaction.annotation.Transactional;

@ValidatedApplicationService
public final class MemberSettingsService
        implements MemberSettingsCreator, MemberSettingsFinder, MemberSettingsModifier {

    private final MemberSettingsRepository settingsRepository;
    private final Clock clock;

    public MemberSettingsService(MemberSettingsRepository settingsRepository, Clock clock) {
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MemberSettingsView create(Long memberId, String nickname) {
        rejectDuplicateNickname(nickname);
        MemberSettings settings = settingsRepository.save(
                MemberSettings.create(memberId, nickname, Instant.now(clock))
        );
        return MemberSettingsView.from(settings);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberSettingsView find(Long memberId) {
        return MemberSettingsView.from(findEntity(memberId));
    }

    @Override
    @Transactional
    public MemberSettingsView modify(Long memberId, MemberSettingsModifyInfo modifyInfo) {
        MemberSettings settings = findEntity(memberId);
        settings.updatePreferences(
                modifyInfo.muted(),
                modifyInfo.musicVolume(),
                modifyInfo.effectsVolume(),
                modifyInfo.targetFps(),
                Instant.now(clock)
        );
        return MemberSettingsView.from(settings);
    }

    private MemberSettings findEntity(Long memberId) {
        return settingsRepository.findByMemberId(memberId)
                .orElseThrow(() -> new MemberSettingsNotFoundException(memberId));
    }

    private void rejectDuplicateNickname(String nickname) {
        if (settingsRepository.existsByNickname(nickname.strip())) {
            throw new DuplicateNicknameException(nickname);
        }
    }
}
