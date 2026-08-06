package kr.joseonnight.desktop.view;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.input.KeyCode;
import kr.joseonnight.desktop.gameplay.InputState;
import org.junit.jupiter.api.Test;

class GameViewKeyStateTest {

    @Test
    void ordinaryInputClearPreservesEscapeLatchDuringKeyRepeat() {
        GameView.KeyState keyState = new GameView.KeyState();

        assertThat(keyState.updateEscape(true)).isTrue();
        keyState.updateMovement(KeyCode.W, true);

        keyState.clearMovement();

        assertThat(keyState.input()).isEqualTo(InputState.idle());
        assertThat(keyState.updateEscape(true)).isFalse();
    }

    @Test
    void focusLossClearReleasesEscapeLatchForTheNextPress() {
        GameView.KeyState keyState = new GameView.KeyState();
        assertThat(keyState.updateEscape(true)).isTrue();
        keyState.updateMovement(KeyCode.D, true);

        keyState.clearAfterFocusLoss();

        assertThat(keyState.input()).isEqualTo(InputState.idle());
        assertThat(keyState.updateEscape(true)).isTrue();
    }
}
