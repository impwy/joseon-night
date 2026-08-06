package kr.joseonnight.application.membersettings.provided;

import jakarta.validation.constraints.Positive;

public interface MemberSettingsFinder {

    MemberSettingsView find(@Positive Long memberId);
}
