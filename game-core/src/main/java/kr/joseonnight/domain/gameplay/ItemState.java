package kr.joseonnight.domain.gameplay;

import java.util.Objects;
import org.springframework.util.Assert;

public record ItemState(String itemId, String displayName, int level) {
    public ItemState {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(displayName, "displayName");
        Assert.isTrue(
                level >= 1 && level <= ItemLoadout.MAX_ITEM_LEVEL,
                "item level must be between 1 and 5"
        );
    }
}
