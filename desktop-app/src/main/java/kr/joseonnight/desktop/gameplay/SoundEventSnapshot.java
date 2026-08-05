package kr.joseonnight.desktop.gameplay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One-shot sound cue. Its monotonically increasing id prevents replay after reconnects. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SoundEventSnapshot(long id, String type) {
}
