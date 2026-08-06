package kr.joseonnight.desktop.gameplay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A server-owned level-up or chest reward choice. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RewardOptionSnapshot(String optionId, String kind, String displayName, String description) {
}
