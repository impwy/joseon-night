package kr.joseonnight.adapter.desktopapi;

import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.websocket.WebSocket;
import com.linecorp.armeria.common.websocket.WebSocketFrame;
import com.linecorp.armeria.common.websocket.WebSocketFrameType;
import com.linecorp.armeria.common.websocket.WebSocketWriter;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.websocket.WebSocketService;
import io.netty.util.AttributeKey;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import kr.joseonnight.application.gameplay.provided.GameSessionHandle;
import kr.joseonnight.application.gameplay.provided.GameSessionManager;
import kr.joseonnight.application.gameplay.provided.GameSessionSubscription;
import kr.joseonnight.application.gameplay.provided.GameSessionUpdate;
import kr.joseonnight.application.member.provided.GameSocketTicketConsumer;
import kr.joseonnight.domain.gameplay.CharacterType;
import kr.joseonnight.domain.gameplay.GamePhase;
import kr.joseonnight.domain.gameplay.InputState;
import kr.joseonnight.support.stereotype.DesktopApiAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Authenticated Armeria WebSocket adapter for server-authoritative game commands.
 */
@DesktopApiAdapter
public final class GameWebSocketApi {

    public static final String PATH = "/api/v1/game/ws";

    private static final String TICKET_PREFIX = "Ticket ";
    private static final AttributeKey<String> MEMBER_ID =
            AttributeKey.valueOf(GameWebSocketApi.class, "memberId");

    private final GameSessionManager gameSessions;
    private final GameSocketTicketConsumer ticketConsumer;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public GameWebSocketApi(
            GameSessionManager gameSessions,
            GameSocketTicketConsumer ticketConsumer,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.gameSessions = Objects.requireNonNull(gameSessions, "gameSessions");
        this.ticketConsumer = Objects.requireNonNull(ticketConsumer, "ticketConsumer");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public HttpService service() {
        WebSocketService webSocketService = WebSocketService.builder(this::openConnection)
                // Native desktop clients do not send a browser Origin header. Authentication is
                // enforced by the one-time ticket before the protocol upgrade is delegated.
                .allowedOrigins("*")
                .build();
        return webSocketService.decorate((delegate, context, request) -> {
            String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith(TICKET_PREFIX)) {
                return HttpResponse.of(HttpStatus.UNAUTHORIZED);
            }
            String ticket = authorization.substring(TICKET_PREFIX.length());
            if (ticket.isBlank()) {
                return HttpResponse.of(HttpStatus.UNAUTHORIZED);
            }
            return ticketConsumer.consume(ticket)
                    .map(memberId -> {
                        context.setAttr(MEMBER_ID, memberId.toString());
                        try {
                            return delegate.serve(context, request);
                        } catch (Exception exception) {
                            return HttpResponse.ofFailure(exception);
                        }
                    })
                    .orElseGet(() -> HttpResponse.of(HttpStatus.UNAUTHORIZED));
        });
    }

    private WebSocket openConnection(ServiceRequestContext context, WebSocket inbound) {
        String memberId = Objects.requireNonNull(context.attr(MEMBER_ID), "authenticated memberId");
        WebSocketWriter outbound = WebSocket.streaming();
        Connection connection = new Connection(memberId, outbound);

        String reconnectSessionId = context.queryParam("sessionId");
        if (reconnectSessionId != null && !reconnectSessionId.isBlank()) {
            connection.reconnect(reconnectSessionId);
        }

        inbound.peek(connection::acceptFrame)
                .subscribe(context.eventLoop());
        inbound.whenComplete().whenComplete((unused, cause) -> connection.close());
        return outbound;
    }

    private final class Connection {
        private final String memberId;
        private final WebSocketWriter outbound;
        private final AtomicLong serverSequence = new AtomicLong();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile GameSessionHandle handle;
        private volatile GameSessionSubscription subscription;

        private Connection(String memberId, WebSocketWriter outbound) {
            this.memberId = memberId;
            this.outbound = outbound;
        }

        private void acceptFrame(WebSocketFrame frame) {
            if (frame.type() == WebSocketFrameType.TEXT) {
                handleText(frame.text());
            }
        }

        private void handleText(String text) {
            String requestId = null;
            try {
                ClientMessage message = objectMapper.readValue(text, ClientMessage.class);
                if (message == null) {
                    throw new IllegalArgumentException("message must not be null");
                }
                requestId = requireText(message.requestId(), "requestId");
                String type = requireText(message.type(), "type");
                JsonNode payload = message.payload();
                if (payload == null) {
                    throw new IllegalArgumentException("payload must not be null");
                }
                switch (type) {
                    case "START_GAME" -> startGame(requestId, payload);
                    case "INPUT_CHANGED" -> changeInput(requestId, payload);
                    case "CHOOSE_LEVEL_UP" -> chooseLevelUp(requestId, payload);
                    case "CHOOSE_CHEST_REWARD" -> chooseChestReward(requestId, payload);
                    default -> sendError("UNKNOWN_MESSAGE_TYPE", "지원하지 않는 게임 명령입니다.", requestId);
                }
            } catch (JacksonException exception) {
                sendError("INVALID_MESSAGE", "게임 명령 형식을 읽을 수 없습니다.", requestId);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                sendError("INVALID_COMMAND", exception.getMessage(), requestId);
            }
        }

        private void startGame(String requestId, JsonNode payload) {
            StartGamePayload command = readAndValidate(payload, StartGamePayload.class);
            CharacterType character = CharacterType.fromId(command.characterId());
            closeCurrentSession();
            attach(gameSessions.startNewGame(memberId, character));
            sendSnapshot(requestId, false, handle.snapshot());
        }

        private void reconnect(String sessionId) {
            try {
                attach(gameSessions.reconnect(memberId, sessionId));
                boolean result = isResult(handle.snapshot().phase());
                sendSnapshot(null, result, handle.snapshot());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                sendError("SESSION_UNAVAILABLE", "이어 할 게임을 찾을 수 없습니다.", null);
            }
        }

        private void changeInput(String requestId, JsonNode payload) {
            InputChangedPayload command = readAndValidate(payload, InputChangedPayload.class);
            GameSessionHandle current = requireSession();
            boolean accepted = gameSessions.setInput(
                    memberId,
                    current.sessionId(),
                    current.connectionId(),
                    command.commandSequence(),
                    new InputState(
                            command.up(),
                            command.down(),
                            command.left(),
                            command.right()));
            if (!accepted) {
                sendError("STALE_INPUT", "이미 처리한 입력 순서입니다.", requestId);
            }
        }

        private void chooseLevelUp(String requestId, JsonNode payload) {
            RewardChoicePayload command = readAndValidate(payload, RewardChoicePayload.class);
            GameSessionHandle current = requireSession();
            gameSessions.chooseLevelUp(
                    memberId,
                    current.sessionId(),
                    current.connectionId(),
                    command.optionId());
            sendSnapshot(requestId, false, gameSessions.snapshot(memberId, current.sessionId()));
        }

        private void chooseChestReward(String requestId, JsonNode payload) {
            RewardChoicePayload command = readAndValidate(payload, RewardChoicePayload.class);
            GameSessionHandle current = requireSession();
            gameSessions.chooseChestReward(
                    memberId,
                    current.sessionId(),
                    current.connectionId(),
                    command.optionId());
            sendSnapshot(requestId, false, gameSessions.snapshot(memberId, current.sessionId()));
        }

        private void attach(GameSessionHandle newHandle) {
            handle = newHandle;
            subscription = gameSessions.subscribe(
                    memberId,
                    newHandle.sessionId(),
                    newHandle.connectionId(),
                    this::sendUpdate);
        }

        private void sendUpdate(GameSessionUpdate update) {
            sendSnapshot(null, update.result(), update.snapshot());
        }

        private void sendSnapshot(
                String requestId,
                boolean result,
                Object snapshot
        ) {
            GameSessionHandle current = handle;
            Map<String, Object> payload = requestId == null
                    ? Map.of(
                            "sessionId", current.sessionId(),
                            "snapshot", snapshot)
                    : Map.of(
                            "sessionId", current.sessionId(),
                            "requestId", requestId,
                            "snapshot", snapshot);
            send(result ? "GAME_RESULT" : "GAME_SNAPSHOT", payload);
        }

        private void sendError(String code, String message, String requestId) {
            String safeMessage = message == null || message.isBlank()
                    ? "게임 명령을 처리하지 못했습니다."
                    : message;
            Map<String, Object> payload = requestId == null
                    ? Map.of("code", code, "message", safeMessage)
                    : Map.of("code", code, "message", safeMessage, "requestId", requestId);
            send("ERROR", payload);
        }

        private void send(String type, Object payload) {
            if (closed.get()) {
                return;
            }
            try {
                String json = objectMapper.writeValueAsString(new ServerMessage(
                        type,
                        serverSequence.incrementAndGet(),
                        payload));
                if (!outbound.tryWrite(json)) {
                    close();
                }
            } catch (JacksonException exception) {
                outbound.close(exception);
                close();
            }
        }

        private GameSessionHandle requireSession() {
            GameSessionHandle current = handle;
            if (current == null) {
                throw new IllegalStateException("게임을 먼저 시작해 주세요.");
            }
            return current;
        }

        private void closeCurrentSession() {
            GameSessionSubscription currentSubscription = subscription;
            subscription = null;
            if (currentSubscription != null) {
                currentSubscription.close();
            }
            GameSessionHandle current = handle;
            handle = null;
            if (current != null) {
                gameSessions.disconnect(
                        memberId,
                        current.sessionId(),
                        current.connectionId());
            }
        }

        private void close() {
            if (closed.compareAndSet(false, true)) {
                closeCurrentSession();
                outbound.close();
            }
        }
    }

    private <T> T readAndValidate(JsonNode payload, Class<T> payloadType) {
        final T command;
        try {
            command = objectMapper.readValue(payload.toString(), payloadType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("게임 명령 값의 형식이 올바르지 않습니다.", exception);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(command);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.stream()
                    .min(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .orElseThrow();
            throw new IllegalArgumentException(
                    first.getPropertyPath() + " " + first.getMessage());
        }
        return command;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static boolean isResult(GamePhase phase) {
        return phase == GamePhase.VICTORY
                || phase == GamePhase.DEFEAT
                || phase == GamePhase.ABANDONED;
    }

    private record ClientMessage(String type, String requestId, JsonNode payload) {
    }

    private record StartGamePayload(
            @NotBlank @Size(max = 64) String characterId
    ) {
    }

    private record InputChangedPayload(
            @NotNull Boolean up,
            @NotNull Boolean down,
            @NotNull Boolean left,
            @NotNull Boolean right,
            @NotNull @PositiveOrZero Long commandSequence
    ) {
    }

    private record RewardChoicePayload(
            @NotBlank @Size(max = 128) String optionId
    ) {
    }

    private record ServerMessage(String type, long sequence, Object payload) {
    }
}
