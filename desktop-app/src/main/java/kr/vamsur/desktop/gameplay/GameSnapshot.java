package kr.vamsur.desktop.gameplay;

import java.util.List;
import java.util.Objects;

/**
 * Immutable desktop view state returned by game-core.
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
        enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
        projectiles = List.copyOf(Objects.requireNonNull(projectiles, "projectiles"));
        soulFlames = List.copyOf(Objects.requireNonNull(soulFlames, "soulFlames"));
        upgradeChoices = List.copyOf(Objects.requireNonNull(upgradeChoices, "upgradeChoices"));
    }

    public static GameSnapshot lobby() {
        return new GameSnapshot(
                GamePhase.LOBBY,
                0.0,
                300.0,
                1,
                0,
                5,
                0,
                new EntitySnapshot(1L, 0.0, 0.0, 18.0, 0.0),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
