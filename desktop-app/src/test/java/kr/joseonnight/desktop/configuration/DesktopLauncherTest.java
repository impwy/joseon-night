package kr.joseonnight.desktop.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

class DesktopLauncherTest {

    @Test
    void selectsTheScreenContainingTheLargestWindowArea() {
        Rectangle2D leftScreen = new Rectangle2D(0, 0, 1_920, 1_080);
        Rectangle2D rightScreen = new Rectangle2D(1_920, 0, 2_560, 1_440);
        Rectangle2D windowMostlyOnRight = new Rectangle2D(1_700, 200, 1_000, 700);

        int selected = DesktopLauncher.largestIntersectionIndex(
                windowMostlyOnRight, List.of(leftScreen, rightScreen));

        assertThat(selected).isEqualTo(1);
    }

    @Test
    void rejectsMissingScreens() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                DesktopLauncher.largestIntersectionIndex(
                        new Rectangle2D(0, 0, 1_280, 720), List.of()));
    }

    @Test
    void windowSizeKeepsTheRenderedViewportInsideTheServerContract() {
        assertThat(DesktopLauncher.MIN_WINDOW_WIDTH).isEqualTo(960.0);
        assertThat(DesktopLauncher.MIN_WINDOW_HEIGHT).isEqualTo(540.0);
        assertThat(DesktopLauncher.MAX_WINDOW_WIDTH).isEqualTo(3_840.0);
        assertThat(DesktopLauncher.MAX_WINDOW_HEIGHT).isEqualTo(2_160.0);
    }
}
