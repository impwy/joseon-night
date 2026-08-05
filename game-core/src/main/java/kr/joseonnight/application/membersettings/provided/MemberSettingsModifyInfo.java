package kr.joseonnight.application.membersettings.provided;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MemberSettingsModifyInfo(
        boolean muted,
        @Min(0) @Max(100) int masterVolume
) {
}
