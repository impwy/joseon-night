package kr.joseonnight.domain.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemLoadoutTest {

    @Test
    void keepsFiveSlotsAndExcludesTheOtherCharactersStartingItem() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        equip(loadout, ItemType.EXORCIST_SWORD);
        equip(loadout, ItemType.RETURNING_BOOMERANG);
        equip(loadout, ItemType.THUNDER_BELL);
        equip(loadout, ItemType.SPIRIT_GOURD);

        assertEquals(5, loadout.occupiedSlots());
        assertFalse(loadout.itemStates().stream()
                .anyMatch(item -> item.itemId().equals(ItemType.FLAME_FAN.id())));
        Set<String> ownedItems = Set.of(
                ItemType.SEAL_TALISMAN.id(),
                ItemType.EXORCIST_SWORD.id(),
                ItemType.RETURNING_BOOMERANG.id(),
                ItemType.THUNDER_BELL.id(),
                ItemType.SPIRIT_GOURD.id());
        assertTrue(loadout.yellowChestOptions(new Random(1L)).stream()
                .allMatch(option -> option.kind() == RewardKind.ITEM
                        && ownedItems.contains(option.targetId())));
    }

    @Test
    void evolutionRequiresTwoLevelFiveMaterialsThenFreesOneSlot() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        levelToFive(loadout, ItemType.SEAL_TALISMAN);
        equip(loadout, ItemType.EXORCIST_SWORD);
        levelToFive(loadout, ItemType.EXORCIST_SWORD);

        RewardOption evolution = loadout.purpleChestOptions(new Random(2L)).stream()
                .filter(option -> option.targetId().equals(
                        EvolutionType.TEN_THOUSAND_SEAL_ARRAY.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, loadout.occupiedSlots());

        loadout.apply(evolution);

        assertEquals(1, loadout.occupiedSlots());
        assertEquals(0, loadout.itemLevel(ItemType.SEAL_TALISMAN));
        assertEquals(0, loadout.itemLevel(ItemType.EXORCIST_SWORD));
        assertTrue(loadout.hasEvolution(EvolutionType.TEN_THOUSAND_SEAL_ARRAY));
    }

    @Test
    void fixedSeedLevelUpsCanPrepareTwoLevelFiveMaterialsForAPurpleEvolution() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        Random random = new Random(20260805L);

        for (int levelUp = 0; levelUp < 100
                && (loadout.itemLevel(ItemType.SEAL_TALISMAN) < ItemLoadout.MAX_ITEM_LEVEL
                || loadout.itemLevel(ItemType.EXORCIST_SWORD) < ItemLoadout.MAX_ITEM_LEVEL);
                levelUp++) {
            List<RewardOption> options = loadout.levelUpOptions(random);
            assertEquals(3, options.size());
            assertEquals(3, options.stream().map(RewardOption::optionId).distinct().count());
            assertTrue(options.stream().anyMatch(option -> option.kind() == RewardKind.UPGRADE));

            options.stream()
                    .filter(option -> option.kind() == RewardKind.ITEM)
                    .filter(option -> option.targetId().equals(ItemType.SEAL_TALISMAN.id())
                            || option.targetId().equals(ItemType.EXORCIST_SWORD.id()))
                    .findFirst()
                    .ifPresent(loadout::apply);
        }

        assertEquals(ItemLoadout.MAX_ITEM_LEVEL, loadout.itemLevel(ItemType.SEAL_TALISMAN));
        assertEquals(ItemLoadout.MAX_ITEM_LEVEL, loadout.itemLevel(ItemType.EXORCIST_SWORD));

        RewardOption evolution = loadout.purpleChestOptions(random).stream()
                .filter(option -> option.targetId().equals(
                        EvolutionType.TEN_THOUSAND_SEAL_ARRAY.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(RewardKind.EVOLUTION, evolution.kind());

        loadout.apply(evolution);

        assertTrue(loadout.hasEvolution(EvolutionType.TEN_THOUSAND_SEAL_ARRAY));
    }

    @Test
    void declaresTheSixFixedEvolutionRecipes() {
        List<List<ItemType>> recipes = List.of(EvolutionType.values()).stream()
                .map(evolution -> List.of(
                        evolution.firstMaterial(),
                        evolution.secondMaterial()))
                .toList();

        assertEquals(List.of(
                List.of(ItemType.SEAL_TALISMAN, ItemType.EXORCIST_SWORD),
                List.of(ItemType.SEAL_TALISMAN, ItemType.THUNDER_BELL),
                List.of(ItemType.FLAME_FAN, ItemType.RETURNING_BOOMERANG),
                List.of(ItemType.FLAME_FAN, ItemType.SPIRIT_GOURD),
                List.of(ItemType.EXORCIST_SWORD, ItemType.RETURNING_BOOMERANG),
                List.of(ItemType.THUNDER_BELL, ItemType.SPIRIT_GOURD)
        ), recipes);
    }

    @Test
    void purpleChestPlacesEligibleEvolutionBeforeYellowFallbacks() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        levelToFive(loadout, ItemType.SEAL_TALISMAN);
        equip(loadout, ItemType.EXORCIST_SWORD);
        levelToFive(loadout, ItemType.EXORCIST_SWORD);

        List<RewardOption> options = loadout.purpleChestOptions(new Random(3L));

        assertEquals(3, options.size());
        assertEquals(RewardKind.EVOLUTION, options.getFirst().kind());
        assertEquals(EvolutionType.TEN_THOUSAND_SEAL_ARRAY.id(), options.getFirst().targetId());
    }

    @Test
    void chestEffectsShareTheYellowPoolButNeverAppearDuringLevelUp() {
        ItemLoadout loadout = fullyLevelledHunterLoadout();

        List<RewardOption> yellowOptions = loadout.yellowChestOptions(
                new Random(11L),
                false,
                true);

        assertEquals(Set.of(
                ChestRewardType.HEART.id(),
                ChestRewardType.MAGNET.id()), yellowOptions.stream()
                .map(RewardOption::targetId)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(yellowOptions.stream()
                .allMatch(option -> option.kind() == RewardKind.CHEST_EFFECT));
        assertTrue(loadout.levelUpOptions(new Random(11L)).stream()
                .noneMatch(option -> option.kind() == RewardKind.CHEST_EFFECT));
    }

    @Test
    void unavailableChestEffectsAreExcludedFromTheCandidatePool() {
        ItemLoadout loadout = fullyLevelledHunterLoadout();

        assertTrue(loadout.yellowChestOptions(new Random(12L), true, false).isEmpty());
    }

    @Test
    void purpleChestKeepsEvolutionsBeforeChestEffectFallbacks() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        levelToFive(loadout, ItemType.SEAL_TALISMAN);
        equip(loadout, ItemType.EXORCIST_SWORD);
        levelToFive(loadout, ItemType.EXORCIST_SWORD);
        equip(loadout, ItemType.RETURNING_BOOMERANG);
        equip(loadout, ItemType.THUNDER_BELL);
        equip(loadout, ItemType.SPIRIT_GOURD);

        List<RewardOption> options = loadout.purpleChestOptions(
                new Random(13L),
                false,
                true);

        assertEquals(3, options.size());
        assertEquals(RewardKind.EVOLUTION, options.getFirst().kind());
        assertEquals(EvolutionType.TEN_THOUSAND_SEAL_ARRAY.id(), options.getFirst().targetId());
        assertEquals(options, loadout.purpleChestOptions(
                new Random(13L),
                false,
                true));

        Set<String> observedFallbacks = new HashSet<>();
        for (int seed = 0; seed < 100; seed++) {
            List<RewardOption> seededOptions = loadout.purpleChestOptions(
                    new Random(seed),
                    false,
                    true);
            assertEquals(RewardKind.EVOLUTION, seededOptions.getFirst().kind());
            seededOptions.subList(1, seededOptions.size()).stream()
                    .map(RewardOption::targetId)
                    .forEach(observedFallbacks::add);
        }
        assertTrue(observedFallbacks.contains(ChestRewardType.HEART.id()));
        assertTrue(observedFallbacks.contains(ChestRewardType.MAGNET.id()));
        assertTrue(observedFallbacks.contains(ItemType.RETURNING_BOOMERANG.id()));
    }

    @Test
    void evolutionMaterialsCannotBeOfferedOrReacquiredAfterConsumption() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        levelToFive(loadout, ItemType.SEAL_TALISMAN);
        equip(loadout, ItemType.EXORCIST_SWORD);
        levelToFive(loadout, ItemType.EXORCIST_SWORD);
        loadout.apply(RewardOption.evolution(EvolutionType.TEN_THOUSAND_SEAL_ARRAY));

        assertTrue(loadout.wasEvolutionMaterialConsumed(ItemType.SEAL_TALISMAN));
        assertTrue(loadout.wasEvolutionMaterialConsumed(ItemType.EXORCIST_SWORD));
        assertTrue(loadout.levelUpOptions(new Random(14L)).stream()
                .noneMatch(ItemLoadoutTest::isConsumedSealArrayMaterial));
        assertTrue(loadout.yellowChestOptions(new Random(14L), false, true).stream()
                .noneMatch(ItemLoadoutTest::isConsumedSealArrayMaterial));
        assertTrue(loadout.purpleChestOptions(new Random(14L), false, true).stream()
                .noneMatch(ItemLoadoutTest::isConsumedSealArrayMaterial));
        assertTrue(loadout.purpleChestOptions(new Random(14L), false, true).stream()
                .noneMatch(option -> option.targetId().equals(
                        EvolutionType.HEAVENLY_THUNDER_SEAL.id())
                        || option.targetId().equals(
                        EvolutionType.LUNAR_ECLIPSE_TWIN_BLADES.id())));
        assertThrows(IllegalStateException.class,
                () -> loadout.apply(RewardOption.item(ItemType.EXORCIST_SWORD, false, 0)));
        assertThrows(IllegalStateException.class,
                () -> loadout.apply(RewardOption.item(ItemType.SEAL_TALISMAN, false, 0)));
    }

    @Test
    void aDisjointSecondEvolutionRemainsEligibleAfterTheFirstEvolution() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        equip(loadout, ItemType.EXORCIST_SWORD);
        levelToFive(loadout, ItemType.EXORCIST_SWORD);
        equip(loadout, ItemType.RETURNING_BOOMERANG);
        levelToFive(loadout, ItemType.RETURNING_BOOMERANG);
        loadout.apply(RewardOption.evolution(EvolutionType.LUNAR_ECLIPSE_TWIN_BLADES));
        levelToFive(loadout, ItemType.SEAL_TALISMAN);
        equip(loadout, ItemType.THUNDER_BELL);
        levelToFive(loadout, ItemType.THUNDER_BELL);

        RewardOption secondEvolution = loadout.purpleChestOptions(
                        new Random(15L),
                        false,
                        false).stream()
                .filter(option -> option.targetId().equals(
                        EvolutionType.HEAVENLY_THUNDER_SEAL.id()))
                .findFirst()
                .orElseThrow();
        loadout.apply(secondEvolution);

        assertIterableEquals(List.of(
                EvolutionType.HEAVENLY_THUNDER_SEAL,
                EvolutionType.LUNAR_ECLIPSE_TWIN_BLADES),
                loadout.equippedEvolutions());
    }

    @Test
    void chestRewardIdsRoundTripAndCannotBeAppliedAsEquipment() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        RewardOption heart = RewardOption.chestEffect(ChestRewardType.HEART);

        assertEquals(ChestRewardType.HEART, ChestRewardType.fromId(heart.targetId()));
        assertEquals("chest-effect:heart", heart.optionId());
        assertEquals(RewardKind.CHEST_EFFECT, heart.kind());
        assertThrows(IllegalArgumentException.class, () -> loadout.apply(heart));
        assertThrows(IllegalArgumentException.class, () -> ChestRewardType.fromId("unknown"));
    }

    @Test
    void equippedAttackSourcesAlwaysFollowEnumDeclarationOrder() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        equip(loadout, ItemType.SPIRIT_GOURD);
        equip(loadout, ItemType.THUNDER_BELL);
        equip(loadout, ItemType.RETURNING_BOOMERANG);
        equip(loadout, ItemType.EXORCIST_SWORD);

        assertIterableEquals(List.of(
                ItemType.SEAL_TALISMAN,
                ItemType.EXORCIST_SWORD,
                ItemType.RETURNING_BOOMERANG,
                ItemType.THUNDER_BELL,
                ItemType.SPIRIT_GOURD), loadout.equippedItems());

        ItemLoadout evolutionLoadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        equip(evolutionLoadout, ItemType.EXORCIST_SWORD);
        levelToFive(evolutionLoadout, ItemType.EXORCIST_SWORD);
        equip(evolutionLoadout, ItemType.RETURNING_BOOMERANG);
        levelToFive(evolutionLoadout, ItemType.RETURNING_BOOMERANG);
        evolutionLoadout.apply(RewardOption.evolution(
                EvolutionType.LUNAR_ECLIPSE_TWIN_BLADES));
        levelToFive(evolutionLoadout, ItemType.SEAL_TALISMAN);
        equip(evolutionLoadout, ItemType.THUNDER_BELL);
        levelToFive(evolutionLoadout, ItemType.THUNDER_BELL);
        evolutionLoadout.apply(RewardOption.evolution(
                EvolutionType.HEAVENLY_THUNDER_SEAL));

        assertIterableEquals(List.of(
                EvolutionType.HEAVENLY_THUNDER_SEAL,
                EvolutionType.LUNAR_ECLIPSE_TWIN_BLADES),
                evolutionLoadout.equippedEvolutions());
    }

    @Test
    void rejectsAnotherCharactersStartingItem() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);

        assertThrows(IllegalArgumentException.class,
                () -> loadout.apply(RewardOption.item(ItemType.FLAME_FAN, false, 0)));
    }

    @Test
    void rejectsAnItemAboveLevelFiveAndAnIneligibleEvolution() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        levelToFive(loadout, ItemType.SEAL_TALISMAN);

        assertThrows(IllegalStateException.class,
                () -> loadout.apply(RewardOption.item(
                        ItemType.SEAL_TALISMAN,
                        true,
                        ItemLoadout.MAX_ITEM_LEVEL
                )));
        assertThrows(IllegalStateException.class,
                () -> loadout.apply(RewardOption.evolution(
                        EvolutionType.TEN_THOUSAND_SEAL_ARRAY
                )));
    }

    private static void equip(ItemLoadout loadout, ItemType item) {
        loadout.apply(RewardOption.item(item, false, 0));
    }

    private static void levelToFive(ItemLoadout loadout, ItemType item) {
        while (loadout.itemLevel(item) < ItemLoadout.MAX_ITEM_LEVEL) {
            loadout.apply(RewardOption.item(item, true, loadout.itemLevel(item)));
        }
    }

    private static ItemLoadout fullyLevelledHunterLoadout() {
        ItemLoadout loadout = new ItemLoadout(CharacterType.DOKKAEBI_HUNTER);
        equip(loadout, ItemType.EXORCIST_SWORD);
        equip(loadout, ItemType.RETURNING_BOOMERANG);
        equip(loadout, ItemType.THUNDER_BELL);
        equip(loadout, ItemType.SPIRIT_GOURD);
        levelToFive(loadout, ItemType.SEAL_TALISMAN);
        levelToFive(loadout, ItemType.EXORCIST_SWORD);
        levelToFive(loadout, ItemType.RETURNING_BOOMERANG);
        levelToFive(loadout, ItemType.THUNDER_BELL);
        levelToFive(loadout, ItemType.SPIRIT_GOURD);
        return loadout;
    }

    private static boolean isConsumedSealArrayMaterial(RewardOption option) {
        return option.targetId().equals(ItemType.SEAL_TALISMAN.id())
                || option.targetId().equals(ItemType.EXORCIST_SWORD.id());
    }
}
