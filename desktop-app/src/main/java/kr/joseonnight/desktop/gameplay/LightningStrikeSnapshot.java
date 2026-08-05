package kr.joseonnight.desktop.gameplay;

/** A short-lived lightning effect whose gameplay damage was resolved by game-core. */
public record LightningStrikeSnapshot(
        long id,
        double x,
        double y,
        double remainingSeconds,
        String kindId
) {
}
