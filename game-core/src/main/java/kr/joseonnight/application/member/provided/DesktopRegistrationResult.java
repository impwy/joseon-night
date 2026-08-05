package kr.joseonnight.application.member.provided;

public record DesktopRegistrationResult(
        IssuedAccessToken accessToken,
        AuthenticatedMember member
) {
}
