package kr.joseonnight.domain.gameplay;

import java.util.Arrays;

/**
 * Immediate-use treasure chest effects that do not occupy an item slot.
 */
public enum ChestRewardType {
    HEART(
            "heart",
            "하트",
            "죽음을 한 번 막아 줍니다."),
    MAGNET(
            "magnet",
            "자석",
            "맵의 모든 혼불을 즉시 끌어옵니다.");

    private final String id;
    private final String displayName;
    private final String description;

    ChestRewardType(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static ChestRewardType fromId(String id) {
        return Arrays.stream(values())
                .filter(effect -> effect.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown chest reward: " + id));
    }
}
