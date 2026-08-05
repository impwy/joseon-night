package kr.joseonnight.desktop.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.joseonnight.desktop.gameplay.SoundEventSnapshot;
import org.junit.jupiter.api.Test;

class SoundEventDeduplicatorTest {
    private final SoundEventDeduplicator deduplicator = new SoundEventDeduplicator();

    @Test
    void repeatedAndOutOfOrderSnapshotsPlayEachEventOnlyOnce() {
        List<SoundEventSnapshot> first = deduplicator.selectNew(List.of(
                new SoundEventSnapshot(2L, "LEVEL_UP"),
                new SoundEventSnapshot(1L, "GUARD")));
        List<SoundEventSnapshot> repeated = deduplicator.selectNew(List.of(
                new SoundEventSnapshot(1L, "GUARD"),
                new SoundEventSnapshot(2L, "LEVEL_UP")));
        List<SoundEventSnapshot> next = deduplicator.selectNew(List.of(
                new SoundEventSnapshot(2L, "LEVEL_UP"),
                new SoundEventSnapshot(3L, "CHEST_OPENED")));

        assertThat(first).extracting(SoundEventSnapshot::id).containsExactly(1L, 2L);
        assertThat(repeated).isEmpty();
        assertThat(next).extracting(SoundEventSnapshot::id).containsExactly(3L);
    }

    @Test
    void aNewGameCanRestartTheServerEventSequence() {
        deduplicator.selectNew(List.of(new SoundEventSnapshot(10L, "DEFEAT")));
        deduplicator.reset();

        assertThat(deduplicator.selectNew(List.of(new SoundEventSnapshot(1L, "GUARD"))))
                .hasSize(1);
    }
}
