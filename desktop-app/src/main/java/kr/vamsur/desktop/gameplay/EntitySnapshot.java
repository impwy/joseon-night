package kr.vamsur.desktop.gameplay;

/**
 * Immutable render data received from game-core.
 */
public record EntitySnapshot(
        long id,
        double x,
        double y,
        double radius,
        double rotationDegrees
) {
}
