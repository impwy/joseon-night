package kr.joseonnight.desktop;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
import kr.joseonnight.desktop.settings.AudioSettings;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.ObjectMapper;

class DesktopApplicationIntegrationTest {
    private static final Pattern COMMAND_SEQUENCE_PATTERN = Pattern.compile(
            "\\\"commandSequence\\\":(\\d+)");
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
    void soundSettingsPersistOnlyTheLastValueAfterFiveHundredMilliseconds() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             MemberApiClient members = new MemberApiClient(webClient(backend, factory), new ObjectMapper())) {
            assertThat(members.loadBootstrap("jwt-token").get(2, java.util.concurrent.TimeUnit.SECONDS)
                    .nickname()).isEqualTo("달빛사냥꾼");
            assertThat(members.loadSettings("jwt-token").get(2, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(new AudioSettings(false, 70));

            members.saveSettingsDebounced("jwt-token", new AudioSettings(false, 10));
            members.saveSettingsDebounced("jwt-token", new AudioSettings(true, 40));
            members.saveSettingsDebounced("jwt-token", new AudioSettings(false, 95));

            await(() -> backend.countRestRequests("PATCH", "/api/v1/members/me/settings") == 1);
            RecordedRequest saved = backend.requests.stream()
                    .filter(request -> request.path().equals("/api/v1/members/me/settings")
                            && request.method().equals("PATCH"))
                    .findFirst()
                    .orElseThrow();
            assertThat(saved.authorization()).isEqualTo("Bearer jwt-token");
            assertThat(saved.body())
                    .contains("\"muted\":false")
                    .contains("\"masterVolume\":95");
        }
    }

    @Test
    void armeriaGameSocketUsesTicketHeaderAndExchangesTypedMessages() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper(),
                     Duration.ofSeconds(2))) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER");
            await(() -> game.snapshot().phase() == GamePhase.RUNNING);

            assertThat(game.status().state()).isEqualTo(DesktopApiStatus.State.ONLINE);
            assertThat(backend.socketAuthorization.get()).isEqualTo("Ticket opaque-ticket");
            assertThat(game.snapshot().characterId()).isEqualTo("DOKKAEBI_HUNTER");
            assertThat(game.snapshot().barrierAvailable()).isTrue();
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
                    .contains("\"characterId\":\"DOKKAEBI_HUNTER\""));
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
    void reconnectsRunningGameWithSessionIdAndPreservesInputSequence() throws Exception {
        try (MockPlatform backend = new MockPlatform();
             ClientFactory factory = clientFactory();
             DesktopApiClient game = new DesktopApiClient(
                     webClient(backend, factory),
                     factory,
                     new ObjectMapper(),
                     Duration.ofSeconds(2))) {
            game.startNewGame("jwt-token", "DOKKAEBI_HUNTER");
            await(() -> game.snapshot().phase() == GamePhase.RUNNING);
            game.setInput(new InputState(false, false, false, true));
            await(() -> backend.countSocketInputsWith("\"right\":true") == 1);
            long sequenceBeforeDrop = backend.maxInputSequence();

            backend.dropGameSocket();

            await(() -> backend.socketConnections.get() >= 2
                    && game.status().state() == DesktopApiStatus.State.ONLINE);
            await(() -> backend.maxInputSequence() > sequenceBeforeDrop);
            assertThat(backend.countRestRequests("POST", "/api/v1/game/socket-tickets"))
                    .isGreaterThanOrEqualTo(2);
            assertThat(backend.reconnectSessionIds).contains("session-1");
            assertThat(backend.countSocketCommands("START_GAME")).isEqualTo(1);

            long sequenceAfterReconnect = backend.maxInputSequence();
            game.setInput(new InputState(true, false, false, false));
            await(() -> backend.maxInputSequence() > sequenceAfterReconnect);

            int connectionsBeforeDisconnect = backend.socketConnections.get();
            game.disconnect();
            Thread.sleep(1_200L);
            assertThat(backend.socketConnections.get()).isEqualTo(connectionsBeforeDisconnect);
        }
    }

    private static ClientFactory clientFactory() {
        return ClientFactory.builder().connectTimeout(Duration.ofSeconds(1)).build();
    }

    private static WebClient webClient(MockPlatform backend, ClientFactory factory) {
        return WebClient.builder(backend.baseUrl())
                .factory(factory)
                .responseTimeout(Duration.ofSeconds(2))
                .build();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static final class MockPlatform implements AutoCloseable {
        private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
        private final List<String> socketMessages = new CopyOnWriteArrayList<>();
        private final List<WebSocketWriter> socketWriters = new CopyOnWriteArrayList<>();
        private final List<String> reconnectSessionIds = new CopyOnWriteArrayList<>();
        private final AtomicReference<String> socketAuthorization = new AtomicReference<>();
        private final AtomicInteger exchanges = new AtomicInteger();
        private final AtomicInteger socketConnections = new AtomicInteger();
        private final AtomicInteger ticketRequests = new AtomicInteger();
        private final Server server;

        private MockPlatform() {
            server = Server.builder()
                    .http(0)
                    .service("/ws/game", WebSocketService.of(this::openSocket))
                    .serviceUnder("/", (context, request) -> HttpResponse.of(
                            request.aggregate().thenApply(aggregated -> handleRest(context, aggregated))))
                    .build();
            server.start().join();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.activeLocalPort(SessionProtocol.HTTP);
        }

        private String socketUrl() {
            return "ws://127.0.0.1:" + server.activeLocalPort(SessionProtocol.HTTP) + "/ws/game";
        }

        private boolean hasRestRequest(String method, String path) {
            return requests.stream().anyMatch(request ->
                    request.method().equals(method) && request.path().equals(path));
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

        private void dropGameSocket() {
            socketWriters.getLast().close();
        }

        private HttpResponse handleRest(ServiceRequestContext context, AggregatedHttpRequest request) {
            String path = context.path();
            String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            requests.add(new RecordedRequest(request.method().name(), path, request.contentUtf8(), authorization));

            if (path.equals("/api/v1/auth/desktop/attempts")) {
                return json(HttpStatus.CREATED, """
                        {"attemptId":"attempt-1","pollToken":"poll-secret",
                         "authorizationUri":"https://accounts.example.test/login",
                         "expiresAt":"2099-01-01T00:00:00Z"}
                        """);
            }
            if (path.equals("/api/v1/auth/desktop/attempts/attempt-1/exchange")) {
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
                         "characters":[{"characterId":"DOKKAEBI_HUNTER","displayName":"도깨비 사냥꾼"}]}
                        """);
            }
            if (path.equals("/api/v1/members/me/settings") && request.method().name().equals("GET")) {
                return json(HttpStatus.OK, "{\"muted\":false,\"masterVolume\":70}");
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
                      "phase":"__PHASE__","elapsedSeconds":1.0,"remainingSeconds":299.0,
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
                      "soundEvents":[{"id":1,"type":"TALISMAN_FIRED"},{"id":2,"type":"ENEMY_DEFEATED"}],
                      "futureServerField":"ignored"
                    }}}
                    """
                    .replace("__SEQUENCE__", Long.toString(sequence))
                    .replace("__PHASE__", phase)
                    .replace("__LEVEL_UP_OPTIONS__", levelUpOptions);
        }
    }

    private record RecordedRequest(String method, String path, String body, String authorization) {
    }
}
