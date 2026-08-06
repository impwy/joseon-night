package kr.joseonnight.desktop.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GameSnapshotCompatibilityTest {

    @Test
    void snapshotFromAnOlderServerWithoutHeartStateDefaultsToUnavailable() {
        GameSnapshot snapshot = new ObjectMapper().readValue(
                """
                {
                  "phase": "RUNNING",
                  "paused": false,
                  "elapsedSeconds": 12.5,
                  "level": 3,
                  "experience": 4,
                  "experienceToNextLevel": 10,
                  "killCount": 20,
                  "player": {"id": 1, "x": 0.0, "y": 0.0, "radius": 18.0,
                             "rotationDegrees": 0.0},
                  "enemies": [],
                  "projectiles": [],
                  "lightningStrikes": [],
                  "soulFlames": [],
                  "upgradeChoices": [],
                  "character": "DOKKAEBI_HUNTER",
                  "barrierAvailable": false,
                  "invulnerabilityRemainingSeconds": 0.0,
                  "items": [],
                  "evolutions": [],
                  "occupiedItemSlots": 0,
                  "chests": [],
                  "chestIndicators": [],
                  "levelUpOptions": [],
                  "chestRewardOptions": [],
                  "soundEvents": [],
                  "futureServerField": "ignored"
                }
                """,
                GameSnapshot.class);

        assertThat(snapshot.heartAvailable()).isFalse();
        assertThat(snapshot.enemies()).isEmpty();
        assertThat(snapshot.pendingChestOptions()).isEmpty();
    }
}
