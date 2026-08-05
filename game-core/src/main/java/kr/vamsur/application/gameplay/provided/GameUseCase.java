package kr.vamsur.application.gameplay.provided;

import kr.vamsur.domain.gameplay.InputState;
import kr.vamsur.domain.gameplay.UpgradeType;

/**
 * Driving port for a local game session.
 */
public interface GameUseCase {

    void startNewGame();

    void setInput(InputState input);

    void tick(double deltaSeconds);

    void chooseUpgrade(UpgradeType upgradeType);

    GameSnapshot snapshot();
}
