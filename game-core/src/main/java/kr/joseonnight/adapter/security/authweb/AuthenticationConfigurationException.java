package kr.joseonnight.adapter.security.authweb;

public final class AuthenticationConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthenticationConfigurationException() {
        super("Desktop authentication is not configured");
    }
}
