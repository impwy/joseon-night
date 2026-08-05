package kr.joseonnight.desktop.audio;

import java.util.Comparator;
import java.util.List;
import kr.joseonnight.desktop.gameplay.SoundEventSnapshot;

/** Selects only sound events newer than the last processed server event id. */
public final class SoundEventDeduplicator {
    private long lastProcessedId = -1L;

    public synchronized List<SoundEventSnapshot> selectNew(List<SoundEventSnapshot> events) {
        List<SoundEventSnapshot> fresh = events.stream()
                .filter(event -> event.id() > lastProcessedId)
                .sorted(Comparator.comparingLong(SoundEventSnapshot::id))
                .toList();
        if (!fresh.isEmpty()) {
            lastProcessedId = fresh.getLast().id();
        }
        return fresh;
    }

    public synchronized void reset() {
        lastProcessedId = -1L;
    }
}
