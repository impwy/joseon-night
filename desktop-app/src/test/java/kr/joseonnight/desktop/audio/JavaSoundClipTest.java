package kr.joseonnight.desktop.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class JavaSoundClipTest {

    @Test
    void replaysCompletedEffectFromTheBeginning() {
        Clip nativeClip = openClip();
        JavaSoundClip clip = new JavaSoundClip(nativeClip);

        assertThat(clip.playOnce(0.7)).isTrue();

        InOrder order = inOrder(nativeClip);
        order.verify(nativeClip).setFramePosition(0);
        order.verify(nativeClip).start();
    }

    @Test
    void doesNotOverlapTheSameEffect() {
        Clip nativeClip = openClip();
        when(nativeClip.isRunning()).thenReturn(true);
        JavaSoundClip clip = new JavaSoundClip(nativeClip);

        assertThat(clip.playOnce(0.7)).isFalse();

        verify(nativeClip, never()).setFramePosition(0);
        verify(nativeClip, never()).start();
    }

    @Test
    void reusesOneNativeLineForRepeatedEffects() {
        Clip nativeClip = openClip();
        JavaSoundClip clip = new JavaSoundClip(nativeClip);

        for (int play = 0; play < 100; play++) {
            assertThat(clip.playOnce(0.7)).isTrue();
        }

        verify(nativeClip, times(100)).setFramePosition(0);
        verify(nativeClip, times(100)).start();
        verify(nativeClip, never()).close();
    }

    @Test
    void loopsMusicContinuouslyFromNormalizedPosition() {
        Clip nativeClip = openClip();
        when(nativeClip.getMicrosecondLength()).thenReturn(48_000_000L);
        JavaSoundClip clip = new JavaSoundClip(nativeClip);

        clip.playLoop(0.6, 50_500_000L);

        InOrder order = inOrder(nativeClip);
        order.verify(nativeClip).setMicrosecondPosition(2_500_000L);
        order.verify(nativeClip).loop(Clip.LOOP_CONTINUOUSLY);
    }

    @Test
    void convertsLinearVolumeToClampedDecibels() {
        assertThat(JavaSoundClip.decibels(1.0, -80.0f, 6.0f)).isZero();
        assertThat(JavaSoundClip.decibels(0.5, -80.0f, 6.0f)).isCloseTo(-6.0206f, within(0.001f));
        assertThat(JavaSoundClip.decibels(0.0, -80.0f, 6.0f)).isEqualTo(-80.0f);
    }

    @Test
    void appliesMuteAndGainOnTheNativeLine() {
        Clip nativeClip = openClip();
        BooleanControl mute = mock(BooleanControl.class);
        FloatControl gain = mock(FloatControl.class);
        when(nativeClip.isControlSupported(BooleanControl.Type.MUTE)).thenReturn(true);
        when(nativeClip.getControl(BooleanControl.Type.MUTE)).thenReturn(mute);
        when(nativeClip.isControlSupported(FloatControl.Type.MASTER_GAIN)).thenReturn(true);
        when(nativeClip.getControl(FloatControl.Type.MASTER_GAIN)).thenReturn(gain);
        when(gain.getMinimum()).thenReturn(-80.0f);
        when(gain.getMaximum()).thenReturn(6.0f);
        JavaSoundClip clip = new JavaSoundClip(nativeClip);

        clip.setVolume(0.5);

        verify(mute).setValue(false);
        verify(gain).setValue(JavaSoundClip.decibels(0.5, -80.0f, 6.0f));
    }

    @Test
    void rejectsInvalidVolume() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JavaSoundClip.decibels(-0.1, -80.0f, 6.0f));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JavaSoundClip.decibels(Double.NaN, -80.0f, 6.0f));
    }

    @Test
    void stopsAndClosesTheNativeLine() {
        Clip nativeClip = openClip();
        JavaSoundClip clip = new JavaSoundClip(nativeClip);

        clip.close();

        InOrder order = inOrder(nativeClip);
        order.verify(nativeClip).stop();
        order.verify(nativeClip).flush();
        order.verify(nativeClip).close();
    }

    @Test
    void stillClosesTheNativeLineWhenStopFails() {
        Clip nativeClip = openClip();
        doThrow(new IllegalStateException("stop failed")).when(nativeClip).stop();
        JavaSoundClip clip = new JavaSoundClip(nativeClip);

        assertThatIllegalStateException().isThrownBy(clip::close).withMessage("stop failed");

        verify(nativeClip).flush();
        verify(nativeClip).close();
    }

    private static Clip openClip() {
        Clip clip = mock(Clip.class);
        when(clip.isOpen()).thenReturn(true);
        return clip;
    }

    private static org.assertj.core.data.Offset<Float> within(float value) {
        return org.assertj.core.data.Offset.offset(value);
    }

}
