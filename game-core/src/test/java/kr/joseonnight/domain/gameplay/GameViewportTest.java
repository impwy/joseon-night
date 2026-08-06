package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GameViewportTest {

    @Test
    void acceptsInclusiveLogicalPixelBounds() {
        assertThat(new GameViewport(640, 360)).isNotNull();
        assertThat(new GameViewport(3_840, 2_160)).isNotNull();
    }

    @Test
    void rejectsDimensionsOutsideLogicalPixelBounds() {
        assertThatThrownBy(() -> new GameViewport(639, 720))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("width");
        assertThatThrownBy(() -> new GameViewport(1_280, 2_161))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("height");
    }

    @Test
    void usesTheActualCircleWhenCheckingViewportCorners() {
        GameViewport viewport = new GameViewport(640, 360);

        assertThat(viewport.intersectsCircle(0.0, 0.0, 336.0, 180.0, 17.0)).isTrue();
        assertThat(viewport.intersectsCircle(0.0, 0.0, 336.0, 196.0, 17.0)).isFalse();
    }
}
