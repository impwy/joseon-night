package kr.joseonnight.application.membersettings.provided;

import java.time.Instant;
import kr.joseonnight.domain.membersettings.MemberSettings;

public record MemberSettingsView(
        Long memberId,
        String nickname,
        int masterVolume,
        boolean muted,
        Instant updatedAt
) {

    public static MemberSettingsView from(MemberSettings settings) {
        return new MemberSettingsView(
                settings.getMemberId(),
                settings.getNickname(),
                settings.getMasterVolume(),
                settings.isMuted(),
                settings.getUpdatedAt()
        );
    }
}
