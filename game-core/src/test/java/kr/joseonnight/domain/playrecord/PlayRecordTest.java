package kr.joseonnight.domain.playrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayRecordTest {

    private static final UUID SESSION_ID = UUID.fromString("c434447a-0e8b-4b4c-af52-c9f04bd2fe9c");
    private static final Instant ENDED_AT = Instant.parse("2026-08-05T12:05:00Z");

    @Test
    void recordsServerCalculatedResultAndCopiesFinalBuild() {
        Map<String, Object> finalBuild = new HashMap<>();
        finalBuild.put("seal-talisman", 5);

        PlayRecord record = record(PlayOutcome.VICTORY, true, finalBuild);
        finalBuild.put("seal-talisman", 1);

        assertThat(record.getGameSession()).isEqualTo(SESSION_ID);
        assertThat(record.getScore()).isEqualTo(12_000L);
        assertThat(record.getFinalBuild()).containsEntry("seal-talisman", 5);
        assertThatThrownBy(() -> record.getFinalBuild().put("bell", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void abandonedGameCannotBeRankingEligible() {
        assertThatThrownBy(() -> record(PlayOutcome.ABANDONED, true, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Abandoned");
    }

    @Test
    void rejectsInvalidCalculatedValuesAndCharacterId() {
        assertThatThrownBy(() -> PlayRecord.record(
                SESSION_ID,
                1L,
                "dokkaebi-hunter",
                -1,
                0,
                1,
                PlayOutcome.DEFEAT,
                0,
                Map.of(),
                ENDED_AT,
                false
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlayRecord.record(
                SESSION_ID,
                1L,
                " ",
                0,
                0,
                1,
                PlayOutcome.DEFEAT,
                0,
                Map.of(),
                ENDED_AT,
                false
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static PlayRecord record(
            PlayOutcome outcome,
            boolean rankingEligible,
            Map<String, Object> finalBuild
    ) {
        return PlayRecord.record(
                SESSION_ID,
                1L,
                "dokkaebi-hunter",
                12_000L,
                31,
                8,
                outcome,
                300_000L,
                finalBuild,
                ENDED_AT,
                rankingEligible
        );
    }
}
