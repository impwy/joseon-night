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

    ItemLoadout(CharacterType character) {
        items.put(Objects.requireNonNull(character, "character").startingItem(), 1);
    }

    List<RewardOption> yellowChestOptions(Random random) {
        return shuffledLimit(yellowCandidates(), random);
    }

    /**
     * Builds a level-up choice set that always keeps one general upgrade available while making
     * normal item acquisition and levelling possible without relying on treasure chests.
     */
    List<RewardOption> levelUpOptions(Random random) {
        Objects.requireNonNull(random, "random");
        List<RewardOption> itemCandidates = yellowCandidates();
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
        List<RewardOption> candidates = new ArrayList<>();
        for (EvolutionType evolution : EvolutionType.values()) {
            if (canEvolve(evolution)) {
                candidates.add(RewardOption.evolution(evolution));
            }
        }
        if (candidates.size() < MAX_OPTIONS) {
            List<RewardOption> fallback = yellowCandidates();
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
            case UPGRADE -> throw new IllegalArgumentException(
                    "A general upgrade is not an item-loadout reward");
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
        return Set.copyOf(items.keySet());
    }

    Set<EvolutionType> equippedEvolutions() {
        return Set.copyOf(evolutions);
    }

    private List<RewardOption> yellowCandidates() {
        List<RewardOption> candidates = new ArrayList<>();
        for (Map.Entry<ItemType, Integer> entry : items.entrySet()) {
            if (entry.getValue() < MAX_ITEM_LEVEL) {
                candidates.add(RewardOption.item(entry.getKey(), true, entry.getValue()));
            }
        }
        if (occupiedSlots() < MAX_SLOTS) {
            for (ItemType item : ItemType.values()) {
                if (!CHARACTER_STARTING_ITEMS.contains(item) && !items.containsKey(item)) {
                    candidates.add(RewardOption.item(item, false, 0));
                }
            }
        }
        return candidates;
    }

    private boolean canEvolve(EvolutionType evolution) {
        return itemLevel(evolution.firstMaterial()) == MAX_ITEM_LEVEL
                && itemLevel(evolution.secondMaterial()) == MAX_ITEM_LEVEL
                && !evolutions.contains(evolution);
    }

    private void applyItem(ItemType item) {
        Integer currentLevel = items.get(item);
        if (currentLevel == null) {
            if (CHARACTER_STARTING_ITEMS.contains(item)) {
                throw new IllegalArgumentException("Another character's starting item cannot be acquired");
            }
            if (occupiedSlots() >= MAX_SLOTS) {
                throw new IllegalStateException("All item slots are occupied");
            }
            items.put(item, 1);
            return;
        }
        if (currentLevel >= MAX_ITEM_LEVEL) {
            throw new IllegalStateException("The item is already level five");
        }
        items.put(item, currentLevel + 1);
    }

    private void applyEvolution(EvolutionType evolution) {
        if (!canEvolve(evolution)) {
            throw new IllegalStateException("Evolution requirements are not met: " + evolution.id());
        }
        items.remove(evolution.firstMaterial());
        items.remove(evolution.secondMaterial());
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
