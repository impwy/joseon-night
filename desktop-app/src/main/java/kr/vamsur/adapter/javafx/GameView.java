package kr.vamsur.adapter.javafx;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import kr.vamsur.application.gameplay.provided.EntitySnapshot;
import kr.vamsur.application.gameplay.provided.GameSnapshot;
import kr.vamsur.application.gameplay.provided.GameUseCase;
import kr.vamsur.domain.gameplay.GamePhase;
import kr.vamsur.domain.gameplay.InputState;
import kr.vamsur.domain.gameplay.UpgradeType;

/**
 * Canvas combat renderer with JavaFX controls layered above it.
 */
public final class GameView extends StackPane {
    private static final double FIXED_STEP_SECONDS = 1.0 / 60.0;
    private static final int MAX_CATCH_UP_STEPS = 15;
    private static final double MAX_FRAME_SECONDS = 0.25;
    private static final double GROUND_TILE_SIZE = 64.0;
    private static final double ACTOR_SIZE = 56.0;
    private static final String PANEL_STYLE = "-fx-background-color: rgba(9, 15, 24, 0.92);"
            + "-fx-background-radius: 14; -fx-border-color: #c8a35a; -fx-border-radius: 14;"
            + "-fx-border-width: 2;";
    private static final String TITLE_STYLE = "-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: #f4dca3;";
    private static final String TEXT_STYLE = "-fx-font-size: 16px; -fx-text-fill: #e9edf2;";
    private static final String BUTTON_STYLE = "-fx-background-color: #a7353f; -fx-text-fill: white;"
            + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 8;"
            + "-fx-padding: 10 18 10 18;";

    private final GameUseCase game;
    private final Consumer<GameSnapshot> statusPublisher;
    private final SpriteAtlas sprites = new SpriteAtlas();
    private final Canvas canvas = new Canvas();
    private final VBox lobbyPanel = new VBox(16);
    private final HBox hudPanel = new HBox(22);
    private final VBox upgradePanel = new VBox(12);
    private final VBox resultPanel = new VBox(16);
    private final Label timeLabel = hudLabel();
    private final Label levelLabel = hudLabel();
    private final Label experienceLabel = hudLabel();
    private final Label killLabel = hudLabel();
    private final Label resultTitle = new Label();
    private final Label resultSummary = new Label();
    private final AnimationTimer gameLoop;

    private List<UpgradeType> displayedChoices = List.of();
    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;
    private long previousFrameNanos;
    private double accumulatorSeconds;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "The JavaFX view intentionally keeps the Spring-owned game use-case collaborator.")
    public GameView(GameUseCase game, Consumer<GameSnapshot> statusPublisher) {
        this.game = game;
        this.statusPublisher = statusPublisher;

        setPrefSize(1280, 720);
        setStyle("-fx-background-color: #111923;");
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.setMouseTransparent(true);

        configureLobby();
        configureHud();
        configureUpgradePanel();
        configureResultPanel();

        StackPane overlay = new StackPane(lobbyPanel, hudPanel, upgradePanel, resultPanel);
        overlay.setPickOnBounds(false);
        getChildren().addAll(canvas, overlay);

        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateFrame(now);
            }
        };

        publishAndRefresh();
    }

    public void installInputHandlers(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> updateKey(event, true));
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> updateKey(event, false));
    }

    public void startLoop() {
        previousFrameNanos = 0L;
        accumulatorSeconds = 0.0;
        gameLoop.start();
    }

    public void stopLoop() {
        gameLoop.stop();
        clearInput();
    }

    public void clearInput() {
        up = false;
        down = false;
        left = false;
        right = false;
        game.setInput(InputState.idle());
    }

    private void configureLobby() {
        Label title = new Label("조선 야행");
        title.setStyle(TITLE_STYLE);

        Label subtitle = new Label("달빛 폐허에서 그림자 도깨비를 피해 5분을 버티세요.");
        subtitle.setStyle(TEXT_STYLE);
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(440);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label controls = new Label("이동: W A S D  ·  공격: 자동");
        controls.setStyle("-fx-font-size: 15px; -fx-text-fill: #afbed0;");

        Button startButton = actionButton("야행 시작");
        startButton.setOnAction(ignored -> startNewGame());

        lobbyPanel.getChildren().addAll(title, subtitle, controls, startButton);
        lobbyPanel.setAlignment(Pos.CENTER);
        lobbyPanel.setPadding(new Insets(34));
        lobbyPanel.setMaxSize(560, 330);
        lobbyPanel.setStyle(PANEL_STYLE);
        StackPane.setAlignment(lobbyPanel, Pos.CENTER);
    }

    private void configureHud() {
        hudPanel.getChildren().addAll(timeLabel, levelLabel, experienceLabel, killLabel);
        hudPanel.setAlignment(Pos.CENTER_LEFT);
        hudPanel.setPadding(new Insets(10, 16, 10, 16));
        hudPanel.setMaxHeight(48);
        hudPanel.setStyle("-fx-background-color: rgba(5, 9, 15, 0.78); -fx-background-radius: 8;");
        hudPanel.setMouseTransparent(true);
        StackPane.setAlignment(hudPanel, Pos.TOP_LEFT);
        StackPane.setMargin(hudPanel, new Insets(18));
    }

    private void configureUpgradePanel() {
        upgradePanel.setAlignment(Pos.CENTER);
        upgradePanel.setPadding(new Insets(28));
        upgradePanel.setMaxSize(600, 500);
        upgradePanel.setStyle(PANEL_STYLE);
        StackPane.setAlignment(upgradePanel, Pos.CENTER);
    }

    private void configureResultPanel() {
        resultTitle.setStyle(TITLE_STYLE);
        resultSummary.setStyle(TEXT_STYLE);
        resultSummary.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button restartButton = actionButton("다시 시작");
        restartButton.setOnAction(ignored -> startNewGame());

        resultPanel.getChildren().addAll(resultTitle, resultSummary, restartButton);
        resultPanel.setAlignment(Pos.CENTER);
        resultPanel.setPadding(new Insets(34));
        resultPanel.setMaxSize(500, 300);
        resultPanel.setStyle(PANEL_STYLE);
        StackPane.setAlignment(resultPanel, Pos.CENTER);
    }

    private void startNewGame() {
        clearInput();
        displayedChoices = List.of();
        game.startNewGame();
        accumulatorSeconds = 0.0;
        publishAndRefresh();
    }

    private void updateFrame(long now) {
        if (previousFrameNanos == 0L) {
            previousFrameNanos = now;
            publishAndRefresh();
            return;
        }

        double frameSeconds = Math.min((now - previousFrameNanos) / 1_000_000_000.0, MAX_FRAME_SECONDS);
        previousFrameNanos = now;
        accumulatorSeconds += Math.max(0.0, frameSeconds);

        int steps = 0;
        while (accumulatorSeconds >= FIXED_STEP_SECONDS && steps < MAX_CATCH_UP_STEPS) {
            game.setInput(new InputState(up, down, left, right));
            game.tick(FIXED_STEP_SECONDS);
            accumulatorSeconds -= FIXED_STEP_SECONDS;
            steps++;
        }
        if (steps == MAX_CATCH_UP_STEPS) {
            accumulatorSeconds = 0.0;
        }

        publishAndRefresh();
    }

    private void publishAndRefresh() {
        GameSnapshot snapshot = game.snapshot();
        statusPublisher.accept(snapshot);
        render(snapshot);
        updateInterface(snapshot);
    }

    private void updateInterface(GameSnapshot snapshot) {
        GamePhase phase = snapshot.phase();
        boolean lobby = phase == GamePhase.LOBBY;
        boolean active = phase == GamePhase.RUNNING || phase == GamePhase.LEVEL_UP;
        boolean choosingUpgrade = phase == GamePhase.LEVEL_UP;
        boolean finished = phase == GamePhase.VICTORY || phase == GamePhase.DEFEAT;

        lobbyPanel.setVisible(lobby);
        lobbyPanel.setManaged(lobby);
        hudPanel.setVisible(active);
        hudPanel.setManaged(active);
        upgradePanel.setVisible(choosingUpgrade);
        upgradePanel.setManaged(choosingUpgrade);
        resultPanel.setVisible(finished);
        resultPanel.setManaged(finished);

        int seconds = Math.max(0, (int) Math.ceil(snapshot.remainingSeconds()));
        timeLabel.setText("남은 시간  %02d:%02d".formatted(seconds / 60, seconds % 60));
        levelLabel.setText("레벨  %d".formatted(snapshot.level()));
        experienceLabel.setText("혼불  %d / %d".formatted(
                snapshot.experience(), snapshot.experienceToNextLevel()));
        killLabel.setText("퇴치  %d".formatted(snapshot.killCount()));

        if (choosingUpgrade && !displayedChoices.equals(snapshot.upgradeChoices())) {
            rebuildUpgradeChoices(snapshot.upgradeChoices());
        }
        if (finished) {
            resultTitle.setText(phase == GamePhase.VICTORY ? "새벽을 맞았습니다" : "야행이 끝났습니다");
            resultSummary.setText("생존 시간 %.1f초%n그림자 도깨비 %d마리 퇴치"
                    .formatted(snapshot.elapsedSeconds(), snapshot.killCount()));
        }
    }

    private void rebuildUpgradeChoices(List<UpgradeType> choices) {
        displayedChoices = List.copyOf(choices);
        Label title = new Label("부적의 힘을 고르세요");
        title.setStyle("-fx-font-size: 25px; -fx-font-weight: bold; -fx-text-fill: #f4dca3;");
        Label paused = new Label("선택하는 동안 야행은 멈춥니다.");
        paused.setStyle("-fx-font-size: 14px; -fx-text-fill: #afbed0;");

        upgradePanel.getChildren().setAll(title, paused);
        for (UpgradeType choice : choices) {
            Button button = new Button(choice.label() + "\n" + choice.description());
            button.setWrapText(true);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setPrefHeight(72);
            button.setStyle("-fx-background-color: #26364a; -fx-text-fill: #f5f5f5;"
                    + "-fx-font-size: 15px; -fx-background-radius: 8; -fx-border-color: #596f88;"
                    + "-fx-border-radius: 8; -fx-padding: 10 16 10 16;");
            button.setOnAction(ignored -> {
                game.chooseUpgrade(choice);
                publishAndRefresh();
            });
            upgradePanel.getChildren().add(button);
        }
    }

    private void render(GameSnapshot snapshot) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setImageSmoothing(false);
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0.0 || height <= 0.0) {
            return;
        }

        EntitySnapshot player = snapshot.player();
        double cameraX = player == null ? 0.0 : player.x();
        double cameraY = player == null ? 0.0 : player.y();
        drawGround(graphics, width, height, cameraX, cameraY);

        for (EntitySnapshot flame : snapshot.soulFlames()) {
            drawSoulFlame(graphics, flame, width, height, cameraX, cameraY);
        }
        for (EntitySnapshot projectile : snapshot.projectiles()) {
            drawTalisman(graphics, projectile, width, height, cameraX, cameraY);
        }
        for (EntitySnapshot enemy : snapshot.enemies()) {
            drawEnemy(graphics, enemy, width, height, cameraX, cameraY);
        }
        if (player != null) {
            drawPlayer(graphics, player, width, height, cameraX, cameraY);
        }
    }

    private void drawGround(
            GraphicsContext graphics,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        graphics.setFill(Color.web("#111923"));
        graphics.fillRect(0, 0, width, height);

        double originX = width / 2.0 - cameraX;
        double originY = height / 2.0 - cameraY;
        double firstX = positiveModulo(originX, GROUND_TILE_SIZE) - GROUND_TILE_SIZE;
        double firstY = positiveModulo(originY, GROUND_TILE_SIZE) - GROUND_TILE_SIZE;
        Image ground = sprites.ground();

        int row = 0;
        for (double y = firstY; y < height; y += GROUND_TILE_SIZE, row++) {
            int column = 0;
            for (double x = firstX; x < width; x += GROUND_TILE_SIZE, column++) {
                if (ground != null) {
                    graphics.drawImage(ground, x, y, GROUND_TILE_SIZE, GROUND_TILE_SIZE);
                } else {
                    boolean alternate = ((row + column) & 1) == 0;
                    graphics.setFill(Color.web(alternate ? "#172431" : "#1b2937"));
                    graphics.fillRect(x, y, GROUND_TILE_SIZE, GROUND_TILE_SIZE);
                    graphics.setStroke(Color.web("#253747", 0.55));
                    graphics.strokeRect(x, y, GROUND_TILE_SIZE, GROUND_TILE_SIZE);
                }
            }
        }
    }

    private void drawPlayer(
            GraphicsContext graphics,
            EntitySnapshot entity,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        double screenX = screenX(entity, width, cameraX);
        double screenY = screenY(entity, height, cameraY);
        Image sprite = sprites.player();
        if (sprite != null) {
            drawCentered(graphics, sprite, screenX, screenY, ACTOR_SIZE, entity.rotationDegrees());
            return;
        }
        graphics.setFill(Color.web("#304b65"));
        graphics.fillOval(screenX - 20, screenY - 22, 40, 44);
        graphics.setStroke(Color.web("#e3b960"));
        graphics.setLineWidth(3);
        graphics.strokeOval(screenX - 20, screenY - 22, 40, 44);
        graphics.setFill(Color.web("#d14846"));
        graphics.fillRect(screenX - 23, screenY - 6, 46, 8);
    }

    private void drawEnemy(
            GraphicsContext graphics,
            EntitySnapshot entity,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        double screenX = screenX(entity, width, cameraX);
        double screenY = screenY(entity, height, cameraY);
        Image sprite = sprites.enemy();
        if (sprite != null) {
            drawCentered(graphics, sprite, screenX, screenY, ACTOR_SIZE, entity.rotationDegrees());
            return;
        }
        graphics.setFill(Color.web("#6c3f75"));
        graphics.fillOval(screenX - 19, screenY - 18, 38, 38);
        graphics.setFill(Color.web("#9c5e94"));
        graphics.fillPolygon(
                new double[] {screenX - 17, screenX - 8, screenX - 2},
                new double[] {screenY - 12, screenY - 32, screenY - 17},
                3);
        graphics.fillPolygon(
                new double[] {screenX + 17, screenX + 8, screenX + 2},
                new double[] {screenY - 12, screenY - 32, screenY - 17},
                3);
        graphics.setFill(Color.web("#ffdf73"));
        graphics.fillOval(screenX - 10, screenY - 5, 5, 5);
        graphics.fillOval(screenX + 5, screenY - 5, 5, 5);
    }

    private void drawTalisman(
            GraphicsContext graphics,
            EntitySnapshot entity,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        double screenX = screenX(entity, width, cameraX);
        double screenY = screenY(entity, height, cameraY);
        Image sprite = sprites.talisman();
        if (sprite != null) {
            drawCentered(graphics, sprite, screenX, screenY, 32, entity.rotationDegrees());
            return;
        }
        graphics.save();
        graphics.translate(screenX, screenY);
        graphics.rotate(entity.rotationDegrees());
        graphics.setFill(Color.web("#f1d17b"));
        graphics.fillRect(-11, -6, 22, 12);
        graphics.setFill(Color.web("#b53838"));
        graphics.fillRect(-2, -5, 4, 10);
        graphics.restore();
    }

    private void drawSoulFlame(
            GraphicsContext graphics,
            EntitySnapshot entity,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        double screenX = screenX(entity, width, cameraX);
        double screenY = screenY(entity, height, cameraY);
        Image sprite = sprites.soulFlame();
        if (sprite != null) {
            drawCentered(graphics, sprite, screenX, screenY, 30, entity.rotationDegrees());
            return;
        }
        graphics.setFill(Color.web("#65e4df", 0.35));
        graphics.fillOval(screenX - 13, screenY - 15, 26, 30);
        graphics.setFill(Color.web("#9afaf1"));
        graphics.fillOval(screenX - 7, screenY - 9, 14, 18);
    }

    private static void drawCentered(
            GraphicsContext graphics,
            Image image,
            double x,
            double y,
            double size,
            double rotationDegrees) {
        graphics.save();
        graphics.translate(x, y);
        graphics.rotate(rotationDegrees);
        graphics.drawImage(image, -size / 2.0, -size / 2.0, size, size);
        graphics.restore();
    }

    private static double screenX(EntitySnapshot entity, double width, double cameraX) {
        return width / 2.0 + entity.x() - cameraX;
    }

    private static double screenY(EntitySnapshot entity, double height, double cameraY) {
        return height / 2.0 + entity.y() - cameraY;
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0 ? result + modulus : result;
    }

    private void updateKey(KeyEvent event, boolean pressed) {
        KeyCode code = event.getCode();
        boolean handled = true;
        switch (code) {
            case W -> up = pressed;
            case S -> down = pressed;
            case A -> left = pressed;
            case D -> right = pressed;
            default -> handled = false;
        }
        if (handled) {
            event.consume();
        }
    }

    private static Label hudLabel() {
        Label label = new Label();
        label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #f4e7c5;");
        return label;
    }

    private static Button actionButton(String text) {
        Button button = new Button(text);
        button.setStyle(BUTTON_STYLE);
        return button;
    }
}
