package kr.joseonnight.application.member.provided;

import kr.joseonnight.application.member.required.DesktopLoginStatus;

public record DesktopLoginExchange(
        DesktopLoginStatus status,
        String registrationToken,
        IssuedAccessToken accessToken,
        AuthenticatedMember member
) {
}
