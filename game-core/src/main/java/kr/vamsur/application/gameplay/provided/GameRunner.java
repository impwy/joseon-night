package kr.vamsur.application.gameplay.provided;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import kr.vamsur.domain.gameplay.InputState;
import kr.vamsur.domain.gameplay.UpgradeType;

/**
 * Driving port for running a local game session.
 */
public interface GameRunner {

    void startNewGame();

    void setInput(@Valid @NotNull InputState input);

    void tick(@Positive double deltaSeconds);

    void chooseUpgrade(@NotNull UpgradeType upgradeType);

    GameSnapshot snapshot();
}
