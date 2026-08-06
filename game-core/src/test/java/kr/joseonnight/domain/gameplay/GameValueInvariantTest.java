package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GameValueInvariantTest {

    @Test
    void gameRulesRequireFiniteValuesAndPositiveCapacities() {
        assertThatThrownBy(() -> rulesWithPlayerSpeed(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playerSpeed");
        assertThatThrownBy(() -> new GameRules(
                240.0,
                18.0,
                760.0,
                0.75,
                55.0,
                20.0,
                17.0,
                520.0,
                20.0,
                0.65,
                100.0,
                5,
                0,
                160,
                300
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxEnemies");
    }

    @Test
    void itemAndSoundStateRejectInvalidIdentifiersAndLevels() {
        assertThatThrownBy(() -> new ItemState("seal-talisman", "봉인 부적", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level");
        assertThatThrownBy(() -> new SoundEvent(0L, SoundCue.GUARD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    private static GameRules rulesWithPlayerSpeed(double playerSpeed) {
        return new GameRules(
                playerSpeed,
                18.0,
                760.0,
                0.75,
                55.0,
                20.0,
                17.0,
                520.0,
                20.0,
                0.65,
                100.0,
                5,
                220,
                160,
                300
        );
    }
}
