package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberRegistrationInfo(
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String providerSubjectHmac,
        @NotBlank @Size(min = 2, max = 20) String nickname
) {
}
