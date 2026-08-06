package kr.joseonnight.desktop.gameplay;

/**
 * Direction keys sent to the game-core desktop API.
 */
public record InputState(boolean up, boolean down, boolean left, boolean right) {

    public static InputState idle() {
        return new InputState(false, false, false, false);
    }
}
