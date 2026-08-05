package kr.vamsur.domain.gameplay;

/**
 * Immutable domain projection of a circular entity.
 */
public record EntityState(
        long id,
        double x,
        double y,
        double radius,
        double rotationDegrees
) {
}
