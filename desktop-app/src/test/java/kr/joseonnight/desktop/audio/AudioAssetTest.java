package kr.joseonnight.desktop.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import kr.joseonnight.desktop.gameplay.SoundCue;
import org.junit.jupiter.api.Test;

class AudioAssetTest {
    private static final int SAMPLE_RATE = 22_050;
    private static final int CHANNELS = 2;
    private static final int MAX_LOOP_DISCONTINUITY = 512;
    private static final double MINIMUM_BOUNDARY_RMS = 350.0;
    private static final double MINIMUM_BOUNDARY_RMS_RATIO = 0.40;

    private static final List<AssetExpectation> GENERATED_ASSETS = List.of(
            new AssetExpectation("bgm-lobby.wav", 36 * SAMPLE_RATE, true),
            new AssetExpectation("bgm-combat.wav", 48 * SAMPLE_RATE, true),
            new AssetExpectation("sfx-attack-seal-talisman.wav", 6_615, false),
            new AssetExpectation("sfx-attack-flame-fan.wav", 9_261, false),
            new AssetExpectation("sfx-attack-exorcist-sword.wav", 6_174, false),
            new AssetExpectation("sfx-attack-returning-boomerang.wav", 10_143, false),
            new AssetExpectation("sfx-attack-thunder-bell.wav", 13_230, false),
            new AssetExpectation("sfx-attack-spirit-gourd.wav", 11_025, false),
            new AssetExpectation("sfx-attack-ten-thousand-seal-array.wav", 15_876, false),
            new AssetExpectation("sfx-attack-heavenly-thunder-seal.wav", 14_994, false),
            new AssetExpectation("sfx-attack-inferno-returning-wheel.wav", 17_199, false),
            new AssetExpectation("sfx-attack-blue-flame-spirit-gourd.wav", 16_758, false),
            new AssetExpectation("sfx-attack-lunar-eclipse-twin-blades.wav", 14_112, false),
            new AssetExpectation("sfx-attack-thunder-flame-divine-orb.wav", 18_081, false));

    @Test
    void generatedAssetsHaveTheRequiredFormatSignalAndLoopBoundaries()
            throws IOException, UnsupportedAudioFileException {
        for (AssetExpectation expectation : GENERATED_ASSETS) {
            inspect(expectation);
        }
    }

    @Test
    void everyProtocolCueMapsToAnExistingEffectAsset() {
        assertThat(GameAudioService.EFFECT_PATHS).containsOnlyKeys(SoundCue.values());
        GameAudioService.EFFECT_PATHS.forEach((cue, path) ->
                assertThat(AudioAssetTest.class.getResource(path)).as(cue.name()).isNotNull());

        List<String> attackPaths = GameAudioService.EFFECT_PATHS.entrySet().stream()
                .filter(entry -> entry.getKey().name().endsWith("_ATTACK"))
                .map(java.util.Map.Entry::getValue)
                .toList();
        assertThat(attackPaths).hasSize(12).doesNotHaveDuplicates();
    }

    @Test
    void importantEffectsHavePriorityOverAttackEffects() {
        assertThat(List.of(
                SoundCue.GUARD,
                SoundCue.LEVEL_UP,
                SoundCue.CHEST_OPENED,
                SoundCue.DEFEAT))
                .allSatisfy(cue -> assertThat(GameAudioService.effectPriority(cue)).isEqualTo(100));
        Arrays.stream(SoundCue.values())
                .filter(cue -> cue.name().endsWith("_ATTACK"))
                .forEach(cue -> assertThat(GameAudioService.effectPriority(cue)).isZero());
    }

    private static void inspect(AssetExpectation expectation)
            throws IOException, UnsupportedAudioFileException {
        String resourcePath = "/assets/audio/" + expectation.filename();
        try (InputStream resource = AudioAssetTest.class.getResourceAsStream(resourcePath)) {
            assertThat(resource).as(resourcePath).isNotNull();
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(resource)) {
                AudioFormat format = audio.getFormat();
                assertThat(format.getEncoding()).isEqualTo(AudioFormat.Encoding.PCM_SIGNED);
                assertThat(format.getSampleRate()).isEqualTo((float) SAMPLE_RATE);
                assertThat(format.getSampleSizeInBits()).isEqualTo(16);
                assertThat(format.getChannels()).isEqualTo(CHANNELS);
                assertThat(format.getFrameSize()).isEqualTo(4);
                assertThat(format.isBigEndian()).isFalse();
                assertThat(audio.getFrameLength()).isEqualTo(expectation.frames());

                byte[] bytes = audio.readAllBytes();
                assertThat(bytes).hasSize(Math.toIntExact(expectation.frames() * 4L));
                verifySignal(expectation, bytes);
            }
        }
    }

    private static void verifySignal(AssetExpectation expectation, byte[] bytes) {
        long squareSum = 0L;
        int peak = 0;
        for (int offset = 0; offset < bytes.length; offset += 2) {
            int sample = (short) ((bytes[offset] & 0xFF) | (bytes[offset + 1] << 8));
            peak = Math.max(peak, Math.abs(sample));
            squareSum += (long) sample * sample;
        }
        double rms = Math.sqrt((double) squareSum / ((double) bytes.length / 2.0));
        assertThat(rms).as(expectation.filename() + " RMS").isGreaterThan(100.0);
        assertThat(peak).as(expectation.filename() + " peak").isLessThan(32_000);

        if (!expectation.looped()) {
            return;
        }
        verifyLoopBoundary(expectation, bytes, rms);
    }

    private static void verifyLoopBoundary(
            AssetExpectation expectation, byte[] bytes, double overallRms) {
        int finalFrameOffset = bytes.length - 4;
        for (int channel = 0; channel < CHANNELS; channel++) {
            int first = sampleAt(bytes, channel * 2);
            int last = sampleAt(bytes, finalFrameOffset + channel * 2);
            assertThat(Math.abs(first - last))
                    .as(expectation.filename() + " loop discontinuity")
                    .isLessThanOrEqualTo(MAX_LOOP_DISCONTINUITY);
        }

        int boundaryWindowBytes = SAMPLE_RATE / 20 * CHANNELS * 2;
        double startRms = rms(bytes, 0, boundaryWindowBytes);
        double endRms = rms(bytes, bytes.length - boundaryWindowBytes, bytes.length);
        double requiredRms = Math.max(MINIMUM_BOUNDARY_RMS, overallRms * MINIMUM_BOUNDARY_RMS_RATIO);
        assertThat(startRms)
                .as(expectation.filename() + " start boundary RMS")
                .isGreaterThanOrEqualTo(requiredRms);
        assertThat(endRms)
                .as(expectation.filename() + " end boundary RMS")
                .isGreaterThanOrEqualTo(requiredRms);
    }

    private static double rms(byte[] bytes, int startOffset, int endOffset) {
        long squareSum = 0L;
        int sampleCount = 0;
        for (int offset = startOffset; offset < endOffset; offset += 2) {
            int sample = sampleAt(bytes, offset);
            squareSum += (long) sample * sample;
            sampleCount++;
        }
        return Math.sqrt((double) squareSum / sampleCount);
    }

    private static int sampleAt(byte[] bytes, int offset) {
        return (short) ((bytes[offset] & 0xFF) | (bytes[offset + 1] << 8));
    }

    private record AssetExpectation(String filename, long frames, boolean looped) {
    }
}
