package kr.joseonnight.desktop.view;

import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import kr.joseonnight.desktop.audio.AudioScene;
import kr.joseonnight.desktop.audio.GameAudioService;
import kr.joseonnight.desktop.authentication.AuthPhase;
import kr.joseonnight.desktop.authentication.AuthSession;
import kr.joseonnight.desktop.authentication.AuthState;
import kr.joseonnight.desktop.client.AuthApiClient;
import kr.joseonnight.desktop.client.DesktopApiClient;
import kr.joseonnight.desktop.client.MemberApiClient;
import kr.joseonnight.desktop.gameplay.DesktopApiStatus;
import kr.joseonnight.desktop.member.MemberBootstrap;
import kr.joseonnight.desktop.settings.AudioSettings;
import kr.joseonnight.desktop.settings.TargetFps;

/** Owns the Login → Lobby → Settings/Combat root-screen transitions. */
public final class DesktopRootView extends StackPane implements AutoCloseable {
    private static final String PANEL_STYLE = "-fx-background-color: rgba(9, 15, 24, 0.94);"
            + "-fx-background-radius: 14; -fx-border-color: #c8a35a; -fx-border-radius: 14;"
            + "-fx-border-width: 2;";
    private static final String TITLE_STYLE =
            "-fx-font-size: 38px; -fx-font-weight: bold; -fx-text-fill: #f4dca3;";
    private static final String TEXT_STYLE = "-fx-font-size: 16px; -fx-text-fill: #e9edf2;";
    private static final String BUTTON_STYLE = "-fx-background-color: #a7353f; -fx-text-fill: white;"
            + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 8;"
            + "-fx-padding: 10 18 10 18;";
    private static final String GOOGLE_LOGIN_BUTTON_STYLE = "-fx-background-color: #ffffff; -fx-text-fill: #1f1f1f;"
            + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 8;"
            + "-fx-border-color: #747775; -fx-border-radius: 8; -fx-border-width: 1;"
            + "-fx-padding: 8 16 8 12;";
    static final String GOOGLE_LOGIN_BUTTON_TEXT = "Google Login";
    static final String GOOGLE_LOGIN_ICON_RESOURCE = "/assets/brand/google-g-sign-in.png";

    private final AuthApiClient authApiClient;
    private final MemberApiClient memberApiClient;
    private final DesktopApiClient gameApiClient;
    private final GameAudioService audioService;
    private final Consumer<String> browserOpener;
    private final GameView gameView;
    private final ImageView lobbyBackground = createLobbyBackground();

    private final VBox loginPane = panel();
    private final VBox registrationPane = panel();
    private final VBox lobbyPane = panel();
    private final VBox settingsPane = panel();
    private final Label loginMessage = bodyLabel();
    private final Button loginButton = googleLoginButton();
    private final TextField nicknameField = new TextField();
    private final Label registrationMessage = bodyLabel();
    private final Label welcomeLabel = bodyLabel();
    private final ComboBox<String> characterSelector = new ComboBox<>();
    private final CheckBox mutedCheckBox = new CheckBox("모든 소리 끄기");
    private final Slider musicVolumeSlider = volumeSlider();
    private final Slider effectsVolumeSlider = volumeSlider();
    private final Label musicVolumeLabel = bodyLabel();
    private final Label effectsVolumeLabel = bodyLabel();
    private final ComboBox<TargetFps> targetFpsSelector = new ComboBox<>();
    private final Label settingsMessage = bodyLabel();

    private RootScreen screen = RootScreen.LOGIN;
    private MemberBootstrap bootstrap = MemberBootstrap.fallback();
    private AudioSettings audioSettings = AudioSettings.defaults();
    private String loadedSessionToken;
    private URI openedAuthorizationUri;
    private boolean applyingSettings;

    public DesktopRootView(
            AuthApiClient authApiClient,
            MemberApiClient memberApiClient,
            DesktopApiClient gameApiClient,
            GameAudioService audioService,
            Consumer<String> browserOpener) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient");
        this.memberApiClient = Objects.requireNonNull(memberApiClient, "memberApiClient");
        this.gameApiClient = Objects.requireNonNull(gameApiClient, "gameApiClient");
        this.audioService = Objects.requireNonNull(audioService, "audioService");
        this.browserOpener = Objects.requireNonNull(browserOpener, "browserOpener");
        gameView = new GameView(gameApiClient, this::restartGame, this::returnToLobby);

        setPrefSize(1280, 720);
        setStyle("-fx-background-color: linear-gradient(to bottom, #101b27, #080d14);");
        configureLoginPane();
        configureRegistrationPane();
        configureLobbyPane();
        configureSettingsPane();
        lobbyBackground.fitWidthProperty().bind(widthProperty());
        lobbyBackground.fitHeightProperty().bind(heightProperty());
        getChildren().addAll(lobbyBackground, gameView, loginPane, registrationPane, lobbyPane, settingsPane);

        authApiClient.setStateListener(this::refreshAuthenticationOnJavaFxThread);
        gameApiClient.setStateListener(this::refreshGameOnJavaFxThread);
        memberApiClient.setFailureListener(message -> runOnJavaFxThread(() -> {
            settingsMessage.setText(message);
            settingsMessage.setStyle("-fx-font-size: 14px; -fx-text-fill: #ffb1b1;");
        }));
        audioService.applySettings(audioSettings);
        audioService.switchScene(AudioScene.LOBBY);
        refreshAuthentication();
    }

    public void installInputHandlers(Scene scene) {
        gameView.installInputHandlers(scene);
    }

    public void start() {
        gameView.startLoop();
    }

    public void clearInput() {
        gameView.clearInput();
    }

    @Override
    public void close() {
        authApiClient.setStateListener(() -> { });
        gameApiClient.setStateListener(() -> { });
        memberApiClient.setFailureListener(ignored -> { });
        gameView.stopLoop();
        audioService.close();
    }

    private void configureLoginPane() {
        Label title = title("조선 야행");
        Label subtitle = bodyLabel();
        subtitle.setText("달빛 아래 펼쳐지는 조선 다크 판타지 생존 야행");
        subtitle.setStyle("-fx-font-size: 17px; -fx-text-fill: #b9c7d7;");
        loginMessage.setWrapText(true);
        loginMessage.setMaxWidth(430);
        loginButton.setOnAction(ignored -> {
            openedAuthorizationUri = null;
            authApiClient.beginLogin();
        });
        loginPane.getChildren().addAll(title, subtitle, loginMessage, loginButton);
    }

    private void configureRegistrationPane() {
        Label title = title("첫 야행 준비");
        Label description = bodyLabel();
        description.setText("다른 플레이어에게 보일 닉네임을 정해 주세요.");
        nicknameField.setPromptText("닉네임");
        nicknameField.setMaxWidth(320);
        nicknameField.setStyle("-fx-font-size: 16px; -fx-padding: 10;");
        registrationMessage.setStyle("-fx-font-size: 14px; -fx-text-fill: #ffb1b1;");
        Button registerButton = primaryButton("닉네임 등록");
        registerButton.setOnAction(ignored -> registerNickname());
        nicknameField.setOnAction(ignored -> registerNickname());
        registrationPane.getChildren().addAll(
                title, description, nicknameField, registrationMessage, registerButton);
    }

    private void configureLobbyPane() {
        Label title = title("조선 야행");
        welcomeLabel.setStyle("-fx-font-size: 19px; -fx-text-fill: #f4e7c5;");
        Label characterLabel = bodyLabel();
        characterLabel.setText("출전 인물");
        characterSelector.setMaxWidth(330);
        characterSelector.setPrefWidth(330);
        characterSelector.setStyle("-fx-font-size: 16px;");

        Button startButton = primaryButton("야행 시작");
        startButton.setOnAction(ignored -> startGame());
        Button settingsButton = secondaryButton("설정");
        settingsButton.setOnAction(ignored -> showScreen(RootScreen.SETTINGS));
        Button logoutButton = secondaryButton("로그아웃");
        logoutButton.setOnAction(ignored -> logout());
        lobbyPane.getChildren().addAll(
                title, welcomeLabel, characterLabel, characterSelector,
                startButton, settingsButton, logoutButton);
    }

    private void configureSettingsPane() {
        settingsPane.setMaxSize(620, 620);
        Label title = title("환경설정");
        mutedCheckBox.setStyle(TEXT_STYLE);
        mutedCheckBox.setOnAction(ignored -> updateAudioSettings());
        musicVolumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> updateAudioSettings());
        effectsVolumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> updateAudioSettings());
        targetFpsSelector.getItems().setAll(TargetFps.values());
        targetFpsSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(TargetFps value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public TargetFps fromString(String value) {
                return TargetFps.valueOf(value);
            }
        });
        targetFpsSelector.setMaxWidth(420);
        targetFpsSelector.setPrefWidth(420);
        targetFpsSelector.setStyle("-fx-font-size: 15px;");
        targetFpsSelector.setOnAction(ignored -> updateAudioSettings());
        Label frameLabel = bodyLabel();
        frameLabel.setText("화면 프레임");
        settingsMessage.setStyle("-fx-font-size: 14px; -fx-text-fill: #afbed0;");
        Button backButton = primaryButton("로비로 돌아가기");
        backButton.setOnAction(ignored -> showScreen(RootScreen.LOBBY));
        settingsPane.getChildren().addAll(
                title,
                mutedCheckBox,
                musicVolumeLabel,
                musicVolumeSlider,
                effectsVolumeLabel,
                effectsVolumeSlider,
                frameLabel,
                targetFpsSelector,
                settingsMessage,
                backButton);
        applyAudioSettingsToControls();
    }

    private void refreshAuthenticationOnJavaFxThread() {
        runOnJavaFxThread(this::refreshAuthentication);
    }

    private void refreshAuthentication() {
        AuthState state = authApiClient.state();
        loginMessage.setText(state.message());
        loginButton.setDisable(state.phase() == AuthPhase.STARTING_ATTEMPT
                || state.phase() == AuthPhase.WAITING_FOR_BROWSER);
        loginButton.setText(state.phase() == AuthPhase.WAITING_FOR_BROWSER
                ? GOOGLE_LOGIN_BUTTON_TEXT + "..."
                : GOOGLE_LOGIN_BUTTON_TEXT);

        switch (state.phase()) {
            case AUTHENTICATED -> onAuthenticated(state.session());
            case REGISTRATION_REQUIRED -> {
                registrationMessage.setText("");
                showScreen(RootScreen.REGISTRATION);
                nicknameField.requestFocus();
            }
            case WAITING_FOR_BROWSER -> {
                showScreen(RootScreen.LOGIN);
                openAuthorizationPage(state.authorizationUri());
            }
            case SIGNED_OUT, STARTING_ATTEMPT, EXPIRED, FORBIDDEN, OFFLINE, FAILED ->
                    showScreen(RootScreen.LOGIN);
        }
    }

    private void onAuthenticated(AuthSession session) {
        if (session == null) {
            return;
        }
        showScreen(RootScreen.LOBBY);
        if (session.accessToken().equals(loadedSessionToken)) {
            return;
        }
        loadedSessionToken = session.accessToken();
        welcomeLabel.setText("로비 정보를 불러오고 있습니다…");
        memberApiClient.loadBootstrap(session.accessToken()).whenComplete((value, failure) ->
                runOnJavaFxThread(() -> {
                    if (!isCurrentSession(session)) {
                        return;
                    }
                    if (failure == null) {
                        bootstrap = value;
                    } else {
                        bootstrap = MemberBootstrap.fallback();
                    }
                    populateLobby();
                }));
        memberApiClient.loadSettings(session.accessToken()).whenComplete((value, failure) ->
                runOnJavaFxThread(() -> {
                    if (!isCurrentSession(session)) {
                        return;
                    }
                    if (failure == null) {
                        audioSettings = value;
                        settingsMessage.setText("서버에 저장된 설정을 불러왔습니다.");
                    } else {
                        settingsMessage.setText("기본 소리 설정을 사용합니다.");
                    }
                    applyAudioSettingsToControls();
                    audioService.applySettings(audioSettings);
                    gameView.setTargetFps(audioSettings.targetFps());
                }));
    }

    private void populateLobby() {
        welcomeLabel.setText(bootstrap.nickname() + " 님, 달빛 폐허가 기다립니다.");
        characterSelector.getItems().setAll(
                bootstrap.characters().stream().map(MemberBootstrap.CharacterOption::displayName).toList());
        characterSelector.getSelectionModel().selectFirst();
    }

    private void startGame() {
        AuthSession session = authApiClient.state().session();
        int selected = characterSelector.getSelectionModel().getSelectedIndex();
        if (session == null || selected < 0 || selected >= bootstrap.characters().size()) {
            welcomeLabel.setText("로그인과 캐릭터 선택을 확인해 주세요.");
            return;
        }
        String characterId = bootstrap.characters().get(selected).characterId();
        audioService.beginNewGame();
        gameApiClient.startNewGame(session.accessToken(), characterId);
        showScreen(RootScreen.COMBAT);
    }

    private void restartGame() {
        audioService.beginNewGame();
        gameApiClient.startNewGame();
        showScreen(RootScreen.COMBAT);
    }

    private void returnToLobby() {
        gameApiClient.disconnect();
        showScreen(RootScreen.LOBBY);
    }

    private void logout() {
        loadedSessionToken = null;
        bootstrap = MemberBootstrap.fallback();
        gameApiClient.clearSession();
        authApiClient.logout();
    }

    private void registerNickname() {
        try {
            registrationMessage.setText("");
            authApiClient.registerNickname(nicknameField.getText());
        } catch (IllegalArgumentException exception) {
            registrationMessage.setText("닉네임을 입력해 주세요.");
        } catch (IllegalStateException exception) {
            registrationMessage.setText("로그인부터 다시 시도해 주세요.");
        }
    }

    private void openAuthorizationPage(URI authorizationUri) {
        if (authorizationUri == null || authorizationUri.equals(openedAuthorizationUri)) {
            return;
        }
        openedAuthorizationUri = authorizationUri;
        try {
            browserOpener.accept(authorizationUri.toString());
        } catch (RuntimeException exception) {
            loginMessage.setText("브라우저를 열지 못했습니다: " + authorizationUri);
            loginButton.setDisable(false);
        }
    }

    private void updateAudioSettings() {
        if (applyingSettings) {
            return;
        }
        int musicVolume = (int) Math.round(musicVolumeSlider.getValue());
        int effectsVolume = (int) Math.round(effectsVolumeSlider.getValue());
        TargetFps targetFps = targetFpsSelector.getValue() == null
                ? TargetFps.FPS_60
                : targetFpsSelector.getValue();
        audioSettings = new AudioSettings(
                mutedCheckBox.isSelected(), musicVolume, effectsVolume, targetFps);
        musicVolumeLabel.setText("배경음악 음량  %d".formatted(musicVolume));
        effectsVolumeLabel.setText("효과음 음량  %d".formatted(effectsVolume));
        audioService.applySettings(audioSettings);
        gameView.setTargetFps(targetFps);
        AuthSession session = authApiClient.state().session();
        if (session != null) {
            settingsMessage.setText("변경 후 0.5초 뒤 자동 저장됩니다.");
            memberApiClient.saveSettingsDebounced(session.accessToken(), audioSettings);
        }
    }

    private void applyAudioSettingsToControls() {
        applyingSettings = true;
        try {
            mutedCheckBox.setSelected(audioSettings.muted());
            musicVolumeSlider.setValue(audioSettings.musicVolume());
            effectsVolumeSlider.setValue(audioSettings.effectsVolume());
            targetFpsSelector.setValue(audioSettings.targetFps());
            musicVolumeLabel.setText("배경음악 음량  %d".formatted(audioSettings.musicVolume()));
            effectsVolumeLabel.setText("효과음 음량  %d".formatted(audioSettings.effectsVolume()));
        } finally {
            applyingSettings = false;
        }
    }

    private void refreshGameOnJavaFxThread() {
        runOnJavaFxThread(() -> {
            gameView.refreshFromClient();
            audioService.consume(gameApiClient.snapshot());
            DesktopApiStatus.State connectionState = gameApiClient.status().state();
            if (connectionState == DesktopApiStatus.State.AUTHENTICATION_EXPIRED) {
                loadedSessionToken = null;
                gameApiClient.clearSession();
                authApiClient.logout();
                loginMessage.setText("로그인이 만료되었습니다. 다시 로그인해 주세요.");
            }
        });
    }

    private void showScreen(RootScreen nextScreen) {
        screen = nextScreen;
        loginPane.setVisible(screen == RootScreen.LOGIN);
        loginPane.setManaged(screen == RootScreen.LOGIN);
        registrationPane.setVisible(screen == RootScreen.REGISTRATION);
        registrationPane.setManaged(screen == RootScreen.REGISTRATION);
        lobbyPane.setVisible(screen == RootScreen.LOBBY);
        lobbyPane.setManaged(screen == RootScreen.LOBBY);
        settingsPane.setVisible(screen == RootScreen.SETTINGS);
        settingsPane.setManaged(screen == RootScreen.SETTINGS);
        gameView.setVisible(screen == RootScreen.COMBAT);
        gameView.setManaged(screen == RootScreen.COMBAT);
        lobbyBackground.setVisible(screen != RootScreen.COMBAT);
        gameView.setInputEnabled(screen == RootScreen.COMBAT);
        audioService.switchScene(screen == RootScreen.COMBAT ? AudioScene.COMBAT : AudioScene.LOBBY);
    }

    private boolean isCurrentSession(AuthSession session) {
        AuthSession current = authApiClient.state().session();
        return current != null && current.accessToken().equals(session.accessToken());
    }

    private static VBox panel() {
        VBox pane = new VBox(16);
        pane.setAlignment(Pos.CENTER);
        pane.setPadding(new Insets(38));
        pane.setMaxSize(570, 520);
        pane.setStyle(PANEL_STYLE);
        return pane;
    }

    private static ImageView createLobbyBackground() {
        var resource = DesktopRootView.class.getResource(
                "/assets/backgrounds/lobby-moonlit-courtyard.png");
        ImageView view = resource == null
                ? new ImageView()
                : new ImageView(new Image(resource.toExternalForm(), true));
        view.setPreserveRatio(false);
        view.setSmooth(false);
        view.setMouseTransparent(true);
        return view;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.setStyle(TITLE_STYLE);
        return label;
    }

    private static Label bodyLabel() {
        Label label = new Label();
        label.setStyle(TEXT_STYLE);
        label.setWrapText(true);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        return label;
    }

    private static Button primaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(BUTTON_STYLE);
        button.setMinWidth(220);
        return button;
    }

    private static Button googleLoginButton() {
        Image icon = new Image(Objects.requireNonNull(
                        DesktopRootView.class.getResource(GOOGLE_LOGIN_ICON_RESOURCE),
                        "Google sign-in icon resource")
                .toExternalForm());
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(20);
        iconView.setFitHeight(20);
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);

        Button button = new Button(GOOGLE_LOGIN_BUTTON_TEXT, iconView);
        button.setAlignment(Pos.CENTER);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setStyle(GOOGLE_LOGIN_BUTTON_STYLE);
        button.setMinWidth(260);
        return button;
    }

    private static Button secondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #32465c; -fx-text-fill: white;"
                + "-fx-font-size: 15px; -fx-background-radius: 8; -fx-padding: 9 16 9 16;");
        button.setMinWidth(220);
        return button;
    }

    private static Slider volumeSlider() {
        Slider slider = new Slider(0, 100, 70);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(25);
        slider.setBlockIncrement(5);
        slider.setMaxWidth(420);
        return slider;
    }

    private static void runOnJavaFxThread(Runnable operation) {
        if (Platform.isFxApplicationThread()) {
            operation.run();
        } else {
            Platform.runLater(operation);
        }
    }

    private enum RootScreen {
        LOGIN,
        REGISTRATION,
        LOBBY,
        SETTINGS,
        COMBAT
    }
}
