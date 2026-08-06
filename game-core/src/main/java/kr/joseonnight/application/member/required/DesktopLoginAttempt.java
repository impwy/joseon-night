package kr.joseonnight.application.member.required;

import java.time.Instant;
import java.util.UUID;

public record DesktopLoginAttempt(
        UUID id,
        String pollTokenHash,
        String browserLaunchTokenHash,
        Instant expiresAt,
        DesktopLoginStatus status,
        String providerSubjectHmac,
        String registrationToken,
        Long memberId,
        String nickname
) {

    public DesktopLoginAttempt withRegistration(String subjectHmac, String token) {
        return new DesktopLoginAttempt(
                id, pollTokenHash, null, expiresAt,
                DesktopLoginStatus.NICKNAME_REQUIRED,
                subjectHmac, token, null, null
        );
    }

    public DesktopLoginAttempt withAuthentication(Long authenticatedMemberId, String authenticatedNickname) {
        return new DesktopLoginAttempt(
                id, pollTokenHash, null, expiresAt,
                DesktopLoginStatus.AUTHENTICATED,
                providerSubjectHmac,
                null,
                authenticatedMemberId,
                authenticatedNickname
        );
    }

    public DesktopLoginAttempt withExchanged() {
        return new DesktopLoginAttempt(
                id, pollTokenHash, null, expiresAt,
                DesktopLoginStatus.EXCHANGED,
                providerSubjectHmac,
                null,
                memberId,
                nickname
        );
    }

    public DesktopLoginAttempt withoutBrowserLaunchToken() {
        return new DesktopLoginAttempt(
                id, pollTokenHash, null, expiresAt, status,
                providerSubjectHmac, registrationToken, memberId, nickname
        );
    }

    public DesktopLoginAttempt withFailure() {
        return new DesktopLoginAttempt(
                id, pollTokenHash, null, expiresAt, DesktopLoginStatus.FAILED,
                null, null, null, null
        );
    }
}
