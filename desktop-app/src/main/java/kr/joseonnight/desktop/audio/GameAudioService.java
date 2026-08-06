package kr.joseonnight.desktop.audio;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import kr.joseonnight.desktop.gameplay.GameSnapshot;
import kr.joseonnight.desktop.gameplay.SoundCue;
import kr.joseonnight.desktop.gameplay.SoundEventSnapshot;
import kr.joseonnight.desktop.settings.AudioSettings;
import lombok.extern.slf4j.Slf4j;

/** JavaFX media facade with graceful no-audio fallbacks for source-only development builds. */
@Slf4j
public final class GameAudioService implements AutoCloseable {
    private static final int IMPORTANT_EFFECT_PRIORITY = 100;
    private static final int ATTACK_EFFECT_PRIORITY = 0;

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
    private final Map<SoundCue, AudioClip> effectCache = new EnumMap<>(SoundCue.class);
    private final MediaPlayerDisposer mediaPlayerDisposer = new MediaPlayerDisposer();

    private AudioSettings settings = AudioSettings.defaults();
    private AudioScene scene = AudioScene.SILENT;
    private MediaPlayer musicPlayer;
    private Duration lastMusicPosition = Duration.ZERO;
    private boolean musicRecoveryAttempted;

    public void applySettings(AudioSettings newSettings) {
        dispatch(() -> {
            settings = Objects.requireNonNull(newSettings, "newSettings");
            if (musicPlayer != null) {
                musicPlayer.setVolume(settings.effectiveMusicVolume());
            }
        });
    }

    public void switchScene(AudioScene nextScene) {
        dispatch(() -> switchSceneOnJavaFxThread(nextScene));
    }

    public void beginNewGame() {
        deduplicator.reset();
        rateLimiter.reset();
        dispatch(() -> {
            musicRecoveryAttempted = false;
            if (scene == AudioScene.COMBAT && requiresMusicRestart()) {
                replaceMusicPlayer(AudioScene.COMBAT, currentMusicPosition());
            } else {
                switchSceneOnJavaFxThread(AudioScene.COMBAT);
            }
        });
    }

    public void consume(GameSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        for (SoundEventSnapshot event : deduplicator.selectNew(snapshot.soundEvents())) {
            dispatch(() -> playEffectOnJavaFxThread(event.type()));
        }
    }

    /** Reconnects media objects after JavaFX reports that the window moved to another output. */
    public void refreshOutputDevice() {
        dispatch(this::refreshOutputDeviceOnJavaFxThread);
    }

    @Override
    public void close() {
        dispatch(() -> {
            stopMusic();
            clearEffectCache();
        });
    }

    private void switchSceneOnJavaFxThread(AudioScene nextScene) {
        AudioScene selected = Objects.requireNonNull(nextScene, "nextScene");
        if (scene == selected) {
            return;
        }
        scene = selected;
        musicRecoveryAttempted = false;
        lastMusicPosition = Duration.ZERO;
        replaceMusicPlayer(selected, Duration.ZERO);
    }

    private void refreshOutputDeviceOnJavaFxThread() {
        Duration resumeAt = currentMusicPosition();
        clearEffectCache();
        replaceMusicPlayer(scene, resumeAt);
    }

    private void replaceMusicPlayer(AudioScene selected, Duration resumeAt) {
        MediaPlayer previousPlayer = detachCurrentMusicPlayer();
        String path = musicPath(selected);
        try {
            URL resource = path == null ? null : GameAudioService.class.getResource(path);
            if (resource == null) {
                if (path != null) {
                    log.error("배경음악 자산을 찾을 수 없습니다. scene={}, path={}", selected, path);
                }
                return;
            }
            Media media = new Media(resource.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setVolume(settings.effectiveMusicVolume());
            player.setOnReady(() -> startReadyPlayer(player, resumeAt));
            player.setOnError(() -> handlePlaybackFailure(
                    selected, path, player, "MediaPlayer 비동기 오류", player.getError()));
            player.setOnStopped(() -> handlePlaybackFailure(
                    selected, path, player, "예상하지 못한 STOPPED 상태", player.getError()));
            player.setOnHalted(() -> handlePlaybackFailure(
                    selected, path, player, "HALTED 상태", player.getError()));
            media.setOnError(() -> handlePlaybackFailure(
                    selected, path, player, "Media 비동기 오류", media.getError()));
            player.currentTimeProperty().addListener((ignored, previous, current) -> {
                if (musicPlayer == player && isUsablePosition(current)) {
                    lastMusicPosition = current;
                }
            });
            musicPlayer = player;
            lastMusicPosition = normalizePosition(resumeAt, media.getDuration());
        } catch (MediaException exception) {
            log.error("배경음악 재생기를 만들지 못했습니다. scene={}, path={}", selected, path, exception);
            musicPlayer = null;
        } finally {
            stopAndDispose(previousPlayer);
        }
    }

    private void startReadyPlayer(MediaPlayer player, Duration requestedPosition) {
        if (musicPlayer != player) {
            return;
        }
        Duration resumeAt = normalizePosition(requestedPosition, player.getMedia().getDuration());
        if (resumeAt.greaterThan(Duration.ZERO)) {
            player.seek(resumeAt);
        }
        player.play();
    }

    private void handlePlaybackFailure(
            AudioScene failedScene,
            String path,
            MediaPlayer failedPlayer,
            String reason,
            MediaException exception) {
        if (musicPlayer != failedPlayer || scene != failedScene) {
            return;
        }
        if (exception == null) {
            log.error("배경음악 재생 오류: {}. scene={}, path={}", reason, failedScene, path);
        } else {
            log.error("배경음악 재생 오류: {}. scene={}, path={}", reason, failedScene, path, exception);
        }
        if (musicRecoveryAttempted) {
            log.error("현재 장면의 배경음악 자동 복구를 이미 한 번 시도했습니다. scene={}", failedScene);
            return;
        }
        Duration resumeAt = currentMusicPosition();
        musicRecoveryAttempted = true;
        replaceMusicPlayer(failedScene, resumeAt);
    }

    private void playEffectOnJavaFxThread(SoundCue cue) {
        if (cue == null || settings.effectiveEffectsVolume() <= 0.0) {
            return;
        }
        String path = EFFECT_PATHS.get(cue);
        if (path == null) {
            return;
        }
        AudioClip clip = effectCache.computeIfAbsent(cue, ignored -> loadClip(cue, path));
        if (clip == null || clip.isPlaying() || !rateLimiter.tryAcquire(cue)) {
            return;
        }
        clip.play(settings.effectiveEffectsVolume());
    }

    private AudioClip loadClip(SoundCue cue, String path) {
        URL resource = GameAudioService.class.getResource(path);
        if (resource == null) {
            log.error("효과음 자산을 찾을 수 없습니다. path={}", path);
            return null;
        }
        try {
            AudioClip clip = new AudioClip(resource.toExternalForm());
            clip.setPriority(effectPriority(cue));
            return clip;
        } catch (MediaException exception) {
            log.error("효과음 재생기를 만들지 못했습니다. path={}", path, exception);
            return null;
        }
    }

    static int effectPriority(SoundCue cue) {
        return switch (Objects.requireNonNull(cue, "cue")) {
            case LEVEL_UP, GUARD, CHEST_OPENED, DEFEAT -> IMPORTANT_EFFECT_PRIORITY;
            default -> ATTACK_EFFECT_PRIORITY;
        };
    }

    private Duration currentMusicPosition() {
        MediaPlayer player = musicPlayer;
        if (player == null) {
            return lastMusicPosition;
        }
        if (player.getStatus() == MediaPlayer.Status.STOPPED
                || player.getStatus() == MediaPlayer.Status.HALTED) {
            return lastMusicPosition;
        }
        Duration current = player.getCurrentTime();
        return isUsablePosition(current) ? current : lastMusicPosition;
    }

    private boolean requiresMusicRestart() {
        if (musicPlayer == null) {
            return true;
        }
        MediaPlayer.Status status = musicPlayer.getStatus();
        return status == MediaPlayer.Status.STOPPED || status == MediaPlayer.Status.HALTED;
    }

    private static Duration normalizePosition(Duration requested, Duration total) {
        if (!isUsablePosition(requested)) {
            return Duration.ZERO;
        }
        if (!isUsablePosition(total) || total.equals(Duration.ZERO)) {
            return requested;
        }
        return Duration.millis(requested.toMillis() % total.toMillis());
    }

    private static boolean isUsablePosition(Duration position) {
        return position != null
                && !position.isUnknown()
                && !position.isIndefinite()
                && position.greaterThanOrEqualTo(Duration.ZERO);
    }

    private static String musicPath(AudioScene selected) {
        return switch (selected) {
            case LOBBY -> "/assets/audio/bgm-lobby.wav";
            case COMBAT -> "/assets/audio/bgm-combat.wav";
            case SILENT -> null;
        };
    }

    private void clearEffectCache() {
        effectCache.values().forEach(AudioClip::stop);
        effectCache.clear();
    }

    private void stopMusic() {
        MediaPlayer player = detachCurrentMusicPlayer();
        stopAndDispose(player);
    }

    private MediaPlayer detachCurrentMusicPlayer() {
        MediaPlayer player = musicPlayer;
        musicPlayer = null;
        if (player != null) {
            detachPlayerCallbacks(player);
        }
        return player;
    }

    private void stopAndDispose(MediaPlayer player) {
        if (player == null) {
            return;
        }
        try {
            player.stop();
        } catch (RuntimeException exception) {
            log.warn("배경음악 재생기를 중지하지 못했습니다.", exception);
        }
        mediaPlayerDisposer.dispose(player);
    }

    private static void detachPlayerCallbacks(MediaPlayer player) {
        player.setOnReady(null);
        player.setOnError(null);
        player.setOnStopped(null);
        player.setOnHalted(null);
        player.getMedia().setOnError(null);
    }

    private static void dispatch(Runnable operation) {
        if (Platform.isFxApplicationThread()) {
            operation.run();
        } else {
            Platform.runLater(operation);
        }
    }
}
