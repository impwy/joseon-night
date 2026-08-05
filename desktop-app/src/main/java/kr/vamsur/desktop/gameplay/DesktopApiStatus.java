package kr.vamsur.desktop.gameplay;

import java.util.Objects;

/**
 * Current connection state of the local game-core process.
 */
public record DesktopApiStatus(State state, String message) {

    public DesktopApiStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(message, "message");
    }

    public static DesktopApiStatus connecting() {
        return new DesktopApiStatus(State.CONNECTING, "game-core에 연결하는 중입니다.");
    }

    public static DesktopApiStatus online() {
        return new DesktopApiStatus(State.ONLINE, "game-core 연결됨");
    }

    public static DesktopApiStatus offline(String message) {
        return new DesktopApiStatus(State.OFFLINE, message);
    }

    public enum State {
        CONNECTING,
        ONLINE,
        OFFLINE
    }
}
