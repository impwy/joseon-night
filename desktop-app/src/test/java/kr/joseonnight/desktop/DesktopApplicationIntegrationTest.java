package kr.joseonnight.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.websocket.WebSocket;
import com.linecorp.armeria.common.websocket.WebSocketFrame;
import com.linecorp.armeria.common.websocket.WebSocketFrameType;
import com.linecorp.armeria.common.websocket.WebSocketWriter;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.websocket.WebSocketService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.joseonnight.desktop.authentication.AuthPhase;
import kr.joseonnight.desktop.client.AuthApiClient;
import kr.joseonnight.desktop.client.DesktopApiClient;
import kr.joseonnight.desktop.client.MemberApiClient;
import kr.joseonnight.desktop.gameplay.DesktopApiStatus;
import kr.joseonnight.desktop.gameplay.GamePhase;
import kr.joseonnight.desktop.gameplay.InputState;
import kr.joseonnight.desktop.member.MemberBootstrap;
import kr.joseonnight.desktop.settings.AudioSettings;
import kr.joseonnight.desktop.settings.TargetFps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.ObjectMapper;

class DesktopApplicationIntegrationTest {
    private static final Pattern COMMAND_SEQUENCE_PATTERN = Pattern.compile(
            "\\\"commandSequence\\\":(\\d+)");
    private static final Duration REST_RESPONSE_TIMEOUT = Duration.ofMillis(400);
    private static final Duration SOCKET_STABILITY_WINDOW = Duration.ofMillis(2_400);
    private static final int ARMERIA_DEFAULT_MAX_FRAME_PAYLOAD_LENGTH = 65_535;
    private static final int GAME_MAX_FRAME_PAYLOAD_LENGTH = 256 * 1024;

    @Test
    void springDesktopContextContainsClientsButNoEmbeddedServer() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(DesktopApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off")) {
            assertThat(context.getBean(ClientFactory.class)).isNotNull();
            assertThat(context.getBean(WebClient.class)).isNotNull();
            assertThat(context.getBean(AuthApiClient.class)).isNotNull();
            assertThat(context.getBean(MemberApiClient.class)).isNotNull();
            assertThat(context.getBean(DesktopApiClient.class)).isNotNull();
            assertThat(context.getBeansOfType(Server.class)).isEmpty();
            assertThat(ClassUtils.isPresent(
                    "jakarta.persistence.EntityManagerFactory", context.getClassLoader())).isFalse();
            assertThat(ClassUtils.isPresent(
                    "org.springframework.kafka.core.KafkaTemplate", context.getClassLoader())).isFalse();
        }
    }

    @Test
    void browserLoginPollsOffThreadAndRegistersANewNickname() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             AuthApiClient auth = new AuthApiClient(
                     webClient(backend, factory),
                     new ObjectMapper(),
                     Duration.ofMillis(20),
                     Duration.ofSeconds(2))) {
            AtomicReference<URI> browserUri = new AtomicReference<>();
            auth.setStateListener(() -> {
                if (auth.state().authorizationUri() != null) {
                    browserUri.set(auth.state().authorizationUri());
                }
            });

            auth.beginLogin();
            await(() -> auth.state().phase() == AuthPhase.REGISTRATION_REQUIRED);

            assertThat(browserUri.get()).isEqualTo(URI.create("https://accounts.example.test/login"));
            assertThat(backend.hasRestRequest("POST", "/api/v1/auth/desktop/attempts")).isTrue();
            assertThat(backend.requests).anySatisfy(request -> {
                assertThat(request.path()).isEqualTo("/api/v1/auth/desktop/attempts/attempt-1/exchange");
                assertThat(request.body()).contains("\"pollToken\":\"poll-secret\"");
            });

            auth.registerNickname("달빛사냥꾼");
            await(() -> auth.state().phase() == AuthPhase.AUTHENTICATED);

            assertThat(auth.state().session().accessToken()).isEqualTo("jwt-token");
            assertThat(backend.requests).anySatisfy(request -> {
                assertThat(request.path()).isEqualTo("/api/v1/auth/desktop/registrations");
                assertThat(request.body())
                        .contains("\"registrationTicket\":\"registration-ticket\"")
                        .contains("\"nickname\":\"달빛사냥꾼\"");
            });
        }
    }

    @Test
    void authenticationConfigurationFailureShowsSafeMessageAndLogsCodeWithoutSecrets() throws Exception {
        Logger clientLogger = (Logger) LoggerFactory.getLogger(AuthApiClient.class);
        List<ILoggingEvent> logEvents = new CopyOnWriteArrayList<>();
        AppenderBase<ILoggingEvent> collectingAppender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                logEvents.add(event);
            }
        };
        collectingAppender.start();
        clientLogger.addAppender(collectingAppender);
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             AuthApiClient auth = new AuthApiClient(
                     webClient(backend, factory),
                     new ObjectMapper(),
                     Duration.ofMillis(20),
                     Duration.ofSeconds(2))) {
            backend.respondToAuthAttempt(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "{\"code\":\"AUTH_CONFIGURATION_MISSING\","
                            + "\"message\":\"Desktop authentication is not configured\","
                            + "\"sensitive\":\"poll-secret\"}");

            auth.beginLogin();
            await(() -> auth.state().phase() == AuthPhase.FAILED);

            assertThat(auth.state().userMessage())
                    .isEqualTo("현재 로그인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            assertThat(logEvents)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("create-attempt")
                            && message.contains("httpStatus=503")
                            && message.contains("errorCode=AUTH_CONFIGURATION_MISSING"))
                    .noneMatch(message -> message.contains("poll-secret"));
            assertThat(logEvents).allSatisfy(event -> {
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getMDCPropertyMap()).isEmpty();
            });
        } finally {
            clientLogger.detachAppender(collectingAppender);
            collectingAppender.stop();
        }
    }

    @Test
    void genericServiceUnavailableDoesNotClaimAuthenticationIsMisconfigured() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             AuthApiClient auth = new AuthApiClient(
                     webClient(backend, factory),
                     new ObjectMapper(),
                     Duration.ofMillis(20),
                     Duration.ofSeconds(2))) {
            backend.respondToAuthAttempt(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "{\"code\":\"SERVICE_UNAVAILABLE\"}");

            auth.beginLogin();
            await(() -> auth.state().phase() == AuthPhase.FAILED);

            assertThat(auth.state().userMessage())
                    .isEqualTo("현재 로그인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    @Test
    void malformedAttemptResponseIsAProtocolFailureRatherThanOffline() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             AuthApiClient auth = new AuthApiClient(
                     webClient(backend, factory),
                     new ObjectMapper(),
                     Duration.ofMillis(20),
                     Duration.ofSeconds(2))) {
            backend.respondToAuthAttempt(HttpStatus.CREATED, "{\"attemptId\":");

            auth.beginLogin();
            await(() -> auth.state().phase() == AuthPhase.FAILED);

            assertThat(auth.state().userMessage())
                    .isEqualTo("현재 로그인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    @Test
    void unresponsiveAuthenticationServerIsReportedAsOffline() throws Exception {
        try (ServerSocket unresponsiveServer = new ServerSocket(
                0, 1, InetAddress.getLoopbackAddress());
             ClientFactory factory = clientFactory(Duration.ofMillis(100));
             AuthApiClient auth = new AuthApiClient(
                     WebClient.builder("http://127.0.0.1:" + unresponsiveServer.getLocalPort())
                             .factory(factory)
                             .responseTimeout(Duration.ofMillis(200))
                             .build(),
                     new ObjectMapper(),
                     Duration.ofMillis(20),
                     Duration.ofSeconds(2))) {
            auth.beginLogin();
            await(() -> auth.state().phase() == AuthPhase.OFFLINE);

            assertThat(auth.state().userMessage())
                    .isEqualTo("로그인 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    @Test
    void failedProviderLoginOffersAUserFacingRetry() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             AuthApiClient auth = new AuthApiClient(
                     webClient(backend, factory),
                     new ObjectMapper(),
                     Duration.ofMillis(20),
                     Duration.ofSeconds(2))) {
            backend.respondToAuthExchange("FAILED");

            auth.beginLogin();
            await(() -> auth.state().phase() == AuthPhase.FAILED);

            assertThat(auth.state().userMessage())
                    .isEqualTo("Google 로그인을 완료하지 못했습니다. 다시 시도해 주세요.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPIRED", "EXCHANGED"})
    void unusableCompletedLoginAttemptRequestsANewLogin(String exchangeStatus) throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             AuthApiClient auth = new AuthApiClient(
                     webClient(backend, factory),
                     new ObjectMapper(),
                     Duration.ofMillis(20),
                     Duration.ofSeconds(2))) {
            backend.respondToAuthExchange(exchangeStatus);

            auth.beginLogin();
            await(() -> auth.state().phase() == AuthPhase.EXPIRED);

            assertThat(auth.state().userMessage())
                    .isEqualTo("로그인 시간이 만료되었습니다. 다시 시도해 주세요.");
        }
    }

    @Test
    void retryIgnoresThePreviousAttemptResponse() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             AuthApiClient auth = new AuthApiClient(
                     WebClient.builder(backend.baseUrl())
                             .factory(factory)
                             .responseTimeout(Duration.ofSeconds(2))
                             .build(),
                     new ObjectMapper(),
                     Duration.ofSeconds(5),
                     Duration.ofSeconds(10))) {
            List<URI> openedAuthorizationUris = new CopyOnWriteArrayList<>();
            auth.setStateListener(() -> {
                URI authorizationUri = auth.state().authorizationUri();
                if (authorizationUri != null) {
                    openedAuthorizationUris.add(authorizationUri);
                }
            });
            backend.respondToSequencedAuthAttempts();

            auth.beginLogin();
            await(() -> backend.authAttemptRequests.get() == 1);
            auth.beginLogin();
            backend.releaseFirstAuthAttempt();
            await(() -> openedAuthorizationUris.contains(
                    URI.create("https://accounts.example.test/login/2")));

            assertThat(openedAuthorizationUris)
                    .containsExactly(URI.create("https://accounts.example.test/login/2"));
        }
    }

    @Test
    void soundSettingsPersistOnlyTheLastValueAfterFiveHundredMilliseconds() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             MemberApiClient members = new MemberApiClient(webClient(backend, factory), new ObjectMapper())) {
            MemberBootstrap bootstrap = members.loadBootstrap("jwt-token")
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(bootstrap.nickname()).isEqualTo("달빛사냥꾼");
            assertThat(bootstrap.characters()).hasSize(2);
            assertThat(bootstrap.characters()).anySatisfy(character -> {
                assertThat(character.displayName()).isEqualTo("질풍 무녀");
                assertThat(character.unlocked()).isFalse();
                assertThat(character.startingItem().displayName()).isEqualTo("화염 부채");
                assertThat(character.skill().displayName()).isEqualTo("질풍걸음");
            });
            assertThat(members.loadSettings("jwt-token").get(2, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(new AudioSettings(false, 70, 65, TargetFps.FPS_60));

            members.saveSettingsDebounced(
                    "jwt-token", new AudioSettings(false, 10, 20, TargetFps.FPS_30));
            members.saveSettingsDebounced(
                    "jwt-token", new AudioSettings(true, 40, 50, TargetFps.AUTO));
            members.saveSettingsDebounced(
                    "jwt-token", new AudioSettings(false, 95, 85, TargetFps.FPS_60));

            await(() -> backend.countRestRequests("PATCH", "/api/v1/members/me/settings") == 1);
            RecordedRequest saved = backend.requests.stream()
                    .filter(request -> request.path().equals("/api/v1/members/me/settings")
                            && request.method().equals("PATCH"))
                    .findFirst()
                    .orElseThrow();
            assertThat(saved.authorization()).isEqualTo("Bearer jwt-token");
            assertThat(saved.body())
                    .contains("\"muted\":false")
                    .contains("\"musicVolume\":95")
                    .contains("\"effectsVolume\":85")
                    .contains("\"targetFps\":\"FPS_60\"");
        }
    }

    @Test
    void missingAndLegacySettingsKeepTheirIntendedVolumeDefaults() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             MemberApiClient members = new MemberApiClient(webClient(backend, factory), new ObjectMapper())) {
            backend.respondToSettings("{\"muted\":false}");
            assertThat(members.loadSettings("jwt-token").get(2, TimeUnit.SECONDS))
                    .isEqualTo(AudioSettings.defaults());

            backend.respondToSettings("{\"muted\":false,\"masterVolume\":42}");
            assertThat(members.loadSettings("jwt-token").get(2, TimeUnit.SECONDS))
                    .isEqualTo(new AudioSettings(false, 42, 42, TargetFps.FPS_60));
        }
    }

    @Test
    void armeriaGameSocketUsesTicketHeaderAndExchangesTypedMessages() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper())) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER", 960, 540);
            await(() -> game.snapshot().phase() == GamePhase.RUNNING);

            assertThat(game.status().state()).isEqualTo(DesktopApiStatus.State.ONLINE);
            assertThat(backend.socketAuthorization.get()).isEqualTo("Ticket opaque-ticket");
            assertThat(game.snapshot().characterId()).isEqualTo("DOKKAEBI_HUNTER");
            assertThat(game.snapshot().barrierAvailable()).isTrue();
            assertThat(game.snapshot().paused()).isFalse();
            assertThat(game.snapshot().occupiedItemSlots()).isEqualTo(1);
            assertThat(game.snapshot().itemSlots().getFirst().displayName()).isEqualTo("봉인 부적");
            assertThat(game.snapshot().chests()).hasSize(1);
            assertThat(game.snapshot().chestIndicators()).hasSize(1);
            assertThat(game.snapshot().soundEvents()).hasSize(2);

            game.setInput(new InputState(true, false, true, false));
            game.setInput(new InputState(true, false, true, false));
            await(() -> backend.countSocketInputsWith("\"up\":true") == 1);
            backend.publishLevelUpSnapshot();
            await(() -> game.snapshot().phase() == GamePhase.LEVEL_UP);
            assertThat(game.snapshot().pendingLevelUpOptions().getFirst().description())
                    .isEqualTo("레벨이 오릅니다.");
            game.chooseLevelUp("level-option-1");
            game.chooseChestReward("chest-option-1");
            await(() -> backend.hasSocketCommand("CHOOSE_CHEST_REWARD"));
            await(() -> backend.countSocketInputsWith("\"up\":true") == 2);

            assertThat(backend.socketMessages).anySatisfy(message -> assertThat(message)
                    .contains("\"type\":\"START_GAME\"")
                    .contains("\"characterId\":\"DOKKAEBI_HUNTER\"")
                    .contains("\"viewportWidth\":960")
                    .contains("\"viewportHeight\":540"));
            assertThat(backend.socketMessages).anySatisfy(message -> assertThat(message)
                    .contains("\"type\":\"INPUT_CHANGED\"")
                    .contains("\"up\":true")
                    .contains("\"left\":true")
                    .contains("\"commandSequence\":"));
            assertThat(backend.socketMessages).anySatisfy(message -> assertThat(message)
                    .contains("\"type\":\"CHOOSE_LEVEL_UP\"")
                    .contains("\"optionId\":\"level-option-1\""));
            assertThat(backend.socketMessages).anySatisfy(message -> assertThat(message)
                    .contains("\"type\":\"CHOOSE_CHEST_REWARD\"")
                    .contains("\"optionId\":\"chest-option-1\""));
            assertThat(backend.countSocketInputsWith("\"up\":true")).isEqualTo(2);
        }
    }

    @Test
    void gameSocketReceivesLargeGameSnapshotWithoutReconnecting() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper())) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER");
            await(() -> game.snapshot().phase() == GamePhase.RUNNING);

            String largeSnapshot = backend.publishMaximumEntitySnapshot();
            assertThat(largeSnapshot.getBytes(StandardCharsets.UTF_8).length)
                    .isGreaterThan(ARMERIA_DEFAULT_MAX_FRAME_PAYLOAD_LENGTH)
                    .isLessThan(GAME_MAX_FRAME_PAYLOAD_LENGTH);

            await(() -> game.snapshot().enemies().size() == 286);
            assertThat(game.snapshot().projectiles()).hasSize(160);
            assertThat(game.snapshot().soulFlames()).hasSize(300);
            assertThat(game.status().state()).isEqualTo(DesktopApiStatus.State.ONLINE);

            Thread.sleep(1_200L);
            assertThat(backend.socketConnections.get()).isEqualTo(1);
            assertThat(backend.ticketRequests.get()).isEqualTo(1);
        }
    }

    @Test
    void gameSocketStaysOpenBeyondRestResponseTimeoutWithoutAnotherTicket() throws Exception {
        assertThat(SOCKET_STABILITY_WINDOW)
                .isGreaterThanOrEqualTo(REST_RESPONSE_TIMEOUT.multipliedBy(5));
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper())) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER");
            await(() -> game.snapshot().phase() == GamePhase.RUNNING);

            waitForSocketStability();

            assertThat(game.status().state()).isEqualTo(DesktopApiStatus.State.ONLINE);
            assertThat(backend.socketConnections.get()).isEqualTo(1);
            assertThat(backend.ticketRequests.get()).isEqualTo(1);
        }
    }

    @Test
    void stalledWebSocketHandshakeStopsAtConnectDeadlineAndCancelsTheUpgrade() throws Exception {
        try (MockPlatform backend = new MockPlatform(true);
             ClientFactory factory = clientFactory(Duration.ofMillis(150));
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper())) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER");

            await(() -> game.status().state() == DesktopApiStatus.State.OFFLINE);
            await(() -> backend.cancelledHandshakes.get() == 1);

            assertThat(backend.ticketRequests.get()).isEqualTo(1);
            assertThat(backend.socketConnections.get()).isZero();
        }
    }

    @Test
    void reconnectsRunningGameWithSessionIdAndPreservesInputSequence() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper())) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER");
            await(() -> game.snapshot().phase() == GamePhase.RUNNING);
            game.setInput(new InputState(false, false, false, true));
            await(() -> backend.countSocketInputsWith("\"right\":true") == 1);
            long sequenceBeforeDrop = backend.maxInputSequence();

            backend.dropGameSocket();

            await(() -> backend.socketConnections.get() >= 2
                    && game.status().state() == DesktopApiStatus.State.ONLINE);
            await(() -> backend.maxInputSequence() > sequenceBeforeDrop);
            await(() -> backend.countSocketInputsWith("\"right\":true") == 2);
            assertThat(backend.countRestRequests("POST", "/api/v1/game/socket-tickets"))
                    .isEqualTo(2);
            assertThat(backend.reconnectSessionIds).contains("session-1");
            assertThat(backend.countSocketCommands("START_GAME")).isEqualTo(1);

            long sequenceAfterReconnect = backend.maxInputSequence();
            game.setInput(new InputState(true, false, false, false));
            await(() -> backend.maxInputSequence() > sequenceAfterReconnect);

            waitForSocketStability();
            assertThat(game.status().state()).isEqualTo(DesktopApiStatus.State.ONLINE);
            assertThat(backend.socketConnections.get()).isEqualTo(2);
            assertThat(backend.ticketRequests.get()).isEqualTo(2);

            int connectionsBeforeDisconnect = backend.socketConnections.get();
            game.disconnect();
            Thread.sleep(1_200L);
            assertThat(backend.socketConnections.get()).isEqualTo(connectionsBeforeDisconnect);
        }
    }

    @Test
    void resizeAndPauseRequestsAreDebouncedAndRestoredAfterReconnect() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper())) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER", 5_000, 3_000);
            await(() -> game.snapshot().phase() == GamePhase.RUNNING);
            assertThat(backend.socketMessages).anySatisfy(message -> assertThat(message)
                    .contains("\"type\":\"START_GAME\"")
                    .contains("\"viewportWidth\":3840")
                    .contains("\"viewportHeight\":2160"));

            game.setViewportSize(1000, 560);
            game.setViewportSize(1100, 620);
            game.setViewportSize(1200, 680);
            await(() -> backend.countSocketCommands("VIEWPORT_CHANGED") == 1);
            assertThat(backend.socketMessages).anySatisfy(message -> assertThat(message)
                    .contains("\"type\":\"VIEWPORT_CHANGED\"")
                    .contains("\"viewportWidth\":1200")
                    .contains("\"viewportHeight\":680"));

            game.setInput(new InputState(false, false, false, true));
            await(() -> backend.countSocketInputsWith("\"right\":true") == 1);
            game.setGamePaused(true);
            await(() -> backend.countSocketCommands("SET_GAME_PAUSED") == 1);
            assertThat(backend.socketMessages).anySatisfy(message -> assertThat(message)
                    .contains("\"type\":\"SET_GAME_PAUSED\"")
                    .contains("\"paused\":true"));

            backend.dropGameSocket();
            await(() -> backend.socketConnections.get() >= 2
                    && game.status().state() == DesktopApiStatus.State.ONLINE);
            await(() -> backend.countSocketCommands("SET_GAME_PAUSED") == 2);

            assertThat(backend.countSocketCommands("START_GAME")).isEqualTo(1);
            assertThat(backend.countSocketCommands("VIEWPORT_CHANGED")).isEqualTo(2);
            assertThat(backend.socketMessages.stream()
                    .filter(message -> message.contains("\"type\":\"SET_GAME_PAUSED\""))
                    .allMatch(message -> message.contains("\"paused\":true")))
                    .isTrue();

            game.setGamePaused(false);
            await(() -> backend.countSocketCommands("SET_GAME_PAUSED") == 3);
            assertThat(backend.socketMessages).anySatisfy(message -> assertThat(message)
                    .contains("\"type\":\"SET_GAME_PAUSED\"")
                    .contains("\"paused\":false"));
        }
    }

    @Test
    void explicitDisconnectWinsOverAnInFlightSocketCloseHandler() throws Exception {
        Logger clientLogger = (Logger) LoggerFactory.getLogger(DesktopApiClient.class);
        CountDownLatch closeHandlerEntered = new CountDownLatch(1);
        CountDownLatch continueCloseHandler = new CountDownLatch(1);
        AppenderBase<ILoggingEvent> blockingAppender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                if (!event.getFormattedMessage().startsWith("Game WebSocket closed unexpectedly:")) {
                    return;
                }
                closeHandlerEntered.countDown();
                try {
                    continueCloseHandler.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        blockingAppender.start();
        clientLogger.addAppender(blockingAppender);
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper())) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER");
            await(() -> game.snapshot().phase() == GamePhase.RUNNING);

            CompletableFuture<Void> socketDrop = CompletableFuture.runAsync(backend::dropGameSocket);
            assertThat(closeHandlerEntered.await(2, TimeUnit.SECONDS)).isTrue();

            game.disconnect();
            continueCloseHandler.countDown();
            socketDrop.get(2, TimeUnit.SECONDS);
            Thread.sleep(1_200L);

            assertThat(game.status().state()).isEqualTo(DesktopApiStatus.State.OFFLINE);
            assertThat(backend.socketConnections.get()).isEqualTo(1);
            assertThat(backend.ticketRequests.get()).isEqualTo(1);
        } finally {
            continueCloseHandler.countDown();
            clientLogger.detachAppender(blockingAppender);
            blockingAppender.stop();
        }
    }

    private static ClientFactory clientFactory() {
        return clientFactory(Duration.ofSeconds(1));
    }

    private static ClientFactory clientFactory(Duration connectTimeout) {
        return ClientFactory.builder().connectTimeout(connectTimeout).build();
    }

    private static WebClient webClient(MockPlatform backend, ClientFactory factory) {
        return WebClient.builder(backend.baseUrl())
                .factory(factory)
                .responseTimeout(REST_RESPONSE_TIMEOUT)
                .build();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static void waitForSocketStability() throws InterruptedException {
        Thread.sleep(SOCKET_STABILITY_WINDOW.toMillis());
    }

    private static final class MockPlatform implements AutoCloseable {
        private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
        private final List<String> socketMessages = new CopyOnWriteArrayList<>();
        private final List<WebSocketWriter> socketWriters = new CopyOnWriteArrayList<>();
        private final List<String> reconnectSessionIds = new CopyOnWriteArrayList<>();
        private final AtomicReference<String> socketAuthorization = new AtomicReference<>();
        private final AtomicInteger exchanges = new AtomicInteger();
        private final AtomicInteger authAttemptRequests = new AtomicInteger();
        private final AtomicInteger socketConnections = new AtomicInteger();
        private final AtomicInteger ticketRequests = new AtomicInteger();
        private final AtomicInteger cancelledHandshakes = new AtomicInteger();
        private final AtomicReference<Socket> stalledHandshakeConnection = new AtomicReference<>();
        private final CompletableFuture<Void> firstAuthAttemptRelease = new CompletableFuture<>();
        private volatile HttpStatus authAttemptStatus = HttpStatus.CREATED;
        private volatile boolean sequencedAuthAttempts;
        private volatile String authExchangeStatus;
        private volatile String authAttemptBody = """
                {"attemptId":"attempt-1","pollToken":"poll-secret",
                 "authorizationUri":"https://accounts.example.test/login",
                 "expiresAt":"2099-01-01T00:00:00Z"}
                """;
        private volatile String settingsBody =
                "{\"muted\":false,\"musicVolume\":70,\"effectsVolume\":65,"
                        + "\"targetFps\":\"FPS_60\"}";
        private final ServerSocket stalledHandshakeServer;
        private final Server server;

        private MockPlatform() {
            this(false);
        }

        private MockPlatform(boolean stallWebSocketHandshake) {
            if (stallWebSocketHandshake) {
                try {
                    stalledHandshakeServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                Thread.ofPlatform().daemon().start(this::acceptStalledHandshake);
            } else {
                stalledHandshakeServer = null;
            }
            server = Server.builder()
                    .http(0)
                    .service("/ws/game", WebSocketService.of(this::openSocket))
                    .serviceUnder("/", (context, request) -> HttpResponse.of(
                            request.aggregate().thenApply(aggregated -> handleRest(context, aggregated))))
                    .build();
            server.start().join();
        }

        private void acceptStalledHandshake() {
            try (Socket connection = stalledHandshakeServer.accept()) {
                stalledHandshakeConnection.set(connection);
                connection.getInputStream().readAllBytes();
                cancelledHandshakes.incrementAndGet();
            } catch (IOException ignored) {
                // Closing the fixture also releases an accept/read still in progress.
            }
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.activeLocalPort(SessionProtocol.HTTP);
        }

        private String socketUrl() {
            int port = stalledHandshakeServer == null
                    ? server.activeLocalPort(SessionProtocol.HTTP)
                    : stalledHandshakeServer.getLocalPort();
            return "ws://127.0.0.1:" + port + "/ws/game";
        }

        private boolean hasRestRequest(String method, String path) {
            return requests.stream().anyMatch(request ->
                    request.method().equals(method) && request.path().equals(path));
        }

        private void respondToAuthAttempt(HttpStatus status, String body) {
            authAttemptStatus = status;
            authAttemptBody = body;
        }

        private void respondToSequencedAuthAttempts() {
            sequencedAuthAttempts = true;
        }

        private void respondToAuthExchange(String status) {
            authExchangeStatus = status;
        }

        private void respondToSettings(String body) {
            settingsBody = body;
        }

        private void releaseFirstAuthAttempt() {
            firstAuthAttemptRelease.complete(null);
        }

        private long countRestRequests(String method, String path) {
            return requests.stream().filter(request ->
                    request.method().equals(method) && request.path().equals(path)).count();
        }

        private boolean hasSocketCommand(String type) {
            return socketMessages.stream().anyMatch(message ->
                    message.contains("\"type\":\"" + type + "\""));
        }

        private long countSocketCommands(String type) {
            return socketMessages.stream().filter(message ->
                    message.contains("\"type\":\"" + type + "\"")).count();
        }

        private long countSocketInputsWith(String fragment) {
            return socketMessages.stream().filter(message ->
                    message.contains("\"type\":\"INPUT_CHANGED\"") && message.contains(fragment)).count();
        }

        private long maxInputSequence() {
            return socketMessages.stream()
                    .filter(message -> message.contains("\"type\":\"INPUT_CHANGED\""))
                    .map(COMMAND_SEQUENCE_PATTERN::matcher)
                    .filter(Matcher::find)
                    .mapToLong(matcher -> Long.parseLong(matcher.group(1)))
                    .max()
                    .orElse(-1L);
        }

        private void publishLevelUpSnapshot() {
            socketWriters.getLast().tryWrite(snapshotEnvelope("LEVEL_UP", 2L));
        }

        private String publishMaximumEntitySnapshot() {
            String snapshot = snapshotEnvelope("RUNNING", 2L)
                    .replace("\"killCount\":0", "\"killCount\":286")
                    .replace("\"enemies\":[]", "\"enemies\":"
                            + entities(286, "shadow-dokkaebi"))
                    .replace("\"projectiles\":[]", "\"projectiles\":"
                            + entities(160, "seal-talisman"))
                    .replace("\"soulFlames\":[]", "\"soulFlames\":"
                            + entities(300, "soul-flame"));
            socketWriters.getLast().tryWrite(snapshot);
            return snapshot;
        }

        private void dropGameSocket() {
            socketWriters.getLast().close();
        }

        private HttpResponse handleRest(ServiceRequestContext context, AggregatedHttpRequest request) {
            String path = context.path();
            String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            requests.add(new RecordedRequest(request.method().name(), path, request.contentUtf8(), authorization));

            if (path.equals("/api/v1/auth/desktop/attempts")) {
                if (sequencedAuthAttempts) {
                    int requestNumber = authAttemptRequests.incrementAndGet();
                    HttpResponse response = json(HttpStatus.CREATED, """
                            {"attemptId":"attempt-__NUMBER__","pollToken":"poll-secret-__NUMBER__",
                             "authorizationUri":"https://accounts.example.test/login/__NUMBER__",
                             "expiresAt":"2099-01-01T00:00:00Z"}
                            """.replace("__NUMBER__", Integer.toString(requestNumber)));
                    return requestNumber == 1
                            ? HttpResponse.of(firstAuthAttemptRelease.thenApply(ignored -> response))
                            : response;
                }
                return json(authAttemptStatus, authAttemptBody);
            }
            if (path.equals("/api/v1/auth/desktop/attempts/attempt-1/exchange")) {
                if (authExchangeStatus != null) {
                    return json(HttpStatus.OK, "{\"status\":\"" + authExchangeStatus + "\"}");
                }
                if (exchanges.getAndIncrement() == 0) {
                    return json(HttpStatus.ACCEPTED, "{\"status\":\"PENDING\"}");
                }
                return json(HttpStatus.OK,
                        "{\"status\":\"REGISTRATION_REQUIRED\","
                                + "\"registrationTicket\":\"registration-ticket\"}");
            }
            if (path.equals("/api/v1/auth/desktop/registrations")) {
                return json(HttpStatus.CREATED,
                        "{\"accessToken\":\"jwt-token\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}");
            }
            if (path.equals("/api/v1/members/me/bootstrap")) {
                return json(HttpStatus.OK, """
                        {"member":{"nickname":"달빛사냥꾼"},
                         "characters":[
                           {"id":"dokkaebi-hunter","displayName":"도깨비 사냥꾼",
                            "description":"봉인 부적으로 그림자를 사냥한다.","unlocked":true,
                            "startingItem":{"id":"seal-talisman","displayName":"봉인 부적"},
                            "skill":{"id":"protective-barrier","displayName":"호신결계",
                                     "description":"한 번 충돌을 막는다."}},
                           {"id":"gale-shaman","displayName":"질풍 무녀",
                            "description":"빠른 발걸음으로 야행한다.","unlocked":false,
                            "startingItem":{"id":"flame-fan","displayName":"화염 부채"},
                            "skill":{"id":"gale-step","displayName":"질풍걸음",
                                     "description":"이동 속도가 25% 증가한다."}}
                         ]}
                        """);
            }
            if (path.equals("/api/v1/members/me/settings") && request.method().name().equals("GET")) {
                return json(HttpStatus.OK, settingsBody);
            }
            if (path.equals("/api/v1/members/me/settings") && request.method().name().equals("PATCH")) {
                return json(HttpStatus.OK, request.contentUtf8());
            }
            if (path.equals("/api/v1/game/socket-tickets")) {
                int ticketNumber = ticketRequests.incrementAndGet();
                String ticket = ticketNumber == 1
                        ? "opaque-ticket"
                        : "opaque-ticket-" + ticketNumber;
                return json(HttpStatus.CREATED,
                        "{\"ticket\":\"" + ticket + "\",\"webSocketUri\":\"" + socketUrl()
                                + "\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}");
            }
            return HttpResponse.of(HttpStatus.NOT_FOUND);
        }

        private WebSocket openSocket(ServiceRequestContext context, WebSocket inbound) {
            socketConnections.incrementAndGet();
            socketAuthorization.set(context.request().headers().get(HttpHeaderNames.AUTHORIZATION));
            WebSocketWriter outbound = WebSocket.streaming();
            socketWriters.add(outbound);
            String reconnectSessionId = context.queryParam("sessionId");
            if (reconnectSessionId != null) {
                reconnectSessionIds.add(reconnectSessionId);
                outbound.tryWrite(snapshotEnvelope("RUNNING", 1L));
            }
            inbound.subscribe(new Subscriber<>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(WebSocketFrame frame) {
                    if (frame.type() != WebSocketFrameType.TEXT) {
                        return;
                    }
                    String message = frame.text();
                    socketMessages.add(message);
                    if (message.contains("\"type\":\"START_GAME\"")) {
                        outbound.tryWrite(snapshotEnvelope("RUNNING", 1L));
                    }
                    if (message.contains("\"type\":\"CHOOSE_LEVEL_UP\"")) {
                        outbound.tryWrite(snapshotEnvelope("RUNNING", 3L));
                    }
                }

                @Override
                public void onError(Throwable cause) {
                    outbound.close(cause);
                }

                @Override
                public void onComplete() {
                    outbound.close();
                }
            });
            return outbound;
        }

        @Override
        public void close() {
            socketWriters.forEach(WebSocketWriter::close);
            Socket stalledConnection = stalledHandshakeConnection.get();
            if (stalledConnection != null) {
                try {
                    stalledConnection.close();
                } catch (IOException ignored) {
                    // Best-effort test fixture cleanup.
                }
            }
            if (stalledHandshakeServer != null) {
                try {
                    stalledHandshakeServer.close();
                } catch (IOException ignored) {
                    // Best-effort test fixture cleanup.
                }
            }
            server.stop().join();
        }

        private static HttpResponse json(HttpStatus status, String body) {
            return HttpResponse.of(status, MediaType.JSON_UTF_8, body);
        }

        private static String snapshotEnvelope(String phase, long sequence) {
            String levelUpOptions = "LEVEL_UP".equals(phase)
                    ? "[{\"optionId\":\"level-option-1\",\"kind\":\"ITEM\","
                            + "\"displayName\":\"봉인 부적\",\"description\":\"레벨이 오릅니다.\"}]"
                    : "[]";
            return """
                    {"type":"GAME_SNAPSHOT","sequence":__SEQUENCE__,"payload":{
                     "sessionId":"session-1","snapshot":{
                      "phase":"__PHASE__","paused":false,"elapsedSeconds":1.0,
                      "level":1,"experience":0,"experienceToNextLevel":5,"killCount":0,
                      "character":"DOKKAEBI_HUNTER","barrierAvailable":true,
                      "invulnerabilityRemainingSeconds":0.0,
                      "player":{"id":1,"x":0.0,"y":0.0,"radius":18.0,"rotationDegrees":180.0},
                      "enemies":[],"projectiles":[],"soulFlames":[],"upgradeChoices":[],
                      "items":[{"itemId":"seal-talisman","displayName":"봉인 부적","level":1}],
                      "evolutions":[],"occupiedItemSlots":1,
                      "chests":[{"id":7,"type":"YELLOW","x":800.0,"y":0.0,"opened":false}],
                      "chestIndicators":[{"chestId":7,"directionX":1.0,"directionY":0.0,"distance":800.0}],
                      "levelUpOptions":__LEVEL_UP_OPTIONS__,"chestRewardOptions":[],
                      "soundEvents":[{"id":1,"type":"SEAL_TALISMAN_ATTACK"},{"id":2,"type":"DEFEAT"}],
                      "futureServerField":"ignored"
                    }}}
                    """
                    .replace("__SEQUENCE__", Long.toString(sequence))
                    .replace("__PHASE__", phase)
                    .replace("__LEVEL_UP_OPTIONS__", levelUpOptions);
        }

        private static String entities(int count, String kindId) {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < count; index++) {
                if (index > 0) {
                    json.append(',');
                }
                json.append("{\"id\":")
                        .append(index + 10L)
                        .append(",\"x\":1234.56789,\"y\":-987.654321,")
                        .append("\"radius\":17.0,\"rotationDegrees\":359.99,\"kindId\":\"")
                        .append(kindId)
                        .append("\"}");
            }
            return json.append(']').toString();
        }
    }

    private record RecordedRequest(String method, String path, String body, String authorization) {
    }
}
