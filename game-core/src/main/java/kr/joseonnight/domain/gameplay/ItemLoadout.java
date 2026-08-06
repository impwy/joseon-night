package kr.joseonnight.domain.gameplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.springframework.util.Assert;

/**
 * Owns the five-slot item and evolution invariants for one run.
 */
final class ItemLoadout {
    static final int MAX_SLOTS = 5;
    static final int MAX_ITEM_LEVEL = 5;
    private static final int MAX_OPTIONS = 3;
    private static final Set<ItemType> CHARACTER_STARTING_ITEMS = EnumSet.of(
            ItemType.SEAL_TALISMAN,
            ItemType.FLAME_FAN);

    private final Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
    private final Set<EvolutionType> evolutions = EnumSet.noneOf(EvolutionType.class);
    private final Set<ItemType> consumedEvolutionMaterials = EnumSet.noneOf(ItemType.class);

    ItemLoadout(CharacterType character) {
        items.put(Objects.requireNonNull(character, "character").startingItem(), 1);
    }

    List<RewardOption> yellowChestOptions(Random random) {
        return yellowChestOptions(random, false, false);
    }

    List<RewardOption> yellowChestOptions(
            Random random,
            boolean heartAvailable,
            boolean soulFlamesPresent
    ) {
        return shuffledLimit(chestFallbackCandidates(heartAvailable, soulFlamesPresent), random);
    }

    /**
     * Builds a level-up choice set that always keeps one general upgrade available while making
     * normal item acquisition and levelling possible without relying on treasure chests.
     */
    List<RewardOption> levelUpOptions(Random random) {
        Objects.requireNonNull(random, "random");
        List<RewardOption> itemCandidates = itemCandidates();
        shuffle(itemCandidates, random);

        List<RewardOption> upgradeCandidates = new ArrayList<>();
        for (UpgradeType upgrade : UpgradeType.values()) {
            upgradeCandidates.add(RewardOption.upgrade(upgrade));
        }
        shuffle(upgradeCandidates, random);

        List<RewardOption> choices = new ArrayList<>(MAX_OPTIONS);
        choices.add(upgradeCandidates.getFirst());
        for (RewardOption option : itemCandidates) {
            if (choices.size() == MAX_OPTIONS) {
                break;
            }
            choices.add(option);
        }
        for (RewardOption option : upgradeCandidates.subList(1, upgradeCandidates.size())) {
            if (choices.size() == MAX_OPTIONS) {
                break;
            }
            choices.add(option);
        }
        shuffle(choices, random);
        return List.copyOf(choices);
    }

    List<RewardOption> purpleChestOptions(Random random) {
        return purpleChestOptions(random, false, false);
    }

    List<RewardOption> purpleChestOptions(
            Random random,
            boolean heartAvailable,
            boolean soulFlamesPresent
    ) {
        List<RewardOption> candidates = new ArrayList<>();
        for (EvolutionType evolution : EvolutionType.values()) {
            if (canEvolve(evolution)) {
                candidates.add(RewardOption.evolution(evolution));
            }
        }
        if (candidates.size() < MAX_OPTIONS) {
            List<RewardOption> fallback = chestFallbackCandidates(
                    heartAvailable,
                    soulFlamesPresent);
            shuffle(fallback, random);
            for (RewardOption option : fallback) {
                if (candidates.size() == MAX_OPTIONS) {
                    break;
                }
                candidates.add(option);
            }
        }
        if (candidates.size() > MAX_OPTIONS) {
            shuffle(candidates, random);
            return List.copyOf(candidates.subList(0, MAX_OPTIONS));
        }
        return List.copyOf(candidates);
    }

    void apply(RewardOption option) {
        Objects.requireNonNull(option, "option");
        switch (option.kind()) {
            case ITEM -> applyItem(ItemType.fromId(option.targetId()));
            case EVOLUTION -> applyEvolution(EvolutionType.fromId(option.targetId()));
            case UPGRADE, CHEST_EFFECT -> throw new IllegalArgumentException(
                    "The reward does not belong to the item loadout: " + option.kind());
        }
    }

    List<ItemState> itemStates() {
        return items.entrySet().stream()
                .map(entry -> new ItemState(
                        entry.getKey().id(),
                        entry.getKey().displayName(),
                        entry.getValue()))
                .toList();
    }

    List<EvolutionType> evolutionStates() {
        return List.copyOf(evolutions);
    }

    int occupiedSlots() {
        return items.size() + evolutions.size();
    }

    int itemLevel(ItemType item) {
        return items.getOrDefault(item, 0);
    }

    boolean hasEvolution(EvolutionType evolution) {
        return evolutions.contains(evolution);
    }

    Set<ItemType> equippedItems() {
        EnumSet<ItemType> orderedItems = EnumSet.noneOf(ItemType.class);
        orderedItems.addAll(items.keySet());
        return Collections.unmodifiableSet(orderedItems);
    }

    Set<EvolutionType> equippedEvolutions() {
        EnumSet<EvolutionType> orderedEvolutions = EnumSet.noneOf(EvolutionType.class);
        orderedEvolutions.addAll(evolutions);
        return Collections.unmodifiableSet(orderedEvolutions);
    }

    boolean wasEvolutionMaterialConsumed(ItemType item) {
        return consumedEvolutionMaterials.contains(Objects.requireNonNull(item, "item"));
    }

    private List<RewardOption> itemCandidates() {
        List<RewardOption> candidates = new ArrayList<>();
        for (Map.Entry<ItemType, Integer> entry : items.entrySet()) {
            if (entry.getValue() < MAX_ITEM_LEVEL
                    && !consumedEvolutionMaterials.contains(entry.getKey())) {
                candidates.add(RewardOption.item(entry.getKey(), true, entry.getValue()));
            }
        }
        if (occupiedSlots() < MAX_SLOTS) {
            for (ItemType item : ItemType.values()) {
                if (!CHARACTER_STARTING_ITEMS.contains(item)
                        && !items.containsKey(item)
                        && !consumedEvolutionMaterials.contains(item)) {
                    candidates.add(RewardOption.item(item, false, 0));
                }
            }
        }
        return candidates;
    }

    private List<RewardOption> chestFallbackCandidates(
            boolean heartAvailable,
            boolean soulFlamesPresent
    ) {
        List<RewardOption> candidates = itemCandidates();
        if (!heartAvailable) {
            candidates.add(RewardOption.chestEffect(ChestRewardType.HEART));
        }
        if (soulFlamesPresent) {
            candidates.add(RewardOption.chestEffect(ChestRewardType.MAGNET));
        }
        return candidates;
    }

    private boolean canEvolve(EvolutionType evolution) {
        return itemLevel(evolution.firstMaterial()) == MAX_ITEM_LEVEL
                && itemLevel(evolution.secondMaterial()) == MAX_ITEM_LEVEL
                && !consumedEvolutionMaterials.contains(evolution.firstMaterial())
                && !consumedEvolutionMaterials.contains(evolution.secondMaterial())
                && !evolutions.contains(evolution);
    }

    private void applyItem(ItemType item) {
        Assert.state(!consumedEvolutionMaterials.contains(item),
                () -> "An evolution material cannot be reacquired: " + item.id());
        Integer currentLevel = items.get(item);
        if (currentLevel == null) {
            Assert.isTrue(
                    !CHARACTER_STARTING_ITEMS.contains(item),
                    "Another character's starting item cannot be acquired"
            );
            Assert.state(occupiedSlots() < MAX_SLOTS, "All item slots are occupied");
            items.put(item, 1);
            return;
        }
        Assert.state(currentLevel < MAX_ITEM_LEVEL, "The item is already level five");
        items.put(item, currentLevel + 1);
    }

    private void applyEvolution(EvolutionType evolution) {
        Assert.state(canEvolve(evolution),
                () -> "Evolution requirements are not met: " + evolution.id());
        items.remove(evolution.firstMaterial());
        items.remove(evolution.secondMaterial());
        consumedEvolutionMaterials.add(evolution.firstMaterial());
        consumedEvolutionMaterials.add(evolution.secondMaterial());
        evolutions.add(evolution);
    }

    private static List<RewardOption> shuffledLimit(List<RewardOption> candidates, Random random) {
        shuffle(candidates, random);
        return List.copyOf(candidates.subList(0, Math.min(MAX_OPTIONS, candidates.size())));
    }

    private static void shuffle(List<?> values, Random random) {
        Objects.requireNonNull(random, "random");
        for (int index = values.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            Collections.swap(values, index, swapIndex);
        }
    }
}
