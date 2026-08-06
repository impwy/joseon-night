package kr.joseonnight.desktop.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DesktopApiClientViewportTest {

    @Test
    void resizeBurstsUseTheSpecifiedDebounceWindow() {
        assertThat(DesktopApiClient.VIEWPORT_DEBOUNCE).isEqualTo(Duration.ofMillis(150));
    }

    @Test
    void desktopViewportMaximumMatchesTheServerContract() {
        assertThat(DesktopApiClient.MAX_VIEWPORT_WIDTH).isEqualTo(3_840);
        assertThat(DesktopApiClient.MAX_VIEWPORT_HEIGHT).isEqualTo(2_160);
    }
}
