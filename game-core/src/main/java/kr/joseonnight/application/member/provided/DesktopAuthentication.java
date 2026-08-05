package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public interface DesktopAuthentication {

    DesktopLoginStart start();

    UUID beginBrowserLogin(@NotBlank String browserLaunchToken);

    void complete(@NotNull UUID attemptId, @NotBlank String providerSubjectHmac);

    void fail(@NotNull UUID attemptId);

    DesktopLoginExchange exchange(@NotNull UUID attemptId, @NotBlank String pollToken);

    DesktopRegistrationResult register(
            @NotBlank String registrationToken,
            @NotBlank @Size(min = 2, max = 20) String nickname
    );
}
