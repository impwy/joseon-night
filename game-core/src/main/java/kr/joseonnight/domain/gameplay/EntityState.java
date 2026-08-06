package kr.joseonnight.domain.gameplay;

/**
 * Immutable domain projection of a circular entity.
 */
public record EntityState(
        long id,
        double x,
        double y,
        double radius,
        double rotationDegrees,
        String kindId
) {
    public EntityState(long id, double x, double y, double radius, double rotationDegrees) {
        this(id, x, y, radius, rotationDegrees, "unknown");
    }
}
