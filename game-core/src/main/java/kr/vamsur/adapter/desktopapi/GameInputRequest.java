package kr.vamsur.adapter.desktopapi;

import jakarta.validation.constraints.NotNull;
import kr.vamsur.domain.gameplay.InputState;

/**
 * Keyboard state sent by the desktop client.
 */
public record GameInputRequest(
        @NotNull Boolean up,
        @NotNull Boolean down,
        @NotNull Boolean left,
        @NotNull Boolean right
) {

    InputState toInputState() {
        return new InputState(up, down, left, right);
    }
}
