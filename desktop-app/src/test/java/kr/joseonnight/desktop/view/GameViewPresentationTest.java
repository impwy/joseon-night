package kr.joseonnight.desktop.view;

import static org.assertj.core.api.Assertions.assertThat;

import kr.joseonnight.desktop.gameplay.ChestSnapshot;
import org.junit.jupiter.api.Test;

class GameViewPresentationTest {

    @Test
    void elapsedTimeIsPresentedAsSurvivalTimeWithoutAFiveMinuteLimit() {
        assertThat(GameView.survivalTimeText(301.9)).isEqualTo("생존 시간  05:01");
        assertThat(GameView.survivalTimeText(3_661.2)).isEqualTo("생존 시간  61:01");
    }

    @Test
    void chestIndicatorIsNeededOnlyWhenTheWholeChestIsOutsideTheCanvas() {
        ChestSnapshot partiallyVisible = new ChestSnapshot(1L, "YELLOW", 500.0, 0.0, false);
        ChestSnapshot outside = new ChestSnapshot(2L, "PURPLE", 520.1, 0.0, false);
        ChestSnapshot opened = new ChestSnapshot(3L, "YELLOW", 800.0, 0.0, true);

        assertThat(GameView.isChestOutsideViewport(
                partiallyVisible, 960.0, 540.0, 0.0, 0.0)).isFalse();
        assertThat(GameView.isChestOutsideViewport(
                outside, 960.0, 540.0, 0.0, 0.0)).isTrue();
        assertThat(GameView.isChestOutsideViewport(
                opened, 960.0, 540.0, 0.0, 0.0)).isFalse();
    }
}
