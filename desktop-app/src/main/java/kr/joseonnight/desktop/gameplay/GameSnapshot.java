package kr.joseonnight.desktop.gameplay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;
import java.util.Objects;

/**
 * Immutable desktop view state returned by game-core.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "The compact constructor replaces every list with an immutable List.copyOf value.")
public record GameSnapshot(
        GamePhase phase,
        double elapsedSeconds,
        boolean paused,
        int level,
        int experience,
        int experienceToNextLevel,
        int killCount,
        EntitySnapshot player,
        List<EntitySnapshot> enemies,
        List<EntitySnapshot> projectiles,
        List<LightningStrikeSnapshot> lightningStrikes,
        List<EntitySnapshot> soulFlames,
        List<UpgradeType> upgradeChoices,
        @JsonAlias("character") String characterId,
        boolean barrierAvailable,
        double invulnerabilityRemainingSeconds,
        @JsonAlias("items") List<ItemSlotSnapshot> itemSlots,
        List<String> evolutions,
        int occupiedItemSlots,
        List<ChestSnapshot> chests,
        List<ChestIndicatorSnapshot> chestIndicators,
        @JsonAlias("levelUpOptions") List<RewardOptionSnapshot> pendingLevelUpOptions,
        @JsonAlias("chestRewardOptions") List<RewardOptionSnapshot> pendingChestOptions,
        List<SoundEventSnapshot> soundEvents
) {

    public GameSnapshot {
        Objects.requireNonNull(phase, "phase");
        enemies = immutableOrEmpty(enemies);
        projectiles = immutableOrEmpty(projectiles);
        lightningStrikes = immutableOrEmpty(lightningStrikes);
        soulFlames = immutableOrEmpty(soulFlames);
        upgradeChoices = immutableOrEmpty(upgradeChoices);
        itemSlots = immutableOrEmpty(itemSlots);
        evolutions = immutableOrEmpty(evolutions);
        chests = immutableOrEmpty(chests);
        chestIndicators = immutableOrEmpty(chestIndicators);
        pendingLevelUpOptions = immutableOrEmpty(pendingLevelUpOptions);
        pendingChestOptions = immutableOrEmpty(pendingChestOptions);
        soundEvents = immutableOrEmpty(soundEvents);
    }

    /** Compatibility constructor for the original vertical-slice snapshot. */
    public GameSnapshot(
            GamePhase phase,
            double elapsedSeconds,
            int level,
            int experience,
            int experienceToNextLevel,
            int killCount,
            EntitySnapshot player,
            List<EntitySnapshot> enemies,
            List<EntitySnapshot> projectiles,
            List<EntitySnapshot> soulFlames,
            List<UpgradeType> upgradeChoices) {
        this(
                phase,
                elapsedSeconds,
                false,
                level,
                experience,
                experienceToNextLevel,
                killCount,
                player,
                enemies,
                projectiles,
                List.of(),
                soulFlames,
                upgradeChoices,
                null,
                false,
                0.0,
                List.of(),
                List.of(),
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public static GameSnapshot lobby() {
        return new GameSnapshot(
                GamePhase.LOBBY,
                0.0,
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

    private static <T> List<T> immutableOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
