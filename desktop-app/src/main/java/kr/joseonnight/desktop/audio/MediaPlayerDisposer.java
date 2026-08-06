package kr.joseonnight.desktop.audio;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.media.MediaPlayer;
import lombok.extern.slf4j.Slf4j;

/** Releases JavaFX media resources without blocking the application thread. */
@Slf4j
final class MediaPlayerDisposer {
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final Executor DISPOSAL_EXECUTOR = Executors.newSingleThreadExecutor(operation ->
            Thread.ofPlatform()
                    .daemon()
                    .name("desktop-media-disposer-" + THREAD_SEQUENCE.incrementAndGet())
                    .unstarted(operation));

    private final Executor executor;

    MediaPlayerDisposer() {
        this(DISPOSAL_EXECUTOR);
    }

    MediaPlayerDisposer(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    void dispose(MediaPlayer player) {
        MediaPlayer selected = Objects.requireNonNull(player, "player");
        executor.execute(() -> {
            try {
                selected.dispose();
            } catch (RuntimeException exception) {
                log.warn("배경음악 재생기 자원 해제에 실패했습니다.", exception);
            }
        });
    }
}
