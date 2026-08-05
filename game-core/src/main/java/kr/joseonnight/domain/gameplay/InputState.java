package kr.joseonnight.domain.gameplay;

/**
 * Direction keys held during the next simulation step.
 */
public record InputState(boolean up, boolean down, boolean left, boolean right) {

    public static InputState idle() {
        return new InputState(false, false, false, false);
    }
}
