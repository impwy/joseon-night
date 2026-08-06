package kr.joseonnight.application.membersettings.provided;

import java.time.Instant;
import kr.joseonnight.domain.membersettings.MemberSettings;
import kr.joseonnight.domain.membersettings.TargetFps;

public record MemberSettingsView(
        Long memberId,
        String nickname,
        boolean muted,
        int musicVolume,
        int effectsVolume,
        TargetFps targetFps,
        Instant updatedAt
) {

    public static MemberSettingsView from(MemberSettings settings) {
        return new MemberSettingsView(
                settings.getMemberId(),
                settings.getNickname(),
                settings.isMuted(),
                settings.getMusicVolume(),
                settings.getEffectsVolume(),
                settings.getTargetFps(),
                settings.getUpdatedAt()
        );
    }
}
