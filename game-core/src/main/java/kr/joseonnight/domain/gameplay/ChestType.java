package kr.joseonnight.domain.gameplay;

public enum ChestType {
    YELLOW("yellow", "노란 상자"),
    PURPLE("purple", "보라 상자");

    private final String id;
    private final String displayName;

    ChestType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
