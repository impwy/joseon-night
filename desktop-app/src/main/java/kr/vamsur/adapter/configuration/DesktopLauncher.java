package kr.vamsur.adapter.configuration;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import kr.vamsur.adapter.javafx.GameView;
import kr.vamsur.adapter.webapi.GameStatusPublisher;
import kr.vamsur.application.gameplay.provided.GameUseCase;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Composition root that starts JavaFX and the local Spring/Armeria runtime in the same JVM.
 */
public final class DesktopLauncher extends Application {
    private ConfigurableApplicationContext applicationContext;
    private GameView gameView;

    public static void main(String[] args) {
        launch(DesktopLauncher.class, args);
    }

    @Override
    public void init() {
        String[] arguments = getParameters().getRaw().toArray(String[]::new);
        applicationContext = new SpringApplicationBuilder(DesktopSpringConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(arguments);
    }

    @Override
    public void start(Stage stage) {
        GameUseCase gameUseCase = applicationContext.getBean(GameUseCase.class);
        GameStatusPublisher statusPublisher = applicationContext.getBean(GameStatusPublisher.class);

        gameView = new GameView(gameUseCase, statusPublisher::publish);
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
        if (applicationContext != null) {
            applicationContext.close();
        }
    }
}
