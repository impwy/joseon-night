package kr.joseonnight.desktop.authentication;

import java.net.URI;

/** Immutable authentication state read by JavaFX. */
public record AuthState(
        AuthPhase phase,
        String message,
        URI authorizationUri,
        String registrationTicket,
        AuthSession session) {

    public static AuthState signedOut() {
        return new AuthState(AuthPhase.SIGNED_OUT, "로그인이 필요합니다.", null, null, null);
    }
}
