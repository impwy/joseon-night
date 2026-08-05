package kr.joseonnight.desktop.gameplay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A treasure chest visible in the world. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChestSnapshot(long id, String type, double x, double y, Boolean opened) {
    public ChestSnapshot {
        opened = opened == null ? Boolean.FALSE : opened;
    }
}
