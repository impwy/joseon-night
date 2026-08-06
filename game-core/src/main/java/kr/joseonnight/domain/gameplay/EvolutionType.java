package kr.joseonnight.domain.gameplay;

import java.util.Arrays;

/**
 * Two-item evolution recipes. Both materials must be level five.
 */
public enum EvolutionType {
    TEN_THOUSAND_SEAL_ARRAY(
            "ten-thousand-seal-array",
            "만방봉인진",
            ItemType.SEAL_TALISMAN,
            ItemType.EXORCIST_SWORD),
    HEAVENLY_THUNDER_SEAL(
            "heavenly-thunder-seal",
            "천뢰봉인부",
            ItemType.SEAL_TALISMAN,
            ItemType.THUNDER_BELL),
    INFERNO_RETURNING_WHEEL(
            "inferno-returning-wheel",
            "업화회륜",
            ItemType.FLAME_FAN,
            ItemType.RETURNING_BOOMERANG),
    BLUE_FLAME_SPIRIT_GOURD(
            "blue-flame-spirit-gourd",
            "청염귀호",
            ItemType.FLAME_FAN,
            ItemType.SPIRIT_GOURD),
    LUNAR_ECLIPSE_TWIN_BLADES(
            "lunar-eclipse-twin-blades",
            "월식쌍인",
            ItemType.EXORCIST_SWORD,
            ItemType.RETURNING_BOOMERANG),
    THUNDER_FLAME_DIVINE_ORB(
            "thunder-flame-divine-orb",
            "뇌화신주",
            ItemType.THUNDER_BELL,
            ItemType.SPIRIT_GOURD);

    private final String id;
    private final String displayName;
    private final ItemType firstMaterial;
    private final ItemType secondMaterial;

    EvolutionType(String id, String displayName, ItemType firstMaterial, ItemType secondMaterial) {
        this.id = id;
        this.displayName = displayName;
        this.firstMaterial = firstMaterial;
        this.secondMaterial = secondMaterial;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ItemType firstMaterial() {
        return firstMaterial;
    }

    public ItemType secondMaterial() {
        return secondMaterial;
    }

    public AttackMode attackMode() {
        return switch (this) {
            case HEAVENLY_THUNDER_SEAL, THUNDER_FLAME_DIVINE_ORB -> AttackMode.LIGHTNING;
            case TEN_THOUSAND_SEAL_ARRAY, INFERNO_RETURNING_WHEEL,
                    BLUE_FLAME_SPIRIT_GOURD, LUNAR_ECLIPSE_TWIN_BLADES -> AttackMode.PROJECTILE;
        };
    }

    public static EvolutionType fromId(String id) {
        return Arrays.stream(values())
                .filter(evolution -> evolution.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown evolution: " + id));
    }
}
