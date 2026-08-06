package kr.joseonnight.desktop.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import kr.joseonnight.desktop.audio.GameAudioService;
import kr.joseonnight.desktop.client.AuthApiClient;
import kr.joseonnight.desktop.client.DesktopApiClient;
import kr.joseonnight.desktop.client.MemberApiClient;
import kr.joseonnight.desktop.view.DesktopRootView;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX lifecycle adapter started after the Spring Boot context is ready.
 */
public final class DesktopLauncher extends Application {
    private static final Duration OUTPUT_CHANGE_DEBOUNCE = Duration.millis(250.0);
    static final double MIN_WINDOW_WIDTH = 960.0;
    static final double MIN_WINDOW_HEIGHT = 540.0;
    static final double MAX_WINDOW_WIDTH = 3_840.0;
    static final double MAX_WINDOW_HEIGHT = 2_160.0;

    private static volatile ConfigurableApplicationContext sharedApplicationContext;

    private ConfigurableApplicationContext applicationContext;
    private DesktopRootView rootView;
    private PauseTransition outputChangeDebounce;
    private OutputTarget outputTarget;

    public static void launch(ConfigurableApplicationContext context, String[] args) {
        sharedApplicationContext = Objects.requireNonNull(context, "context");
        try {
            launch(DesktopLauncher.class, args);
        } finally {
            sharedApplicationContext = null;
        }
    }

    @Override
    public void init() {
        applicationContext = Objects.requireNonNull(
                sharedApplicationContext,
                "Spring Boot must start before JavaFX"
        );
    }

    @Override
    public void start(Stage stage) {
        AuthApiClient authApiClient = applicationContext.getBean(AuthApiClient.class);
        MemberApiClient memberApiClient = applicationContext.getBean(MemberApiClient.class);
        DesktopApiClient desktopApiClient = applicationContext.getBean(DesktopApiClient.class);
        GameAudioService audioService = new GameAudioService();

        rootView = new DesktopRootView(
                authApiClient,
                memberApiClient,
                desktopApiClient,
                audioService,
                getHostServices()::showDocument);
        Scene scene = new Scene(rootView, 1280, 720);
        rootView.installInputHandlers(scene);

        stage.setTitle("조선 야행");
        stage.setMinWidth(MIN_WINDOW_WIDTH);
        stage.setMinHeight(MIN_WINDOW_HEIGHT);
        stage.setMaxWidth(MAX_WINDOW_WIDTH);
        stage.setMaxHeight(MAX_WINDOW_HEIGHT);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.focusedProperty().addListener((ignored, wasFocused, isFocused) -> {
            if (!isFocused) {
                rootView.clearInput();
            }
        });
        stage.show();
        monitorOutputChanges(stage, audioService);

        rootView.start();
    }

    @Override
    public void stop() {
        if (outputChangeDebounce != null) {
            outputChangeDebounce.stop();
        }
        if (rootView != null) {
            rootView.close();
        }
    }

    private void monitorOutputChanges(Stage stage, GameAudioService audioService) {
        outputTarget = resolveOutputTarget(stage);
        outputChangeDebounce = new PauseTransition(OUTPUT_CHANGE_DEBOUNCE);
        outputChangeDebounce.setOnFinished(ignored -> {
            OutputTarget nextTarget = resolveOutputTarget(stage);
            if (!nextTarget.equals(outputTarget)) {
                outputTarget = nextTarget;
                audioService.refreshOutputDevice();
            }
        });

        InvalidationListener changeListener = ignored -> outputChangeDebounce.playFromStart();
        stage.xProperty().addListener(changeListener);
        stage.yProperty().addListener(changeListener);
        stage.widthProperty().addListener(changeListener);
        stage.heightProperty().addListener(changeListener);
        stage.outputScaleXProperty().addListener(changeListener);
        stage.outputScaleYProperty().addListener(changeListener);
    }

    private static OutputTarget resolveOutputTarget(Stage stage) {
        Rectangle2D windowBounds = new Rectangle2D(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        List<Screen> candidates = new ArrayList<>(Screen.getScreensForRectangle(windowBounds));
        if (candidates.isEmpty()) {
            candidates.add(Screen.getPrimary());
        }
        List<Rectangle2D> candidateBounds = candidates.stream().map(Screen::getBounds).toList();
        Screen selected = candidates.get(largestIntersectionIndex(windowBounds, candidateBounds));
        return new OutputTarget(
                selected.getBounds(),
                selected.getOutputScaleX(),
                selected.getOutputScaleY(),
                stage.getOutputScaleX(),
                stage.getOutputScaleY());
    }

    static int largestIntersectionIndex(Rectangle2D windowBounds, List<Rectangle2D> screenBounds) {
        Objects.requireNonNull(windowBounds, "windowBounds");
        Objects.requireNonNull(screenBounds, "screenBounds");
        if (screenBounds.isEmpty()) {
            throw new IllegalArgumentException("screenBounds must not be empty");
        }
        int selectedIndex = 0;
        double largestArea = intersectionArea(windowBounds, screenBounds.getFirst());
        for (int index = 1; index < screenBounds.size(); index++) {
            double candidateArea = intersectionArea(windowBounds, screenBounds.get(index));
            if (candidateArea > largestArea) {
                selectedIndex = index;
                largestArea = candidateArea;
            }
        }
        return selectedIndex;
    }

    private static double intersectionArea(Rectangle2D first, Rectangle2D second) {
        double width = Math.max(0.0, Math.min(first.getMaxX(), second.getMaxX())
                - Math.max(first.getMinX(), second.getMinX()));
        double height = Math.max(0.0, Math.min(first.getMaxY(), second.getMaxY())
                - Math.max(first.getMinY(), second.getMinY()));
        return width * height;
    }

    private record OutputTarget(
            Rectangle2D screenBounds,
            double screenScaleX,
            double screenScaleY,
            double windowScaleX,
            double windowScaleY) {
    }
}
