package kr.joseonnight.desktop.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/** In-memory PCM WAV player backed by the JDK audio mixer instead of JavaFX media. */
final class JavaSoundClip implements AutoCloseable {
    private final Clip clip;

    JavaSoundClip(Clip clip) {
        this.clip = Objects.requireNonNull(clip, "clip");
    }

    static JavaSoundClip open(URL resource)
            throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        Objects.requireNonNull(resource, "resource");
        Clip loaded = AudioSystem.getClip();
        try (InputStream source = resource.openStream();
                BufferedInputStream buffered = new BufferedInputStream(source);
                AudioInputStream audio = AudioSystem.getAudioInputStream(buffered)) {
            loaded.open(audio);
            return new JavaSoundClip(loaded);
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException
                | RuntimeException exception) {
            loaded.close();
            throw exception;
        }
    }

    boolean playOnce(double volume) {
        if (!clip.isOpen() || clip.isRunning()) {
            return false;
        }
        setVolume(volume);
        clip.setFramePosition(0);
        clip.start();
        return true;
    }

    void playLoop(double volume, long requestedPositionMicros) {
        if (!clip.isOpen()) {
            return;
        }
        setVolume(volume);
        long lengthMicros = clip.getMicrosecondLength();
        long resumeAt = lengthMicros <= 0L
                ? 0L
                : Math.floorMod(Math.max(0L, requestedPositionMicros), lengthMicros);
        clip.setMicrosecondPosition(resumeAt);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    void setVolume(double volume) {
        double selected = requireUnitVolume(volume);
        if (clip.isControlSupported(BooleanControl.Type.MUTE)) {
            BooleanControl mute = (BooleanControl) clip.getControl(BooleanControl.Type.MUTE);
            mute.setValue(selected == 0.0);
        }
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            gain.setValue(decibels(selected, gain.getMinimum(), gain.getMaximum()));
        } else if (clip.isControlSupported(FloatControl.Type.VOLUME)) {
            FloatControl linear = (FloatControl) clip.getControl(FloatControl.Type.VOLUME);
            float value = (float) Math.clamp(selected, linear.getMinimum(), linear.getMaximum());
            linear.setValue(value);
        }
    }

    boolean isRunning() {
        return clip.isOpen() && clip.isRunning();
    }

    long positionMicros() {
        return clip.isOpen() ? Math.max(0L, clip.getMicrosecondPosition()) : 0L;
    }

    static float decibels(double volume, float minimum, float maximum) {
        double selected = requireUnitVolume(volume);
        double decibels = selected == 0.0 ? minimum : 20.0 * Math.log10(selected);
        return (float) Math.clamp(decibels, minimum, maximum);
    }

    @Override
    public void close() {
        if (!clip.isOpen()) {
            return;
        }
        RuntimeException failure = null;
        try {
            clip.stop();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            clip.flush();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        try {
            clip.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException appendFailure(
            RuntimeException first, RuntimeException additional) {
        if (first == null) {
            return additional;
        }
        first.addSuppressed(additional);
        return first;
    }

    private static double requireUnitVolume(double volume) {
        if (!Double.isFinite(volume) || volume < 0.0 || volume > 1.0) {
            throw new IllegalArgumentException("volume must be between 0.0 and 1.0");
        }
        return volume;
    }
}
