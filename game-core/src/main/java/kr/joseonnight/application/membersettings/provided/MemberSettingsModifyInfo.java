package kr.joseonnight.application.membersettings.provided;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import kr.joseonnight.domain.membersettings.TargetFps;

public record MemberSettingsModifyInfo(
        boolean muted,
        @Min(0) @Max(100) int musicVolume,
        @Min(0) @Max(100) int effectsVolume,
        @NotNull TargetFps targetFps
) {
}
