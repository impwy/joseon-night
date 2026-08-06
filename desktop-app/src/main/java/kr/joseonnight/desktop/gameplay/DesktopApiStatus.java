package kr.joseonnight.desktop.gameplay;

import java.util.Objects;

/**
 * Current connection state of the remote Armeria game socket.
 */
public record DesktopApiStatus(State state, String message) {

    public DesktopApiStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(message, "message");
    }

    public static DesktopApiStatus connecting() {
        return new DesktopApiStatus(State.CONNECTING, "게임 서버에 연결하는 중입니다.");
    }

    public static DesktopApiStatus reconnecting() {
        return new DesktopApiStatus(State.CONNECTING, "연결이 끊겨 게임에 다시 접속하는 중입니다.");
    }

    public static DesktopApiStatus online() {
        return new DesktopApiStatus(State.ONLINE, "게임 서버 연결됨");
    }

    public static DesktopApiStatus offline(String message) {
        return new DesktopApiStatus(State.OFFLINE, message);
    }

    public static DesktopApiStatus authenticationExpired() {
        return new DesktopApiStatus(State.AUTHENTICATION_EXPIRED, "로그인이 만료되었습니다.");
    }

    public static DesktopApiStatus forbidden() {
        return new DesktopApiStatus(State.FORBIDDEN, "게임 서버 접속 권한이 없습니다.");
    }

    public static DesktopApiStatus error(String message) {
        return new DesktopApiStatus(State.ERROR, message);
    }

    public enum State {
        CONNECTING,
        ONLINE,
        OFFLINE,
        AUTHENTICATION_EXPIRED,
        FORBIDDEN,
        ERROR
    }
}
