package kr.joseonnight.desktop.gameplay;

/**
 * Immutable render data received from game-core.
 */
public record EntitySnapshot(
        long id,
        double x,
        double y,
        double radius,
        double rotationDegrees,
        String kindId
) {
    public EntitySnapshot(long id, double x, double y, double radius, double rotationDegrees) {
        this(id, x, y, radius, rotationDegrees, "unknown");
    }
}
