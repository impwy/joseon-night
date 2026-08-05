package kr.joseonnight.domain.gameplay;

import java.util.Arrays;

/**
 * Selectable characters. IDs are protocol values and display names are presentation copy.
 */
public enum CharacterType {
    DOKKAEBI_HUNTER(
            "dokkaebi-hunter",
            "도깨비 사냥꾼",
            ItemType.SEAL_TALISMAN,
            1.0,
            true),
    GALE_SHAMAN(
            "gale-shaman",
            "질풍 무녀",
            ItemType.FLAME_FAN,
            1.25,
            false);

    private final String id;
    private final String displayName;
    private final ItemType startingItem;
    private final double speedMultiplier;
    private final boolean startsWithBarrier;

    CharacterType(
            String id,
            String displayName,
            ItemType startingItem,
            double speedMultiplier,
            boolean startsWithBarrier
    ) {
        this.id = id;
        this.displayName = displayName;
        this.startingItem = startingItem;
        this.speedMultiplier = speedMultiplier;
        this.startsWithBarrier = startsWithBarrier;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ItemType startingItem() {
        return startingItem;
    }

    public double speedMultiplier() {
        return speedMultiplier;
    }

    public boolean startsWithBarrier() {
        return startsWithBarrier;
    }

    public static CharacterType fromId(String id) {
        return Arrays.stream(values())
                .filter(character -> character.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown character: " + id));
    }
}
