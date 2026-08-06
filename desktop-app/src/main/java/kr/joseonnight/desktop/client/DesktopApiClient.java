package kr.joseonnight.desktop.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.websocket.WebSocketClient;
import com.linecorp.armeria.client.websocket.WebSocketSession;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.websocket.WebSocketFrame;
import com.linecorp.armeria.common.websocket.WebSocketFrameType;
import com.linecorp.armeria.common.websocket.WebSocketWriter;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import kr.joseonnight.desktop.gameplay.DesktopApiStatus;
import kr.joseonnight.desktop.gameplay.GamePhase;
import kr.joseonnight.desktop.gameplay.GameSnapshot;
import kr.joseonnight.desktop.gameplay.InputState;
import kr.joseonnight.desktop.gameplay.UpgradeType;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Armeria WebSocket game client.
 *
 * <p>A short-lived socket ticket is requested with the memory-only bearer JWT. The opaque ticket
 * is then sent in the WebSocket {@code Authorization: Ticket ...} header and never in a URL.</p>
 */
@Slf4j
public final class DesktopApiClient implements AutoCloseable {
    static final String SOCKET_TICKETS_PATH = "/api/v1/game/socket-tickets";
    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(1);
    private static final Duration RECONNECT_WINDOW = Duration.ofSeconds(30);
    static final Duration VIEWPORT_DEBOUNCE = Duration.ofMillis(150);
    private static final int DEFAULT_VIEWPORT_WIDTH = 1280;
    private static final int DEFAULT_VIEWPORT_HEIGHT = 720;
    private static final int MIN_VIEWPORT_WIDTH = 640;
    private static final int MIN_VIEWPORT_HEIGHT = 360;
    static final int MAX_VIEWPORT_WIDTH = 3840;
    static final int MAX_VIEWPORT_HEIGHT = 2160;
    private static final int MAX_GAME_FRAME_PAYLOAD_LENGTH = 256 * 1024;

    private final WebClient restClient;
    private final ClientFactory clientFactory;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService worker;
    private final long handshakeTimeoutMillis;
    private final AtomicReference<GameSnapshot> latestSnapshot = new AtomicReference<>(GameSnapshot.lobby());
    private final AtomicReference<DesktopApiStatus> status =
            new AtomicReference<>(DesktopApiStatus.offline("게임을 시작하면 서버에 연결합니다."));
    private final AtomicReference<InputState> requestedInput = new AtomicReference<>(InputState.idle());
    private final AtomicReference<ViewportSize> requestedViewport = new AtomicReference<>(
            new ViewportSize(DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT));
    private final AtomicBoolean requestedPaused = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong connectionGeneration = new AtomicLong();
    private final AtomicLong requestIds = new AtomicLong();
    private final AtomicLong inputSequence = new AtomicLong();
    private final AtomicLong viewportRevision = new AtomicLong();

    private volatile Runnable stateListener = () -> { };
    private volatile WebSocketWriter outbound;
    private volatile InputState sentInput;
    private volatile long latestServerSequence = -1L;
    private volatile String currentAccessToken;
    private volatile String currentCharacterId;
    private volatile String currentSessionId;
    private volatile long reconnectDeadlineNanos;

    public DesktopApiClient(
            WebClient restClient,
            ClientFactory clientFactory,
            ObjectMapper objectMapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        handshakeTimeoutMillis = resolveHandshakeTimeoutMillis(clientFactory);
        worker = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform()
                .daemon()
                .name("desktop-game-socket")
                .unstarted(task));
    }

    public GameSnapshot snapshot() {
        return latestSnapshot.get();
    }

    public DesktopApiStatus status() {
        return status.get();
    }

    public void setStateListener(Runnable listener) {
        stateListener = Objects.requireNonNull(listener, "listener");
        notifyStateChanged();
    }

    public void refresh() {
        notifyStateChanged();
    }

    public void startNewGame(String accessToken, String characterId) {
        String token = requireText(accessToken, "accessToken");
        String selectedCharacter = requireText(characterId, "characterId");
        currentAccessToken = token;
        currentCharacterId = selectedCharacter;
        startConnection(token, selectedCharacter);
    }

    public void startNewGame(
            String accessToken,
            String characterId,
            double viewportWidth,
            double viewportHeight) {
        requestedViewport.set(viewportSize(viewportWidth, viewportHeight));
        startNewGame(accessToken, characterId);
    }

    /** Restarts with the in-memory session and character selected for the previous run. */
    public void startNewGame() {
        String token = currentAccessToken;
        String selectedCharacter = currentCharacterId;
        if (token == null || selectedCharacter == null) {
            updateStatus(DesktopApiStatus.error("로그인 후 캐릭터를 선택해 주세요."));
            return;
        }
        startConnection(token, selectedCharacter);
    }

    public void disconnect() {
        connectionGeneration.incrementAndGet();
        viewportRevision.incrementAndGet();
        reconnectDeadlineNanos = 0L;
        currentSessionId = null;
        closeCurrentSocket();
        requestedInput.set(InputState.idle());
        requestedPaused.set(false);
        sentInput = null;
        latestSnapshot.set(GameSnapshot.lobby());
        updateStatus(DesktopApiStatus.offline("게임 서버 연결을 종료했습니다."));
    }

    public void clearSession() {
        disconnect();
        currentAccessToken = null;
        currentCharacterId = null;
    }

    /** Sends input only when the held-key state actually changes. */
    public void setInput(InputState input) {
        InputState requested = Objects.requireNonNull(input, "input");
        InputState next = requestedPaused.get()
                ? InputState.idle()
                : requested;
        InputState previous = requestedInput.getAndSet(next);
        if (!next.equals(previous) && !closed.get()) {
            worker.execute(() -> sendInputIfChanged(next));
        }
    }

    /**
     * Retained for callers built against the first vertical slice. Simulation time now belongs to
     * the server; this method validates the cadence and only flushes changed input.
     */
    public boolean tick(double deltaSeconds, int steps) {
        if (!(deltaSeconds > 0.0) || !Double.isFinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be finite and positive");
        }
        if (steps < 1 || steps > 15) {
            throw new IllegalArgumentException("steps must be between 1 and 15");
        }
        if (closed.get() || status.get().state() != DesktopApiStatus.State.ONLINE) {
            return false;
        }
        InputState input = requestedInput.get();
        worker.execute(() -> sendInputIfChanged(input));
        return true;
    }

    public void chooseLevelUp(String optionId) {
        String choice = requireText(optionId, "optionId");
        executeCommand("CHOOSE_LEVEL_UP", Map.of("optionId", choice));
    }

    public void chooseUpgrade(UpgradeType upgradeType) {
        chooseLevelUp(Objects.requireNonNull(upgradeType, "upgradeType").name());
    }

    public void chooseChestReward(String optionId) {
        String choice = requireText(optionId, "optionId");
        executeCommand("CHOOSE_CHEST_REWARD", Map.of("optionId", choice));
    }

    /** Stores the logical canvas size and sends only the final value of a resize burst. */
    public void setViewportSize(double width, double height) {
        ViewportSize next = viewportSize(width, height);
        ViewportSize previous = requestedViewport.getAndSet(next);
        if (next.equals(previous) || closed.get()) {
            return;
        }
        long revision = viewportRevision.incrementAndGet();
        worker.schedule(
                () -> sendViewportIfCurrent(revision),
                VIEWPORT_DEBOUNCE.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /** Requests a server-authoritative pause without changing the current game phase. */
    public void setGamePaused(boolean paused) {
        boolean previous = requestedPaused.getAndSet(paused);
        if (paused) {
            requestedInput.set(InputState.idle());
        }
        if (previous == paused || closed.get()) {
            return;
        }
        worker.execute(() -> {
            if (paused) {
                sendInputIfChanged(InputState.idle());
            }
            if (outbound != null) {
                sendPauseRequest(paused);
            }
        });
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            connectionGeneration.incrementAndGet();
            closeCurrentSocket();
            currentAccessToken = null;
            currentCharacterId = null;
            currentSessionId = null;
            reconnectDeadlineNanos = 0L;
            viewportRevision.incrementAndGet();
            worker.shutdownNow();
        }
    }

    private void startConnection(String accessToken, String characterId) {
        if (closed.get()) {
            return;
        }
        long generation = connectionGeneration.incrementAndGet();
        reconnectDeadlineNanos = 0L;
        currentSessionId = null;
        closeCurrentSocket();
        requestedInput.set(InputState.idle());
        requestedPaused.set(false);
        sentInput = null;
        inputSequence.set(0L);
        latestServerSequence = -1L;
        latestSnapshot.set(GameSnapshot.lobby());
        updateStatus(DesktopApiStatus.connecting());
        worker.execute(() -> connect(generation, accessToken, characterId, null, 0L));
    }

    private void connect(
            long generation,
            String accessToken,
            String characterId,
            String reconnectSessionId,
            long deadlineNanos
    ) {
        try {
            SocketTicketResponse ticket = requestSocketTicket(accessToken);
            if (!isCurrent(generation)) {
                return;
            }
            URI socketUri = URI.create(requireText(ticket.webSocketUri(), "webSocketUri"));
            String scheme = socketUri.getScheme();
            if (!("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))
                    || socketUri.getRawAuthority() == null) {
                throw new IOException("invalid WebSocket URI");
            }
            // A WebSocket upgrade must use explicit HTTP/1 so the REST client's pooled HTTP/2
            // connection cannot accidentally be reused for a GET upgrade over h2.
            String armeriaScheme = "wss".equalsIgnoreCase(scheme) ? "ws+h1" : "ws+h1c";
            URI baseUri = URI.create(armeriaScheme + "://" + socketUri.getRawAuthority());
            String path = socketUri.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (socketUri.getRawQuery() != null) {
                path += "?" + socketUri.getRawQuery();
            }
            if (reconnectSessionId != null) {
                String separator = path.contains("?") ? "&" : "?";
                path += separator + "sessionId=" + URLEncoder.encode(
                        reconnectSessionId, StandardCharsets.UTF_8);
            }

            AtomicReference<ClientRequestContext> handshakeContext = new AtomicReference<>();
            WebSocketClient socketClient = WebSocketClient.builder(baseUri)
                    .factory(clientFactory)
                    .maxFramePayloadLength(MAX_GAME_FRAME_PAYLOAD_LENGTH)
                    .contextCustomizer(handshakeContext::set)
                    .build();
            HttpHeaders headers = HttpHeaders.of(
                    HttpHeaderNames.AUTHORIZATION,
                    "Ticket " + requireText(ticket.ticket(), "ticket"));
            CompletableFuture<WebSocketSession> handshake = socketClient.connect(path, headers);
            WebSocketSession session;
            try {
                session = handshake.get(handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                cancelHandshake(handshake, handshakeContext.get());
                throw new IOException("WebSocket handshake timed out", exception);
            } catch (InterruptedException exception) {
                cancelHandshake(handshake, handshakeContext.get());
                throw exception;
            }
            if (!isCurrent(generation)) {
                session.outbound().close();
                return;
            }
            WebSocketWriter writer = session.outbound();
            outbound = writer;
            latestServerSequence = -1L;
            sentInput = null;
            session.inbound().subscribe(new InboundSubscriber(generation));
            updateStatus(DesktopApiStatus.online());
            if (reconnectSessionId == null) {
                ViewportSize viewport = requestedViewport.get();
                sendCommand("START_GAME", Map.of(
                        "characterId", characterId,
                        "viewportWidth", viewport.width(),
                        "viewportHeight", viewport.height()));
                if (requestedPaused.get()) {
                    sendPauseRequest(true);
                }
            } else {
                sendViewportChanged(requestedViewport.get());
                sendPauseRequest(requestedPaused.get());
            }
            sendInputIfChanged(requestedInput.get());
        } catch (ExecutionException exception) {
            connectionFailed(
                    generation,
                    new IOException("WebSocket handshake failed", exception.getCause()),
                    reconnectSessionId,
                    deadlineNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            connectionFailed(generation, exception, reconnectSessionId, deadlineNanos);
        } catch (Exception exception) {
            connectionFailed(generation, exception, reconnectSessionId, deadlineNanos);
        }
    }

    private SocketTicketResponse requestSocketTicket(String accessToken)
            throws IOException, InterruptedException {
        RequestHeaders headers = RequestHeaders.builder(HttpMethod.POST, SOCKET_TICKETS_PATH)
                .add(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken)
                .build();
        AggregatedHttpResponse response;
        try {
            response = restClient.execute(HttpRequest.of(headers)).aggregate().get();
        } catch (ExecutionException exception) {
            throw new IOException("socket ticket request failed", exception.getCause());
        }
        int statusCode = response.status().code();
        if (statusCode != 200 && statusCode != 201) {
            throw new GameApiException(statusCode);
        }
        return objectMapper.readValue(response.contentUtf8(), SocketTicketResponse.class);
    }

    private void executeCommand(String type, Object payload) {
        if (!closed.get()) {
            worker.execute(() -> sendCommand(type, payload));
        }
    }

    private void sendViewportIfCurrent(long revision) {
        if (revision == viewportRevision.get() && outbound != null) {
            sendViewportChanged(requestedViewport.get());
        }
    }

    private void sendViewportChanged(ViewportSize viewport) {
        sendCommand("VIEWPORT_CHANGED", Map.of(
                "viewportWidth", viewport.width(),
                "viewportHeight", viewport.height()));
    }

    private void sendPauseRequest(boolean paused) {
        sendCommand("SET_GAME_PAUSED", Map.of("paused", paused));
    }

    private void sendInputIfChanged(InputState input) {
        if (outbound == null || input.equals(sentInput)) {
            return;
        }
        sentInput = input;
        sendCommand("INPUT_CHANGED", new InputPayload(
                input.up(),
                input.down(),
                input.left(),
                input.right(),
                inputSequence.incrementAndGet()));
    }

    private void sendCommand(String type, Object payload) {
        WebSocketWriter writer = outbound;
        if (writer == null) {
            updateStatus(DesktopApiStatus.offline("게임 서버와 연결되어 있지 않습니다."));
            return;
        }
        try {
            String message = objectMapper.writeValueAsString(new ClientMessage(
                    type,
                    "desktop-" + requestIds.incrementAndGet(),
                    payload));
            if (!writer.tryWrite(message)) {
                connectionClosed(
                        connectionGeneration.get(),
                        new IOException("WebSocket output is closed"));
            }
        } catch (RuntimeException exception) {
            updateStatus(DesktopApiStatus.error("게임 명령을 만들지 못했습니다."));
        }
    }

    private void handleServerText(long generation, String text) {
        if (!isCurrent(generation)) {
            return;
        }
        try {
            ServerMessage message = objectMapper.readValue(text, ServerMessage.class);
            if (message.sequence() <= latestServerSequence) {
                return;
            }
            latestServerSequence = message.sequence();
            switch (requireText(message.type(), "type")) {
                case "GAME_SNAPSHOT", "GAME_RESULT" -> applySnapshot(message.payload());
                case "ERROR" -> applyServerError(message.payload());
                default -> {
                    // Forward-compatible: a newer server message does not terminate the session.
                }
            }
        } catch (IOException | RuntimeException exception) {
            updateStatus(DesktopApiStatus.error("게임 서버 응답을 읽지 못했습니다."));
        }
    }

    private void applySnapshot(JsonNode payload) throws IOException {
        if (payload == null || payload.isNull()) {
            throw new IOException("snapshot payload is missing");
        }
        JsonNode snapshotNode = payload.get("snapshot");
        if (snapshotNode == null || snapshotNode.isNull()) {
            snapshotNode = payload;
        }
        JsonNode sessionIdNode = payload.get("sessionId");
        if (sessionIdNode != null && sessionIdNode.isString()
                && !sessionIdNode.stringValue().isBlank()) {
            currentSessionId = sessionIdNode.stringValue();
        }
        GameSnapshot snapshot = objectMapper.readValue(snapshotNode.toString(), GameSnapshot.class);
        GameSnapshot previous = latestSnapshot.getAndSet(snapshot);
        reconnectDeadlineNanos = 0L;
        updateStatus(DesktopApiStatus.online());
        if (isRewardChoice(previous.phase()) && snapshot.phase() == GamePhase.RUNNING) {
            sentInput = null;
            sendInputIfChanged(requestedInput.get());
        }
    }

    private void applyServerError(JsonNode payload) {
        String message = "게임 서버가 요청을 처리하지 못했습니다.";
        if (payload != null && payload.get("message") != null) {
            String detail = payload.get("message").asString();
            if (!detail.isBlank()) {
                message = detail;
            }
        }
        updateStatus(DesktopApiStatus.error(message));
    }

    private void connectionFailed(
            long generation,
            Exception exception,
            String reconnectSessionId,
            long deadlineNanos
    ) {
        if (!isCurrent(generation)) {
            return;
        }
        outbound = null;
        log.warn(
                "Game WebSocket {} failed: {}",
                reconnectSessionId == null ? "initial connection" : "reconnection",
                exception.getClass().getSimpleName());
        if (exception instanceof GameApiException apiException) {
            if (apiException.statusCode() == 401) {
                updateStatus(DesktopApiStatus.authenticationExpired());
                return;
            }
            if (apiException.statusCode() == 403) {
                updateStatus(DesktopApiStatus.forbidden());
                return;
            }
        }
        if (reconnectSessionId != null && System.nanoTime() < deadlineNanos) {
            updateStatus(DesktopApiStatus.reconnecting());
            scheduleReconnect(generation, reconnectSessionId, deadlineNanos);
            return;
        }
        updateStatus(DesktopApiStatus.offline("네트워크 문제로 게임 서버에 연결하지 못했습니다."));
    }

    private void connectionClosed(long generation, Throwable cause) {
        if (!isCurrent(generation)) {
            return;
        }
        outbound = null;
        String reconnectSessionId = currentSessionId;
        boolean reconnectable = reconnectSessionId != null && isReconnectable(latestSnapshot.get());
        log.warn(
                "Game WebSocket closed unexpectedly: {}",
                cause == null ? "peer completed the stream" : cause.getClass().getSimpleName());
        if (reconnectable) {
            long nextGeneration = generation + 1L;
            if (!connectionGeneration.compareAndSet(generation, nextGeneration)) {
                return;
            }
            long now = System.nanoTime();
            long deadline = reconnectDeadlineNanos > now
                    ? reconnectDeadlineNanos
                    : now + RECONNECT_WINDOW.toNanos();
            reconnectDeadlineNanos = deadline;
            sentInput = null;
            updateStatus(DesktopApiStatus.reconnecting());
            scheduleReconnect(nextGeneration, reconnectSessionId, deadline);
            return;
        }
        String message = cause == null
                ? "게임 서버 연결이 종료되었습니다."
                : "네트워크 문제로 게임 서버 연결이 끊어졌습니다.";
        updateStatus(DesktopApiStatus.offline(message));
    }

    private void scheduleReconnect(long generation, String sessionId, long deadlineNanos) {
        worker.schedule(
                () -> {
                    if (!isCurrent(generation)) {
                        return;
                    }
                    if (System.nanoTime() >= deadlineNanos) {
                        reconnectDeadlineNanos = 0L;
                        updateStatus(DesktopApiStatus.offline(
                                "30초 동안 다시 연결하지 못해 게임 연결을 종료했습니다."));
                        return;
                    }
                    String accessToken = currentAccessToken;
                    String characterId = currentCharacterId;
                    if (accessToken == null || characterId == null) {
                        updateStatus(DesktopApiStatus.authenticationExpired());
                        return;
                    }
                    connect(generation, accessToken, characterId, sessionId, deadlineNanos);
                },
                RECONNECT_DELAY.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static boolean isReconnectable(GameSnapshot snapshot) {
        return snapshot.phase() == GamePhase.RUNNING
                || snapshot.phase() == GamePhase.LEVEL_UP
                || snapshot.phase() == GamePhase.CHEST_REWARD;
    }

    private static boolean isRewardChoice(GamePhase phase) {
        return phase == GamePhase.LEVEL_UP || phase == GamePhase.CHEST_REWARD;
    }

    private static long resolveHandshakeTimeoutMillis(ClientFactory clientFactory) {
        Object timeout = clientFactory.options()
                .channelOptions()
                .get(ChannelOption.CONNECT_TIMEOUT_MILLIS);
        if (timeout instanceof Number number && number.longValue() > 0L) {
            return number.longValue();
        }
        throw new IllegalArgumentException("clientFactory must define a positive connect timeout");
    }

    private static void cancelHandshake(
            CompletableFuture<WebSocketSession> handshake,
            ClientRequestContext context
    ) {
        boolean cancelled = handshake.cancel(true);
        if (context != null) {
            context.cancel();
        }
        if (!cancelled) {
            handshake.thenAccept(session -> session.outbound().close());
        }
    }

    private boolean isCurrent(long generation) {
        return !closed.get() && connectionGeneration.get() == generation;
    }

    private void closeCurrentSocket() {
        WebSocketWriter writer = outbound;
        outbound = null;
        if (writer != null) {
            writer.close();
        }
    }

    private void updateStatus(DesktopApiStatus newStatus) {
        status.set(Objects.requireNonNull(newStatus, "newStatus"));
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        try {
            stateListener.run();
        } catch (RuntimeException ignored) {
            // A presentation callback must never stop the socket worker.
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static ViewportSize viewportSize(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0.0 || height <= 0.0) {
            throw new IllegalArgumentException("viewport dimensions must be finite and positive");
        }
        return new ViewportSize(
                clamp((int) Math.round(width), MIN_VIEWPORT_WIDTH, MAX_VIEWPORT_WIDTH),
                clamp((int) Math.round(height), MIN_VIEWPORT_HEIGHT, MAX_VIEWPORT_HEIGHT));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class InboundSubscriber implements Subscriber<WebSocketFrame> {
        private final long generation;

        private InboundSubscriber(long generation) {
            this.generation = generation;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(WebSocketFrame frame) {
            if (frame.type() == WebSocketFrameType.TEXT) {
                String text = frame.text();
                if (!closed.get()) {
                    worker.execute(() -> handleServerText(generation, text));
                }
            }
        }

        @Override
        public void onError(Throwable cause) {
            if (!closed.get()) {
                worker.execute(() -> connectionClosed(generation, cause));
            }
        }

        @Override
        public void onComplete() {
            if (!closed.get()) {
                worker.execute(() -> connectionClosed(generation, null));
            }
        }
    }

    private record ClientMessage(String type, String requestId, Object payload) {
    }

    private record InputPayload(
            boolean up,
            boolean down,
            boolean left,
            boolean right,
            long commandSequence) {
    }

    private record ViewportSize(int width, int height) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ServerMessage(String type, long sequence, JsonNode payload) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SocketTicketResponse(String ticket, String webSocketUri, String expiresAt) {
    }

    private static final class GameApiException extends IOException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;

        private GameApiException(int statusCode) {
            super("game API returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
