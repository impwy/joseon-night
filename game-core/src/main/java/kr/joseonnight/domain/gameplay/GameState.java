package kr.joseonnight.domain.gameplay;

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
        CharacterType character,
        boolean barrierAvailable,
        double invulnerabilityRemainingSeconds,
        EntityState player,
        List<EntityState> enemies,
        List<EntityState> projectiles,
        List<EntityState> soulFlames,
        List<UpgradeType> upgradeChoices,
        List<ItemState> items,
        List<EvolutionType> evolutions,
        int occupiedItemSlots,
        List<ChestState> chests,
        List<DirectionIndicatorState> chestIndicators,
        List<RewardOption> levelUpOptions,
        List<RewardOption> chestRewardOptions,
        List<SoundEvent> soundEvents
) {

    public GameState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(player, "player");
        enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
        projectiles = List.copyOf(Objects.requireNonNull(projectiles, "projectiles"));
        soulFlames = List.copyOf(Objects.requireNonNull(soulFlames, "soulFlames"));
        upgradeChoices = List.copyOf(Objects.requireNonNull(upgradeChoices, "upgradeChoices"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        evolutions = List.copyOf(Objects.requireNonNull(evolutions, "evolutions"));
        chests = List.copyOf(Objects.requireNonNull(chests, "chests"));
        chestIndicators = List.copyOf(Objects.requireNonNull(chestIndicators, "chestIndicators"));
        levelUpOptions = List.copyOf(Objects.requireNonNull(levelUpOptions, "levelUpOptions"));
        chestRewardOptions = List.copyOf(Objects.requireNonNull(chestRewardOptions, "chestRewardOptions"));
        soundEvents = List.copyOf(Objects.requireNonNull(soundEvents, "soundEvents"));
    }
}
