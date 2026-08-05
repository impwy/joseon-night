package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public interface MemberAuthenticator {

    MemberAuthentication authenticate(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String providerSubjectHmac
    );
}
