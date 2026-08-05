package kr.vamsur.desktop.configuration;

import java.util.Objects;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import kr.vamsur.desktop.client.DesktopApiClient;
import kr.vamsur.desktop.view.GameView;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX lifecycle adapter started after the Spring Boot context is ready.
 */
public final class DesktopLauncher extends Application {
    private static volatile ConfigurableApplicationContext sharedApplicationContext;

    private ConfigurableApplicationContext applicationContext;
    private GameView gameView;

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
        DesktopApiClient desktopApiClient = applicationContext.getBean(DesktopApiClient.class);

        gameView = new GameView(desktopApiClient);
        Scene scene = new Scene(gameView, 1280, 720);
        gameView.installInputHandlers(scene);

        stage.setTitle("조선 야행");
        stage.setMinWidth(960);
        stage.setMinHeight(540);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.focusedProperty().addListener((ignored, wasFocused, isFocused) -> {
            if (!isFocused) {
                gameView.clearInput();
            }
        });
        stage.show();

        gameView.startLoop();
    }

    @Override
    public void stop() {
        if (gameView != null) {
            gameView.stopLoop();
        }
    }
}
