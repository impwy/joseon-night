package kr.vamsur.domain.gameplay;

import java.util.List;
import java.util.Objects;

/**
 * Immutable state emitted by the game aggregate without exposing its internals.
 */
public record GameState(
        GamePhase phase,
        double elapsedSeconds,
        double remainingSeconds,
        int level,
        int experience,
        int experienceToNextLevel,
        int killCount,
        EntityState player,
        List<EntityState> enemies,
        List<EntityState> projectiles,
        List<EntityState> soulFlames,
        List<UpgradeType> upgradeChoices
) {

    public GameState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(player, "player");
        enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
        projectiles = List.copyOf(Objects.requireNonNull(projectiles, "projectiles"));
        soulFlames = List.copyOf(Objects.requireNonNull(soulFlames, "soulFlames"));
        upgradeChoices = List.copyOf(Objects.requireNonNull(upgradeChoices, "upgradeChoices"));
    }
}
