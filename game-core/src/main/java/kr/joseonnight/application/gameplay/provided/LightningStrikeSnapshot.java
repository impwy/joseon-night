package kr.joseonnight.application.gameplay.provided;

/** Render-only lightning effect for damage already resolved by the server. */
public record LightningStrikeSnapshot(
        long id,
        double x,
        double y,
        double remainingSeconds,
        String kindId
) {
}
