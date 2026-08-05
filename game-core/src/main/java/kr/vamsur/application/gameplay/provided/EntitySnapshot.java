package kr.vamsur.application.gameplay.provided;

/**
 * Framework-neutral render DTO for a circular game entity.
 */
public record EntitySnapshot(
        long id,
        double x,
        double y,
        double radius,
        double rotationDegrees
) {
}
