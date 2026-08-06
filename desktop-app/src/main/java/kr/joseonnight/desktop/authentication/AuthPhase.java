package kr.joseonnight.desktop.authentication;

/** User-visible phases of the desktop browser login flow. */
public enum AuthPhase {
    SIGNED_OUT,
    STARTING_ATTEMPT,
    WAITING_FOR_BROWSER,
    REGISTRATION_REQUIRED,
    AUTHENTICATED,
    EXPIRED,
    FORBIDDEN,
    OFFLINE,
    FAILED
}
