package kr.joseonnight.domain.gameplay;

public record DirectionIndicatorState(
        long chestId,
        ChestType chestType,
        double directionX,
        double directionY,
        double distance
) {
}
