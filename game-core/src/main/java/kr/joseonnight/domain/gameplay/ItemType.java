package kr.joseonnight.domain.gameplay;

import java.util.Arrays;

/**
 * Stable item identifiers and Korean display names used by gameplay contracts.
 */
public enum ItemType {
    SEAL_TALISMAN("seal-talisman", "봉인 부적"),
    FLAME_FAN("flame-fan", "화염 부채"),
    EXORCIST_SWORD("exorcist-sword", "벽사검"),
    RETURNING_BOOMERANG("returning-boomerang", "회귀 부메랑"),
    THUNDER_BELL("thunder-bell", "낙뢰 방울"),
    SPIRIT_GOURD("spirit-gourd", "혼령 호리병");

    private final String id;
    private final String displayName;

    ItemType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public AttackMode attackMode() {
        return this == THUNDER_BELL ? AttackMode.LIGHTNING : AttackMode.PROJECTILE;
    }

    public static ItemType fromId(String id) {
        return Arrays.stream(values())
                .filter(item -> item.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + id));
    }
}
