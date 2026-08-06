package kr.joseonnight.application.membersettings.provided;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public interface MemberSettingsCreator {

    MemberSettingsView create(
            @Positive Long memberId,
            @NotBlank @Size(min = 2, max = 20) String nickname
    );
}
