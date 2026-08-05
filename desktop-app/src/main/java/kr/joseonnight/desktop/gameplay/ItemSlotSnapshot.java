package kr.joseonnight.desktop.gameplay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A weapon or passive item currently owned by the player. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemSlotSnapshot(String itemId, String displayName, int level) {
}
