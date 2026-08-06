package kr.joseonnight.desktop.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import kr.joseonnight.desktop.client.DesktopApiClient;
import kr.joseonnight.desktop.gameplay.ChestIndicatorSnapshot;
import kr.joseonnight.desktop.gameplay.ChestSnapshot;
import kr.joseonnight.desktop.gameplay.DesktopApiStatus;
import kr.joseonnight.desktop.gameplay.EntitySnapshot;
import kr.joseonnight.desktop.gameplay.GamePhase;
import kr.joseonnight.desktop.gameplay.GameSnapshot;
import kr.joseonnight.desktop.gameplay.InputState;
import kr.joseonnight.desktop.gameplay.ItemSlotSnapshot;
import kr.joseonnight.desktop.gameplay.LightningStrikeSnapshot;
import kr.joseonnight.desktop.gameplay.RewardOptionSnapshot;
import kr.joseonnight.desktop.gameplay.UpgradeType;
import kr.joseonnight.desktop.settings.TargetFps;

/**
 * Canvas combat renderer with JavaFX controls layered above it.
 */
public final class GameView extends StackPane {
    private static final double FIXED_STEP_SECONDS = 1.0 / 60.0;
    private static final int MAX_CATCH_UP_STEPS = 15;
    private static final double MAX_FRAME_SECONDS = 0.25;
    private static final double GROUND_TILE_SIZE = 64.0;
    private static final double ACTOR_SIZE = 64.0;
    private static final double PROJECTILE_SIZE = 32.0;
    private static final double SOUL_FLAME_SIZE = 32.0;
    private static final double CHEST_SIZE = 64.0;
    private static final Color BACKGROUND_TONE = Color.web("#080b10", 0.30);
    private static final String PANEL_STYLE = "-fx-background-color: rgba(9, 15, 24, 0.92);"
            + "-fx-background-radius: 14; -fx-border-color: #c8a35a; -fx-border-radius: 14;"
            + "-fx-border-width: 2;";
    private static final String TITLE_STYLE = "-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: #f4dca3;";
    private static final String TEXT_STYLE = "-fx-font-size: 16px; -fx-text-fill: #e9edf2;";
    private static final String BUTTON_STYLE = "-fx-background-color: #a7353f; -fx-text-fill: white;"
            + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 8;"
            + "-fx-padding: 10 18 10 18;";

    private final DesktopApiClient desktopApiClient;
    private final Runnable restartGame;
    private final Runnable returnToLobby;
    private final Runnable toggleSettings;
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
    private final Label barrierLabel = hudLabel();
    private final Label heartLabel = hudLabel();
    private final Label loadoutLabel = hudLabel();
    private final Button settingsButton = actionButton("설정");
    private final Label connectionLabel = new Label();
    private final Label resultTitle = new Label();
    private final Label resultSummary = new Label();
    private final AnimationTimer gameLoop;
    private final FrameRateLimiter frameRateLimiter = new FrameRateLimiter(TargetFps.FPS_60);

    private List<String> displayedOptionIds = List.of();
    private final KeyState keyState = new KeyState();
    private boolean inputEnabled;
    private long previousFrameNanos;
    private double accumulatorSeconds;

    public GameView(DesktopApiClient desktopApiClient) {
        this(desktopApiClient, desktopApiClient::startNewGame, () -> { }, () -> { });
    }

    public GameView(
            DesktopApiClient desktopApiClient,
            Runnable restartGame,
            Runnable returnToLobby) {
        this(desktopApiClient, restartGame, returnToLobby, () -> { });
    }

    public GameView(
            DesktopApiClient desktopApiClient,
            Runnable restartGame,
            Runnable returnToLobby,
            Runnable toggleSettings) {
        this.desktopApiClient = Objects.requireNonNull(desktopApiClient, "desktopApiClient");
        this.restartGame = Objects.requireNonNull(restartGame, "restartGame");
        this.returnToLobby = Objects.requireNonNull(returnToLobby, "returnToLobby");
        this.toggleSettings = Objects.requireNonNull(toggleSettings, "toggleSettings");

        setPrefSize(1280, 720);
        setStyle("-fx-background-color: #111923;");
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.setMouseTransparent(true);
        canvas.widthProperty().addListener((ignored, oldValue, newValue) -> reportViewportSize());
        canvas.heightProperty().addListener((ignored, oldValue, newValue) -> reportViewportSize());

        configureLobby();
        configureHud();
        configureConnectionStatus();
        configureUpgradePanel();
        configureResultPanel();

        StackPane overlay = new StackPane(
                lobbyPanel, hudPanel, upgradePanel, resultPanel, settingsButton, connectionLabel);
        overlay.setPickOnBounds(false);
        getChildren().addAll(canvas, overlay);

        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateFrame(now);
            }
        };

        refreshView();
    }

    public void installInputHandlers(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> updateKey(event, true));
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> updateKey(event, false));
    }

    public void startLoop() {
        previousFrameNanos = 0L;
        accumulatorSeconds = 0.0;
        frameRateLimiter.reset();
        gameLoop.start();
    }

    public void stopLoop() {
        gameLoop.stop();
        clearInput();
    }

    public void refreshFromClient() {
        GameSnapshot snapshot = desktopApiClient.snapshot();
        updateInterface(snapshot);
        updateConnectionStatus(desktopApiClient.status());
    }

    public void setTargetFps(TargetFps targetFps) {
        frameRateLimiter.setTargetFps(targetFps);
    }

    public double viewportWidth() {
        return canvas.getWidth() > 0.0 ? canvas.getWidth() : 1280.0;
    }

    public double viewportHeight() {
        return canvas.getHeight() > 0.0 ? canvas.getHeight() : 720.0;
    }

    public void clearInput() {
        keyState.clearMovement();
        desktopApiClient.setInput(InputState.idle());
    }

    public void clearInputAfterFocusLoss() {
        keyState.clearAfterFocusLoss();
        desktopApiClient.setInput(InputState.idle());
    }

    public void setInputEnabled(boolean enabled) {
        inputEnabled = enabled;
        if (!enabled) {
            clearInput();
        }
    }

    private void reportViewportSize() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width > 0.0 && height > 0.0) {
            desktopApiClient.setViewportSize(width, height);
        }
    }

    private void configureLobby() {
        Label title = new Label("조선 야행");
        title.setStyle(TITLE_STYLE);

        Label subtitle = new Label("달빛 폐허에서 쓰러질 때까지 그림자 도깨비에 맞서세요.");
        subtitle.setStyle(TEXT_STYLE);
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(440);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label controls = new Label("이동: W A S D  ·  공격: 자동");
        controls.setStyle("-fx-font-size: 15px; -fx-text-fill: #afbed0;");

        Label connecting = new Label("게임 서버에 연결하고 있습니다…");
        connecting.setStyle("-fx-font-size: 14px; -fx-text-fill: #afbed0;");

        Button lobbyButton = actionButton("로비로 돌아가기");
        lobbyButton.setOnAction(ignored -> returnToLobby.run());

        lobbyPanel.getChildren().addAll(title, subtitle, controls, connecting, lobbyButton);
        lobbyPanel.setAlignment(Pos.CENTER);
        lobbyPanel.setPadding(new Insets(34));
        lobbyPanel.setMaxSize(560, 330);
        lobbyPanel.setStyle(PANEL_STYLE);
        StackPane.setAlignment(lobbyPanel, Pos.CENTER);
    }

    private void configureHud() {
        settingsButton.setStyle("-fx-background-color: #32465c; -fx-text-fill: white;"
                + "-fx-font-size: 13px; -fx-background-radius: 7; -fx-padding: 6 12 6 12;");
        settingsButton.setOnAction(ignored -> toggleSettings.run());
        hudPanel.getChildren().addAll(
                timeLabel,
                levelLabel,
                experienceLabel,
                killLabel,
                barrierLabel,
                heartLabel,
                loadoutLabel);
        loadoutLabel.setMaxWidth(520.0);
        loadoutLabel.setWrapText(true);
        hudPanel.setAlignment(Pos.CENTER_LEFT);
        hudPanel.setPadding(new Insets(10, 16, 10, 16));
        hudPanel.setMaxHeight(80);
        hudPanel.setStyle("-fx-background-color: rgba(5, 9, 15, 0.78); -fx-background-radius: 8;");
        hudPanel.setMouseTransparent(true);
        StackPane.setAlignment(hudPanel, Pos.TOP_LEFT);
        StackPane.setMargin(hudPanel, new Insets(18));
        StackPane.setAlignment(settingsButton, Pos.TOP_RIGHT);
        StackPane.setMargin(settingsButton, new Insets(18));
    }

    private void configureConnectionStatus() {
        connectionLabel.setPadding(new Insets(8, 12, 8, 12));
        connectionLabel.setWrapText(true);
        connectionLabel.setMaxWidth(460);
        connectionLabel.setMouseTransparent(true);
        StackPane.setAlignment(connectionLabel, Pos.TOP_RIGHT);
        StackPane.setMargin(connectionLabel, new Insets(64, 18, 18, 18));
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

        Button lobbyButton = actionButton("로비로 돌아가기");
        lobbyButton.setStyle("-fx-background-color: #32465c; -fx-text-fill: white;"
                + "-fx-font-size: 15px; -fx-background-radius: 8; -fx-padding: 9 16 9 16;");
        lobbyButton.setOnAction(ignored -> returnToLobby.run());

        resultPanel.getChildren().addAll(resultTitle, resultSummary, restartButton, lobbyButton);
        resultPanel.setAlignment(Pos.CENTER);
        resultPanel.setPadding(new Insets(34));
        resultPanel.setMaxSize(500, 300);
        resultPanel.setStyle(PANEL_STYLE);
        StackPane.setAlignment(resultPanel, Pos.CENTER);
    }

    private void startNewGame() {
        clearInput();
        displayedOptionIds = List.of();
        restartGame.run();
        accumulatorSeconds = 0.0;
        refreshView();
    }

    private void updateFrame(long now) {
        if (previousFrameNanos == 0L) {
            previousFrameNanos = now;
            if (frameRateLimiter.shouldRender(now)) {
                refreshView();
            }
            return;
        }

        double frameSeconds = Math.min((now - previousFrameNanos) / 1_000_000_000.0, MAX_FRAME_SECONDS);
        previousFrameNanos = now;
        accumulatorSeconds = Math.min(
                MAX_FRAME_SECONDS,
                accumulatorSeconds + Math.max(0.0, frameSeconds));

        GameSnapshot snapshot = desktopApiClient.snapshot();
        boolean acceptsHeldInput = snapshot.phase() == GamePhase.RUNNING
                || snapshot.phase() == GamePhase.LEVEL_UP
                || snapshot.phase() == GamePhase.CHEST_REWARD;
        if (acceptsHeldInput) {
            desktopApiClient.setInput(keyState.input());
        }
        if (snapshot.phase() != GamePhase.RUNNING) {
            accumulatorSeconds = 0.0;
        }
        if (snapshot.phase() == GamePhase.RUNNING && accumulatorSeconds >= FIXED_STEP_SECONDS) {
            int steps = Math.min(
                    (int) (accumulatorSeconds / FIXED_STEP_SECONDS),
                    MAX_CATCH_UP_STEPS);
            accumulatorSeconds -= steps * FIXED_STEP_SECONDS;
        }

        if (frameRateLimiter.shouldRender(now)) {
            refreshView();
        }
    }

    private void refreshView() {
        GameSnapshot snapshot = desktopApiClient.snapshot();
        render(snapshot);
        updateInterface(snapshot);
        updateConnectionStatus(desktopApiClient.status());
    }

    private void updateConnectionStatus(DesktopApiStatus apiStatus) {
        boolean online = apiStatus.state() == DesktopApiStatus.State.ONLINE;
        connectionLabel.setVisible(!online);
        connectionLabel.setManaged(!online);
        connectionLabel.setText(apiStatus.message());
        boolean failure = apiStatus.state() != DesktopApiStatus.State.CONNECTING;
        connectionLabel.setStyle(failure
                ? "-fx-background-color: rgba(116, 30, 36, 0.94); -fx-text-fill: #fff0f0;"
                        + "-fx-background-radius: 8; -fx-font-size: 14px;"
                : "-fx-background-color: rgba(37, 54, 74, 0.94); -fx-text-fill: #eef4ff;"
                        + "-fx-background-radius: 8; -fx-font-size: 14px;");
    }

    private void updateInterface(GameSnapshot snapshot) {
        GamePhase phase = snapshot.phase();
        boolean lobby = phase == GamePhase.LOBBY;
        boolean active = phase == GamePhase.RUNNING
                || phase == GamePhase.LEVEL_UP
                || phase == GamePhase.CHEST_REWARD;
        boolean choosingUpgrade = phase == GamePhase.LEVEL_UP;
        boolean choosingChestReward = phase == GamePhase.CHEST_REWARD;
        boolean choosingReward = choosingUpgrade || choosingChestReward;
        boolean finished = phase == GamePhase.DEFEAT || phase == GamePhase.ABANDONED;

        lobbyPanel.setVisible(lobby);
        lobbyPanel.setManaged(lobby);
        hudPanel.setVisible(active);
        hudPanel.setManaged(active);
        settingsButton.setVisible(active);
        settingsButton.setManaged(active);
        upgradePanel.setVisible(choosingReward);
        upgradePanel.setManaged(choosingReward);
        resultPanel.setVisible(finished);
        resultPanel.setManaged(finished);

        timeLabel.setText(survivalTimeText(snapshot.elapsedSeconds()));
        levelLabel.setText("레벨  %d".formatted(snapshot.level()));
        experienceLabel.setText("혼불  %d / %d".formatted(
                snapshot.experience(), snapshot.experienceToNextLevel()));
        killLabel.setText("퇴치  %d".formatted(snapshot.killCount()));
        barrierLabel.setText(barrierText(snapshot));
        heartLabel.setText(heartStatusText(snapshot.heartAvailable()));
        loadoutLabel.setText(loadoutText(snapshot));

        if (choosingUpgrade) {
            if (!snapshot.pendingLevelUpOptions().isEmpty()) {
                rebuildRewardChoices(snapshot.pendingLevelUpOptions(), false);
            } else {
                rebuildGeneralUpgradeChoices(snapshot.upgradeChoices());
            }
        }
        if (choosingChestReward) {
            rebuildRewardChoices(snapshot.pendingChestOptions(), true);
        }
        if (finished) {
            resultTitle.setText(phase == GamePhase.DEFEAT ? "패배" : "야행 이탈");
            resultSummary.setText("생존 시간 %.1f초%n그림자 도깨비 %d마리 퇴치"
                    .formatted(snapshot.elapsedSeconds(), snapshot.killCount()));
        }
    }

    private void rebuildGeneralUpgradeChoices(List<UpgradeType> choices) {
        List<String> optionIds = choices.stream().map(choice -> "general:" + choice.name()).toList();
        if (displayedOptionIds.equals(optionIds)) {
            return;
        }
        displayedOptionIds = optionIds;
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
                desktopApiClient.chooseUpgrade(choice);
                refreshView();
            });
            upgradePanel.getChildren().add(button);
        }
    }

    private void rebuildRewardChoices(List<RewardOptionSnapshot> choices, boolean chestReward) {
        String prefix = chestReward ? "chest:" : "level:";
        List<String> optionIds = choices.stream().map(choice -> prefix + choice.optionId()).toList();
        if (displayedOptionIds.equals(optionIds)) {
            return;
        }
        displayedOptionIds = optionIds;
        Label title = new Label(chestReward ? "보물의 힘을 고르세요" : "새 힘을 고르세요");
        title.setStyle("-fx-font-size: 25px; -fx-font-weight: bold; -fx-text-fill: #f4dca3;");
        Label paused = new Label("선택하는 동안 야행은 멈춥니다.");
        paused.setStyle("-fx-font-size: 14px; -fx-text-fill: #afbed0;");
        upgradePanel.getChildren().setAll(title, paused);
        for (RewardOptionSnapshot choice : choices) {
            String displayName = choice.displayName() == null || choice.displayName().isBlank()
                    ? choice.kind()
                    : choice.displayName();
            String description = choice.description();
            String buttonText = description == null || description.isBlank()
                    ? displayName
                    : displayName + "\n" + description;
            Button button = rewardButton(buttonText);
            button.setOnAction(ignored -> {
                if (chestReward) {
                    desktopApiClient.chooseChestReward(choice.optionId());
                } else {
                    desktopApiClient.chooseLevelUp(choice.optionId());
                }
                refreshView();
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
        drawDecorations(graphics, width, height, cameraX, cameraY);
        graphics.setFill(BACKGROUND_TONE);
        graphics.fillRect(0.0, 0.0, width, height);

        for (ChestSnapshot chest : snapshot.chests()) {
            drawChest(graphics, chest, width, height, cameraX, cameraY);
        }

        for (EntitySnapshot flame : snapshot.soulFlames()) {
            drawSoulFlame(graphics, flame, width, height, cameraX, cameraY);
        }
        for (EntitySnapshot projectile : snapshot.projectiles()) {
            drawTalisman(graphics, projectile, width, height, cameraX, cameraY);
        }
        for (EntitySnapshot enemy : snapshot.enemies()) {
            drawEnemy(graphics, enemy, width, height, cameraX, cameraY);
        }
        for (LightningStrikeSnapshot strike : snapshot.lightningStrikes()) {
            drawLightningStrike(graphics, strike, width, height, cameraX, cameraY);
        }
        if (player != null) {
            String playerKind = player.kindId() == null || "unknown".equals(player.kindId())
                    ? snapshot.characterId()
                    : player.kindId();
            drawPlayer(graphics, player, playerKind, width, height, cameraX, cameraY);
        }
        for (ChestIndicatorSnapshot indicator : snapshot.chestIndicators()) {
            snapshot.chests().stream()
                    .filter(chest -> chest.id() == indicator.chestId())
                    .filter(chest -> isChestOutsideViewport(
                            chest, width, height, cameraX, cameraY))
                    .findFirst()
                    .ifPresent(ignored -> drawChestIndicator(graphics, indicator, width, height));
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

    private void drawDecorations(
            GraphicsContext graphics,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        double padding = DecorationLayout.DRAW_SIZE / 2.0;
        int firstCellX = (int) Math.floor(
                (cameraX - width / 2.0 - padding) / DecorationLayout.CELL_SIZE);
        int lastCellX = (int) Math.floor(
                (cameraX + width / 2.0 + padding) / DecorationLayout.CELL_SIZE);
        int firstCellY = (int) Math.floor(
                (cameraY - height / 2.0 - padding) / DecorationLayout.CELL_SIZE);
        int lastCellY = (int) Math.floor(
                (cameraY + height / 2.0 + padding) / DecorationLayout.CELL_SIZE);

        for (int cellY = firstCellY; cellY <= lastCellY; cellY++) {
            for (int cellX = firstCellX; cellX <= lastCellX; cellX++) {
                DecorationLayout.Placement placement = DecorationLayout.placementAt(cellX, cellY);
                if (placement == null) {
                    continue;
                }
                Image decoration = sprites.decoration(placement.kind());
                if (decoration == null) {
                    continue;
                }
                double screenX = width / 2.0 + placement.worldX() - cameraX;
                double screenY = height / 2.0 + placement.worldY() - cameraY;
                graphics.drawImage(
                        decoration,
                        screenX - padding,
                        screenY - padding,
                        DecorationLayout.DRAW_SIZE,
                        DecorationLayout.DRAW_SIZE);
            }
        }
    }

    private void drawPlayer(
            GraphicsContext graphics,
            EntitySnapshot entity,
            String characterId,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        double screenX = screenX(entity, width, cameraX);
        double screenY = screenY(entity, height, cameraY);
        Image sprite = sprites.player(characterId);
        if (sprite != null) {
            drawCentered(graphics, sprite, screenX, screenY, ACTOR_SIZE, 0.0);
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
        Image sprite = sprites.enemy(entity.kindId());
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

    private static void drawLightningStrike(
            GraphicsContext graphics,
            LightningStrikeSnapshot strike,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        double targetX = width / 2.0 + strike.x() - cameraX;
        double targetY = height / 2.0 + strike.y() - cameraY - ACTOR_SIZE * 0.32;
        double intensity = Math.min(1.0, strike.remainingSeconds() / 0.12);
        double bend = ((strike.id() & 1L) == 0L ? 1.0 : -1.0) * 9.0;

        graphics.save();
        graphics.setGlobalAlpha(Math.max(0.25, intensity));
        graphics.setLineWidth(7.0);
        graphics.setStroke(Color.web("#7ecbff", 0.42));
        graphics.strokePolyline(
                new double[] {targetX + bend, targetX - bend, targetX + bend * 0.45, targetX},
                new double[] {targetY - 118.0, targetY - 76.0, targetY - 38.0, targetY + 4.0},
                4);
        graphics.setLineWidth(2.5);
        graphics.setStroke(Color.web("#f7fbff"));
        graphics.strokePolyline(
                new double[] {targetX + bend, targetX - bend, targetX + bend * 0.45, targetX},
                new double[] {targetY - 118.0, targetY - 76.0, targetY - 38.0, targetY + 4.0},
                4);
        graphics.setFill(Color.web("#bde8ff", 0.38));
        graphics.fillOval(targetX - 24.0, targetY - 9.0, 48.0, 18.0);
        graphics.restore();
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
        Image sprite = sprites.projectile(entity.kindId());
        if (sprite != null) {
            drawCentered(graphics, sprite, screenX, screenY, PROJECTILE_SIZE, entity.rotationDegrees());
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
            drawCentered(graphics, sprite, screenX, screenY, SOUL_FLAME_SIZE, entity.rotationDegrees());
            return;
        }
        graphics.setFill(Color.web("#65e4df", 0.35));
        graphics.fillOval(screenX - 13, screenY - 15, 26, 30);
        graphics.setFill(Color.web("#9afaf1"));
        graphics.fillOval(screenX - 7, screenY - 9, 14, 18);
    }

    private void drawChest(
            GraphicsContext graphics,
            ChestSnapshot chest,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        if (Boolean.TRUE.equals(chest.opened())) {
            return;
        }
        double screenX = width / 2.0 + chest.x() - cameraX;
        double screenY = height / 2.0 + chest.y() - cameraY;
        Image sprite = sprites.chest(chest.type());
        if (sprite != null) {
            drawCentered(graphics, sprite, screenX, screenY, CHEST_SIZE, 0.0);
            return;
        }
        graphics.setFill(Color.web("#7b4327"));
        graphics.fillRoundRect(screenX - 18, screenY - 13, 36, 27, 5, 5);
        graphics.setStroke(Color.web("#e0b85f"));
        graphics.setLineWidth(3.0);
        graphics.strokeRoundRect(screenX - 18, screenY - 13, 36, 27, 5, 5);
        graphics.setFill(Color.web("#e0b85f"));
        graphics.fillRect(screenX - 3, screenY - 4, 6, 10);
    }

    private static void drawChestIndicator(
            GraphicsContext graphics,
            ChestIndicatorSnapshot indicator,
            double width,
            double height) {
        double length = Math.hypot(indicator.directionX(), indicator.directionY());
        if (length < 0.0001) {
            return;
        }
        double directionX = indicator.directionX() / length;
        double directionY = indicator.directionY() / length;
        double margin = 42.0;
        double scaleX = directionX == 0.0
                ? Double.POSITIVE_INFINITY
                : (width / 2.0 - margin) / Math.abs(directionX);
        double scaleY = directionY == 0.0
                ? Double.POSITIVE_INFINITY
                : (height / 2.0 - margin) / Math.abs(directionY);
        double scale = Math.min(scaleX, scaleY);
        double x = width / 2.0 + directionX * scale;
        double y = height / 2.0 + directionY * scale;
        double angle = Math.atan2(directionY, directionX);

        graphics.save();
        graphics.translate(x, y);
        graphics.rotate(Math.toDegrees(angle));
        graphics.setFill(Color.web("#f2c96f", 0.95));
        graphics.fillPolygon(
                new double[] {14.0, -10.0, -5.0, -10.0},
                new double[] {0.0, -10.0, 0.0, 10.0},
                4);
        graphics.restore();
        graphics.setFill(Color.web("#f4e7c5"));
        graphics.fillText("%dm".formatted(Math.max(0, Math.round(indicator.distance()))), x - 13, y + 25);
    }

    static boolean isChestOutsideViewport(
            ChestSnapshot chest,
            double width,
            double height,
            double cameraX,
            double cameraY) {
        if (Boolean.TRUE.equals(chest.opened())) {
            return false;
        }
        double screenX = width / 2.0 + chest.x() - cameraX;
        double screenY = height / 2.0 + chest.y() - cameraY;
        double halfSize = CHEST_SIZE / 2.0;
        return screenX + halfSize < 0.0
                || screenX - halfSize > width
                || screenY + halfSize < 0.0
                || screenY - halfSize > height;
    }

    static String survivalTimeText(double elapsedSeconds) {
        int seconds = Math.max(0, (int) Math.floor(elapsedSeconds));
        return "생존 시간  %02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    static String heartStatusText(boolean heartAvailable) {
        return heartAvailable ? "하트  준비됨" : "하트  없음";
    }

    static Color backgroundTone() {
        return BACKGROUND_TONE;
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

    private static String barrierText(GameSnapshot snapshot) {
        if ("GALE_SHAMAN".equals(snapshot.characterId())) {
            return "질풍 가호  이동 속도 +25%";
        }
        if (snapshot.barrierAvailable()) {
            return "호신 결계  준비됨";
        }
        if (snapshot.invulnerabilityRemainingSeconds() > 0.0) {
            return "호신 결계  무적 %.1f초".formatted(snapshot.invulnerabilityRemainingSeconds());
        }
        return "호신 결계  소진";
    }

    private static String loadoutText(GameSnapshot snapshot) {
        List<String> equipment = snapshot.itemSlots().stream()
                .map(GameView::itemLabel)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        equipment.addAll(snapshot.evolutions().stream().map(GameView::evolutionLabel).toList());
        String details = equipment.isEmpty() ? "없음" : String.join(", ", equipment);
        return "장비 %d / 5  %s".formatted(snapshot.occupiedItemSlots(), details);
    }

    private static String itemLabel(ItemSlotSnapshot item) {
        String name = item.displayName() == null || item.displayName().isBlank()
                ? item.itemId()
                : item.displayName();
        return "%s Lv.%d".formatted(name, item.level());
    }

    private static String evolutionLabel(String evolution) {
        if (evolution == null) {
            return "진화";
        }
        return switch (evolution) {
            case "TEN_THOUSAND_SEAL_ARRAY" -> "만방봉인진";
            case "HEAVENLY_THUNDER_SEAL" -> "천뢰봉인부";
            case "INFERNO_RETURNING_WHEEL" -> "업화회륜";
            case "BLUE_FLAME_SPIRIT_GOURD" -> "청염귀호";
            case "LUNAR_ECLIPSE_TWIN_BLADES" -> "월식쌍인";
            case "THUNDER_FLAME_DIVINE_ORB" -> "뇌화신주";
            default -> evolution;
        };
    }

    private void updateKey(KeyEvent event, boolean pressed) {
        if (event.getCode() == KeyCode.ESCAPE) {
            boolean firstPress = keyState.updateEscape(pressed);
            if (isActiveGamePhase(desktopApiClient.snapshot().phase())) {
                if (firstPress) {
                    toggleSettings.run();
                }
                event.consume();
            }
            return;
        }
        if (!inputEnabled) {
            return;
        }
        boolean handled = keyState.updateMovement(event.getCode(), pressed);
        if (handled) {
            event.consume();
        }
    }

    private static boolean isActiveGamePhase(GamePhase phase) {
        return phase == GamePhase.RUNNING
                || phase == GamePhase.LEVEL_UP
                || phase == GamePhase.CHEST_REWARD;
    }

    static final class KeyState {
        private boolean up;
        private boolean down;
        private boolean left;
        private boolean right;
        private boolean escapePressed;

        boolean updateEscape(boolean pressed) {
            boolean firstPress = pressed && !escapePressed;
            escapePressed = pressed;
            return firstPress;
        }

        boolean updateMovement(KeyCode code, boolean pressed) {
            return switch (code) {
                case W -> {
                    up = pressed;
                    yield true;
                }
                case S -> {
                    down = pressed;
                    yield true;
                }
                case A -> {
                    left = pressed;
                    yield true;
                }
                case D -> {
                    right = pressed;
                    yield true;
                }
                default -> false;
            };
        }

        void clearMovement() {
            up = false;
            down = false;
            left = false;
            right = false;
        }

        void clearAfterFocusLoss() {
            clearMovement();
            escapePressed = false;
        }

        InputState input() {
            return new InputState(up, down, left, right);
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

    private static Button rewardButton(String text) {
        Button button = new Button(text);
        button.setWrapText(true);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(88);
        button.setStyle("-fx-background-color: #26364a; -fx-text-fill: #f5f5f5;"
                + "-fx-font-size: 15px; -fx-background-radius: 8; -fx-border-color: #596f88;"
                + "-fx-border-radius: 8; -fx-padding: 10 16 10 16;");
        return button;
    }
}
