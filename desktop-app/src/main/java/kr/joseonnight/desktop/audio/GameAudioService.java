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
import kr.joseonnight.desktop.gameplay.GameSnapshot;
import kr.joseonnight.desktop.gameplay.SoundCue;
import kr.joseonnight.desktop.gameplay.SoundEventSnapshot;
import kr.joseonnight.desktop.settings.AudioSettings;

/** JavaFX media facade with graceful no-audio fallbacks for source-only development builds. */
public final class GameAudioService implements AutoCloseable {
    static final Map<SoundCue, String> EFFECT_PATHS = Map.ofEntries(
            Map.entry(SoundCue.LEVEL_UP, "/assets/audio/sfx-level-up.wav"),
            Map.entry(SoundCue.GUARD, "/assets/audio/sfx-guard.wav"),
            Map.entry(SoundCue.CHEST_OPENED, "/assets/audio/sfx-chest.wav"),
            Map.entry(SoundCue.VICTORY, "/assets/audio/sfx-level-up.wav"),
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

    private AudioSettings settings = AudioSettings.defaults();
    private AudioScene scene = AudioScene.SILENT;
    private MediaPlayer musicPlayer;

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
            case LOBBY -> "/assets/audio/bgm-lobby.wav";
            case COMBAT -> "/assets/audio/bgm-combat.wav";
            case SILENT -> null;
        };
        URL resource = path == null ? null : GameAudioService.class.getResource(path);
        if (resource == null) {
            return;
        }
        try {
            MediaPlayer player = new MediaPlayer(new Media(resource.toExternalForm()));
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setVolume(settings.effectiveMusicVolume());
            player.play();
            musicPlayer = player;
        } catch (MediaException ignored) {
            musicPlayer = null;
        }
    }

    private void playEffectOnJavaFxThread(SoundCue cue) {
        if (cue == null || settings.effectiveEffectsVolume() <= 0.0) {
            return;
        }
        String path = EFFECT_PATHS.get(cue);
        if (path == null) {
            return;
        }
        AudioClip clip = effectCache.computeIfAbsent(cue, ignored -> loadClip(path));
        if (clip != null && rateLimiter.tryAcquire(cue)) {
            clip.play(settings.effectiveEffectsVolume());
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
