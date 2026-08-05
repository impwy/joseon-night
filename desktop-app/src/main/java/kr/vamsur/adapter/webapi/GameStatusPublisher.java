package kr.vamsur.adapter.webapi;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import kr.vamsur.application.gameplay.provided.GameSnapshot;

/**
 * Publishes immutable snapshots from the JavaFX thread to HTTP worker threads.
 */
public final class GameStatusPublisher {
    private final AtomicReference<GameSnapshot> latest;

    public GameStatusPublisher(GameSnapshot initialSnapshot) {
        latest = new AtomicReference<>(Objects.requireNonNull(initialSnapshot, "initialSnapshot"));
    }

    public void publish(GameSnapshot snapshot) {
        latest.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public GameSnapshot latest() {
        return latest.get();
    }
}
