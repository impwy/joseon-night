package kr.vamsur.application.gameplay.provided;

import java.util.List;
import java.util.Objects;
import kr.vamsur.domain.gameplay.GamePhase;
import kr.vamsur.domain.gameplay.UpgradeType;

/**
 * Immutable state DTO published through the driving port.
 */
public record GameSnapshot(
        GamePhase phase,
        double elapsedSeconds,
        double remainingSeconds,
        int level,
        int experience,
        int experienceToNextLevel,
        int killCount,
        EntitySnapshot player,
        List<EntitySnapshot> enemies,
        List<EntitySnapshot> projectiles,
        List<EntitySnapshot> soulFlames,
        List<UpgradeType> upgradeChoices
) {

    public GameSnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(player, "player");
        enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
        projectiles = List.copyOf(Objects.requireNonNull(projectiles, "projectiles"));
        soulFlames = List.copyOf(Objects.requireNonNull(soulFlames, "soulFlames"));
        upgradeChoices = List.copyOf(Objects.requireNonNull(upgradeChoices, "upgradeChoices"));
    }
}
