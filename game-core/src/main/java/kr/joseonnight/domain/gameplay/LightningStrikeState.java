package kr.joseonnight.domain.gameplay;

import java.util.Objects;
import org.springframework.util.Assert;

/** Immutable render state for a short-lived lightning strike whose damage is already confirmed. */
public record LightningStrikeState(
        long id,
        double x,
        double y,
        double remainingSeconds,
        String kindId
) {
    public LightningStrikeState {
        Assert.isTrue(id > 0L, "lightning strike id must be positive");
        Assert.isTrue(Double.isFinite(x) && Double.isFinite(y),
                "lightning strike position must be finite");
        Assert.isTrue(Double.isFinite(remainingSeconds) && remainingSeconds > 0.0,
                "lightning strike lifetime must be positive");
        Objects.requireNonNull(kindId, "kindId");
    }
}
