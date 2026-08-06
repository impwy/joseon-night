package kr.joseonnight.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AudioSettingsTest {
    @Test
    void defaultsUseLowerMusicVolumeWithoutLoweringEffects() {
        AudioSettings settings = AudioSettings.defaults();

        assertThat(settings.musicVolume()).isEqualTo(60);
        assertThat(settings.effectsVolume()).isEqualTo(70);
    }

    @Test
    void muteSilencesMusicAndEffectsWithoutChangingSavedVolumes() {
        AudioSettings settings = new AudioSettings(true, 35, 80, TargetFps.FPS_30);

        assertThat(settings.effectiveMusicVolume()).isZero();
        assertThat(settings.effectiveEffectsVolume()).isZero();
        assertThat(settings.musicVolume()).isEqualTo(35);
        assertThat(settings.effectsVolume()).isEqualTo(80);
    }

    @Test
    void rejectsVolumesOutsidePercentageRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AudioSettings(false, -1, 50, TargetFps.AUTO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AudioSettings(false, 50, 101, TargetFps.AUTO));
    }

    @Test
    void requiresTargetFps() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AudioSettings(false, 50, 50, null));
    }
}
