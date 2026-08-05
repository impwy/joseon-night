package kr.joseonnight.desktop.configuration;

import java.util.Objects;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
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
    private static volatile ConfigurableApplicationContext sharedApplicationContext;

    private ConfigurableApplicationContext applicationContext;
    private DesktopRootView rootView;

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

        rootView = new DesktopRootView(
                authApiClient,
                memberApiClient,
                desktopApiClient,
                new GameAudioService(),
                getHostServices()::showDocument);
        Scene scene = new Scene(rootView, 1280, 720);
        rootView.installInputHandlers(scene);

        stage.setTitle("조선 야행");
        stage.setMinWidth(960);
        stage.setMinHeight(540);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.focusedProperty().addListener((ignored, wasFocused, isFocused) -> {
            if (!isFocused) {
                rootView.clearInput();
            }
        });
        stage.show();

        rootView.start();
    }

    @Override
    public void stop() {
        if (rootView != null) {
            rootView.close();
        }
    }
}
