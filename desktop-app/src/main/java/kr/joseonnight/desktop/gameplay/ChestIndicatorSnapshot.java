package kr.joseonnight.desktop.gameplay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Direction and distance to a chest that is currently outside the viewport. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChestIndicatorSnapshot(
        long chestId,
        double directionX,
        double directionY,
        double distance) {
}
