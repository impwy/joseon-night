package kr.joseonnight.desktop.audio;

import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import kr.joseonnight.desktop.gameplay.GameSnapshot;
import kr.joseonnight.desktop.gameplay.SoundCue;
import kr.joseonnight.desktop.gameplay.SoundEventSnapshot;
import kr.joseonnight.desktop.settings.AudioSettings;
import lombok.extern.slf4j.Slf4j;

/** PCM WAV facade that avoids the JavaFX media and GStreamer playback path. */
@Slf4j
public final class GameAudioService implements AutoCloseable {
    private static final Set<SoundCue> IMPORTANT_EFFECTS = EnumSet.of(
            SoundCue.LEVEL_UP, SoundCue.GUARD, SoundCue.CHEST_OPENED, SoundCue.DEFEAT);

    static final Map<SoundCue, String> EFFECT_PATHS = Map.ofEntries(
            Map.entry(SoundCue.LEVEL_UP, "/assets/audio/sfx-level-up.wav"),
            Map.entry(SoundCue.GUARD, "/assets/audio/sfx-guard.wav"),
            Map.entry(SoundCue.CHEST_OPENED, "/assets/audio/sfx-chest.wav"),
            Map.entry(SoundCue.DEFEAT, "/assets/audio/sfx-defeat.wav"),
            Map.entry(SoundCue.SEAL_TALISMAN_ATTACK, "/assets/audio/sfx-attack-seal-talisman.wav"),
            Map.entry(SoundCue.FLAME_FAN_ATTACK, "/assets/audio/sfx-attack-flame-fan.wav"),
            Map.entry(SoundCue.EXORCIST_SWORD_ATTACK, "/assets/audio/sfx-attack-exorcist-sword.wav"),
            Map.entry(SoundCue.RETURNING_BOOMERANG_ATTACK,
                    "/assets/audio/sfx-attack-returning-boomerang.wav"),
            Map.entry(SoundCue.THUNDER_BELL_ATTACK, "/assets/audio/sfx-attack-thunder-bell.wav"),
            Map.entry(SoundCue.SPIRIT_GOURD_ATTACK, "/assets/audio/sfx-attack-spirit-gourd.wav"),
            Map.entry(SoundCue.TEN_THOUSAND_SEAL_ARRAY_ATTACK,
                    "/assets/audio/sfx-attack-ten-thousand-seal-array.wav"),
            Map.entry(SoundCue.HEAVENLY_THUNDER_SEAL_ATTACK,
                    "/assets/audio/sfx-attack-heavenly-thunder-seal.wav"),
            Map.entry(SoundCue.INFERNO_RETURNING_WHEEL_ATTACK,
                    "/assets/audio/sfx-attack-inferno-returning-wheel.wav"),
            Map.entry(SoundCue.BLUE_FLAME_SPIRIT_GOURD_ATTACK,
                    "/assets/audio/sfx-attack-blue-flame-spirit-gourd.wav"),
            Map.entry(SoundCue.LUNAR_ECLIPSE_TWIN_BLADES_ATTACK,
                    "/assets/audio/sfx-attack-lunar-eclipse-twin-blades.wav"),
            Map.entry(SoundCue.THUNDER_FLAME_DIVINE_ORB_ATTACK,
                    "/assets/audio/sfx-attack-thunder-flame-divine-orb.wav"));

    private final SoundEventDeduplicator deduplicator = new SoundEventDeduplicator();
    private final SoundCueRateLimiter rateLimiter = new SoundCueRateLimiter();
    private final Map<SoundCue, JavaSoundClip> effectCache = new EnumMap<>(SoundCue.class);
    private final Set<SoundCue> unavailableEffects = EnumSet.noneOf(SoundCue.class);
    private final ExecutorService audioWorker = Executors.newSingleThreadExecutor(operation ->
            Thread.ofPlatform().daemon().name("desktop-audio").unstarted(operation));
    private final AtomicBoolean closed = new AtomicBoolean();

    private AudioSettings settings = AudioSettings.defaults();
    private AudioScene scene = AudioScene.SILENT;
    private JavaSoundClip musicClip;
    private long lastMusicPositionMicros;
    private boolean musicRecoveryAttempted;

    public GameAudioService() {
        dispatch(this::preloadImportantEffects);
    }

    public void applySettings(AudioSettings newSettings) {
        dispatch(() -> {
            settings = Objects.requireNonNull(newSettings, "newSettings");
            if (musicClip != null) {
                try {
                    musicClip.setVolume(settings.effectiveMusicVolume());
                } catch (RuntimeException exception) {
                    log.error("배경음악 음량을 적용하지 못했습니다.", exception);
                }
            }
        });
    }

    public void switchScene(AudioScene nextScene) {
        dispatch(() -> switchSceneOnAudioThread(nextScene));
    }

    public void beginNewGame() {
        deduplicator.reset();
        rateLimiter.reset();
        dispatch(() -> {
            unavailableEffects.clear();
            preloadImportantEffects();
            if (scene == AudioScene.COMBAT && requiresMusicRestart()) {
                musicRecoveryAttempted = false;
                replaceMusicClip(AudioScene.COMBAT, currentMusicPositionMicros(), false);
            } else {
                switchSceneOnAudioThread(AudioScene.COMBAT);
            }
        });
    }

    public void consume(GameSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        for (SoundEventSnapshot event : deduplicator.selectNew(snapshot.soundEvents())) {
            dispatch(() -> playEffectOnAudioThread(event.type()));
        }
    }

    /** Reconnects media objects after JavaFX reports that the window moved to another output. */
    public void refreshOutputDevice() {
        dispatch(this::refreshOutputDeviceOnAudioThread);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        audioWorker.execute(() -> {
            stopMusic();
            clearEffectCache();
        });
        audioWorker.shutdown();
    }

    private void switchSceneOnAudioThread(AudioScene nextScene) {
        AudioScene selected = Objects.requireNonNull(nextScene, "nextScene");
        if (scene == selected) {
            if (selected != AudioScene.SILENT
                    && requiresMusicRestart()
                    && !musicRecoveryAttempted) {
                log.error("배경음악이 예상하지 못하게 멈춰 한 번 복구합니다. scene={}", selected);
                musicRecoveryAttempted = true;
                replaceMusicClip(selected, currentMusicPositionMicros(), true);
            }
            return;
        }
        scene = selected;
        musicRecoveryAttempted = false;
        lastMusicPositionMicros = 0L;
        replaceMusicClip(selected, 0L, false);
    }

    private void refreshOutputDeviceOnAudioThread() {
        long resumeAt = currentMusicPositionMicros();
        clearEffectCache();
        preloadImportantEffects();
        replaceMusicClip(scene, resumeAt, true);
    }

    private boolean replaceMusicClip(
            AudioScene selected, long resumeAtMicros, boolean preservePreviousOnFailure) {
        JavaSoundClip previousClip = musicClip;
        JavaSoundClip replacement = null;
        String path = musicPath(selected);
        if (path == null) {
            musicClip = null;
            closeClip(previousClip, "이전 배경음악");
            return true;
        }
        try {
            replacement = loadClip(path, "배경음악");
            if (replacement == null) {
                handleMusicReplacementFailure(previousClip, preservePreviousOnFailure);
                return false;
            }
            replacement.playLoop(settings.effectiveMusicVolume(), resumeAtMicros);
            musicClip = replacement;
            replacement = null;
            lastMusicPositionMicros = Math.max(0L, resumeAtMicros);
            closeClip(previousClip, "이전 배경음악");
            return true;
        } catch (RuntimeException exception) {
            log.error("배경음악을 시작하지 못했습니다. scene={}, path={}", selected, path, exception);
            handleMusicReplacementFailure(previousClip, preservePreviousOnFailure);
            return false;
        } finally {
            closeClip(replacement, "시작하지 못한 배경음악");
        }
    }

    private void handleMusicReplacementFailure(
            JavaSoundClip previousClip, boolean preservePreviousOnFailure) {
        if (preservePreviousOnFailure) {
            musicClip = previousClip;
        } else {
            musicClip = null;
            closeClip(previousClip, "교체하지 못한 이전 배경음악");
        }
    }

    private void playEffectOnAudioThread(SoundCue cue) {
        if (cue == null || settings.effectiveEffectsVolume() <= 0.0) {
            return;
        }
        String path = EFFECT_PATHS.get(cue);
        if (path == null || unavailableEffects.contains(cue)) {
            return;
        }
        JavaSoundClip clip = effectCache.get(cue);
        if ((clip != null && clip.isRunning()) || !rateLimiter.tryAcquire(cue)) {
            return;
        }
        if (clip == null) {
            clip = loadEffectClip(cue, path);
        }
        if (clip == null) {
            return;
        }
        try {
            clip.playOnce(settings.effectiveEffectsVolume());
        } catch (RuntimeException exception) {
            effectCache.remove(cue);
            unavailableEffects.add(cue);
            closeClip(clip, "재생에 실패한 효과음 " + cue);
            log.error("효과음을 재생하지 못했습니다. cue={}, path={}", cue, path, exception);
        }
    }

    private void preloadImportantEffects() {
        for (SoundCue cue : IMPORTANT_EFFECTS) {
            if (!effectCache.containsKey(cue) && !unavailableEffects.contains(cue)) {
                loadEffectClip(cue, EFFECT_PATHS.get(cue));
            }
        }
    }

    private JavaSoundClip loadEffectClip(SoundCue cue, String path) {
        JavaSoundClip loaded = loadClip(path, "효과음");
        if (loaded == null) {
            unavailableEffects.add(cue);
            return null;
        }
        effectCache.put(cue, loaded);
        return loaded;
    }

    private JavaSoundClip loadClip(String path, String assetType) {
        URL resource = GameAudioService.class.getResource(path);
        if (resource == null) {
            log.error("{} 자산을 찾을 수 없습니다. path={}", assetType, path);
            return null;
        }
        try {
            return JavaSoundClip.open(resource);
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException
                | RuntimeException exception) {
            log.error("{} 재생기를 만들지 못했습니다. path={}", assetType, path, exception);
            return null;
        }
    }

    private long currentMusicPositionMicros() {
        JavaSoundClip clip = musicClip;
        return clip == null ? lastMusicPositionMicros : clip.positionMicros();
    }

    private boolean requiresMusicRestart() {
        return musicClip == null || !musicClip.isRunning();
    }

    private static String musicPath(AudioScene selected) {
        return switch (selected) {
            case LOBBY -> "/assets/audio/bgm-lobby.wav";
            case COMBAT -> "/assets/audio/bgm-combat.wav";
            case SILENT -> null;
        };
    }

    private void clearEffectCache() {
        effectCache.forEach((cue, clip) -> closeClip(clip, "효과음 " + cue));
        effectCache.clear();
        unavailableEffects.clear();
    }

    private void stopMusic() {
        JavaSoundClip clip = musicClip;
        musicClip = null;
        closeClip(clip, "배경음악");
    }

    private static void closeClip(JavaSoundClip clip, String description) {
        if (clip == null) {
            return;
        }
        try {
            clip.close();
        } catch (RuntimeException exception) {
            log.warn("{} 재생기를 닫지 못했습니다.", description, exception);
        }
    }

    private void dispatch(Runnable operation) {
        if (closed.get()) {
            return;
        }
        try {
            audioWorker.execute(operation);
        } catch (RejectedExecutionException exception) {
            if (!closed.get()) {
                log.warn("오디오 작업을 예약하지 못했습니다.", exception);
            }
        }
    }
}
