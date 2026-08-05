package kr.joseonnight.application.membersettings.provided;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public interface MemberSettingsModifier {

    MemberSettingsView modify(
            @Positive Long memberId,
            @Valid @NotNull MemberSettingsModifyInfo modifyInfo
    );
}
