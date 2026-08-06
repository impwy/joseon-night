package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class LightningStrikeStateTest {

    @Test
    void rejectsInvalidValueArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LightningStrikeState(0L, 0.0, 0.0, 0.24, "thunder-bell"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LightningStrikeState(1L, Double.NaN, 0.0, 0.24, "thunder-bell"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LightningStrikeState(1L, 0.0, 0.0, 0.0, "thunder-bell"));
    }
}
