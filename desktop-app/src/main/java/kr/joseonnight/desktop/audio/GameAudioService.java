package kr.joseonnight.desktop.audio;

import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import kr.joseonnight.desktop.gameplay.GameSnapshot;
import kr.joseonnight.desktop.gameplay.SoundEventSnapshot;
import kr.joseonnight.desktop.settings.AudioSettings;

/** JavaFX media facade with graceful no-audio fallbacks for source-only development builds. */
public final class GameAudioService implements AutoCloseable {
    private static final Map<String, String> EFFECT_PATHS = Map.ofEntries(
            Map.entry("LEVEL_UP", "/assets/audio/sfx-level-up.wav"),
            Map.entry("GUARD", "/assets/audio/sfx-guard.wav"),
            Map.entry("GUARD_TRIGGERED", "/assets/audio/sfx-guard.wav"),
            Map.entry("SHIELD", "/assets/audio/sfx-guard.wav"),
            Map.entry("SHIELD_BLOCKED", "/assets/audio/sfx-guard.wav"),
            Map.entry("CHEST", "/assets/audio/sfx-chest.wav"),
            Map.entry("CHEST_OPENED", "/assets/audio/sfx-chest.wav"),
            Map.entry("CHEST_REWARD", "/assets/audio/sfx-chest.wav"),
            Map.entry("VICTORY", "/assets/audio/sfx-level-up.wav"),
            Map.entry("DEFEAT", "/assets/audio/sfx-defeat.wav"),
            Map.entry("PLAYER_DEFEATED", "/assets/audio/sfx-defeat.wav"));

    private final SoundEventDeduplicator deduplicator = new SoundEventDeduplicator();
    private final Map<String, AudioClip> effectCache = new HashMap<>();

    private AudioSettings settings = AudioSettings.defaults();
    private AudioScene scene = AudioScene.SILENT;
    private MediaPlayer musicPlayer;

    public void applySettings(AudioSettings newSettings) {
        dispatch(() -> {
            settings = Objects.requireNonNull(newSettings, "newSettings");
            if (musicPlayer != null) {
                musicPlayer.setVolume(settings.effectiveVolume());
            }
        });
    }

    public void switchScene(AudioScene nextScene) {
        dispatch(() -> switchSceneOnJavaFxThread(nextScene));
    }

    public void beginNewGame() {
        deduplicator.reset();
        switchScene(AudioScene.COMBAT);
    }

    public void consume(GameSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        for (SoundEventSnapshot event : deduplicator.selectNew(snapshot.soundEvents())) {
            dispatch(() -> playEffectOnJavaFxThread(event.type()));
        }
    }

    @Override
    public void close() {
        dispatch(() -> {
            stopMusic();
            effectCache.clear();
        });
    }

    private void switchSceneOnJavaFxThread(AudioScene nextScene) {
        AudioScene selected = Objects.requireNonNull(nextScene, "nextScene");
        if (scene == selected) {
            return;
        }
        scene = selected;
        stopMusic();
        String path = switch (selected) {
            case LOBBY, COMBAT -> "/assets/audio/bgm-moonlit-ruins.wav";
            case SILENT -> null;
        };
        URL resource = path == null ? null : GameAudioService.class.getResource(path);
        if (resource == null) {
            return;
        }
        try {
            MediaPlayer player = new MediaPlayer(new Media(resource.toExternalForm()));
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setVolume(settings.effectiveVolume());
            player.play();
            musicPlayer = player;
        } catch (MediaException ignored) {
            musicPlayer = null;
        }
    }

    private void playEffectOnJavaFxThread(String eventType) {
        if (eventType == null || eventType.isBlank() || settings.effectiveVolume() <= 0.0) {
            return;
        }
        String normalized = eventType.toUpperCase(Locale.ROOT);
        String path = EFFECT_PATHS.get(normalized);
        if (path == null) {
            return;
        }
        AudioClip clip = effectCache.computeIfAbsent(normalized, ignored -> loadClip(path));
        if (clip != null) {
            clip.play(settings.effectiveVolume());
        }
    }

    private static AudioClip loadClip(String path) {
        URL resource = GameAudioService.class.getResource(path);
        if (resource == null) {
            return null;
        }
        try {
            return new AudioClip(resource.toExternalForm());
        } catch (MediaException ignored) {
            return null;
        }
    }

    private void stopMusic() {
        MediaPlayer player = musicPlayer;
        musicPlayer = null;
        if (player != null) {
            player.stop();
            player.dispose();
        }
    }

    private static void dispatch(Runnable operation) {
        if (Platform.isFxApplicationThread()) {
            operation.run();
        } else {
            Platform.runLater(operation);
        }
    }
}
