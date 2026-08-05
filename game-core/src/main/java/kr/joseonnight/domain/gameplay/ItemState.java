package kr.joseonnight.domain.gameplay;

import java.util.Objects;

public record ItemState(String itemId, String displayName, int level) {
    public ItemState {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(displayName, "displayName");
        if (level < 1 || level > ItemLoadout.MAX_ITEM_LEVEL) {
            throw new IllegalArgumentException("item level must be between 1 and 5");
        }
    }
}
