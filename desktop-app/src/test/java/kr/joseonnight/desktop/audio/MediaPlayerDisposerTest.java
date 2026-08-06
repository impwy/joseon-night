package kr.joseonnight.desktop.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.media.MediaPlayer;
import org.junit.jupiter.api.Test;

class MediaPlayerDisposerTest {

    @Test
    void schedulesPotentiallyBlockingDisposalInsteadOfRunningItOnTheCaller() {
        List<Runnable> scheduled = new ArrayList<>();
        MediaPlayer player = mock(MediaPlayer.class);
        MediaPlayerDisposer disposer = new MediaPlayerDisposer(scheduled::add);

        disposer.dispose(player);

        assertThat(scheduled).hasSize(1);
        verifyNoInteractions(player);

        scheduled.getFirst().run();

        verify(player).dispose();
    }

    @Test
    void isolatesRuntimeFailureFromTheCleanupExecutor() {
        List<Runnable> scheduled = new ArrayList<>();
        MediaPlayer player = mock(MediaPlayer.class);
        doThrow(new IllegalStateException("native cleanup failed")).when(player).dispose();
        MediaPlayerDisposer disposer = new MediaPlayerDisposer(scheduled::add);

        disposer.dispose(player);

        assertThatCode(() -> scheduled.getFirst().run()).doesNotThrowAnyException();
        verify(player).dispose();
    }
}
