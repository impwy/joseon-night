package kr.joseonnight.domain.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static void equip(ItemLoadout loadout, ItemType item) {
        loadout.apply(RewardOption.item(item, false, 0));
    }

    private static void levelToFive(ItemLoadout loadout, ItemType item) {
        while (loadout.itemLevel(item) < ItemLoadout.MAX_ITEM_LEVEL) {
            loadout.apply(RewardOption.item(item, true, loadout.itemLevel(item)));
        }
    }
}
