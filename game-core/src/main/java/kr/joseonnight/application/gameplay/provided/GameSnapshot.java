package kr.joseonnight.application.gameplay.provided;

import java.util.List;
import java.util.Objects;
import kr.joseonnight.domain.gameplay.CharacterType;
import kr.joseonnight.domain.gameplay.ChestState;
import kr.joseonnight.domain.gameplay.DirectionIndicatorState;
import kr.joseonnight.domain.gameplay.EvolutionType;
import kr.joseonnight.domain.gameplay.GamePhase;
import kr.joseonnight.domain.gameplay.ItemState;
import kr.joseonnight.domain.gameplay.RewardOption;
import kr.joseonnight.domain.gameplay.SoundEvent;
import kr.joseonnight.domain.gameplay.UpgradeType;

/**
 * Immutable state DTO published through the driving port.
 */
public record GameSnapshot(
        GamePhase phase,
        boolean paused,
        double elapsedSeconds,
        int level,
        int experience,
        int experienceToNextLevel,
        int killCount,
        CharacterType character,
        boolean barrierAvailable,
        double invulnerabilityRemainingSeconds,
        EntitySnapshot player,
        List<EntitySnapshot> enemies,
        List<EntitySnapshot> projectiles,
        List<LightningStrikeSnapshot> lightningStrikes,
        List<EntitySnapshot> soulFlames,
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

    public GameSnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(player, "player");
        enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
        projectiles = List.copyOf(Objects.requireNonNull(projectiles, "projectiles"));
        lightningStrikes = List.copyOf(Objects.requireNonNull(lightningStrikes, "lightningStrikes"));
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
