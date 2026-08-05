package kr.joseonnight.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RankingMetricTest {

    @Test
    void convertsSupportedPathsWithoutDependingOnLetterCase() {
        assertThat(RankingMetric.fromPath("survival")).isEqualTo(RankingMetric.SURVIVAL);
        assertThat(RankingMetric.fromPath("KiLlS")).isEqualTo(RankingMetric.KILLS);
    }

    @Test
    void rejectsUnsupportedPaths() {
        assertThatThrownBy(() -> RankingMetric.fromPath("score"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metric must be survival or kills");
    }
}
