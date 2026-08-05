package kr.joseonnight.adapter.desktopapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linecorp.armeria.server.Server;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.joseonnight.application.gameplay.GameService;
import kr.joseonnight.application.member.provided.GameSocketTicketConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GameWebSocketApiTest {

    private final OneTimeTicketStore ticketStore = new OneTimeTicketStore();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private GameService gameService;
    private Server server;
    private URI uri;

    @BeforeEach
    void startServer() {
        gameService = new GameService();
        GameWebSocketApi api = new GameWebSocketApi(gameService, ticketStore, objectMapper, validator);
        server = Server.builder()
                .http(0)
                .service(GameWebSocketApi.PATH, api.service())
                .build();
        server.start().join();
        uri = URI.create("ws://127.0.0.1:" + server.activeLocalPort() + GameWebSocketApi.PATH);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop().join();
        }
        if (gameService != null) {
            gameService.close();
        }
    }

    @Test
    void rejectsHandshakeWithoutAOneTimeTicket() {
        HttpClient client = HttpClient.newHttpClient();

        assertThrows(CompletionException.class,
                () -> client.newWebSocketBuilder().buildAsync(uri, new TextListener()).join());
    }

    @Test
    void consumesTicketAndReturnsSnapshotForStartGameCommand() throws Exception {
        TextListener listener = new TextListener();
        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("Authorization", "Ticket valid-ticket")
                .buildAsync(uri, listener)
                .join();

        socket.sendText("""
                {"type":"START_GAME","requestId":"request-1",\
                 "payload":{"characterId":"gale-shaman"}}
                """, true).join();
        String text = listener.messages.poll(5, TimeUnit.SECONDS);

        assertNotNull(text);
        JsonNode response = objectMapper.readTree(text);
        assertEquals("GAME_SNAPSHOT", response.get("type").stringValue());
        assertEquals(1L, response.get("sequence").longValue());
        assertEquals("request-1",
                response.get("payload").get("requestId").stringValue());
        assertEquals("GALE_SHAMAN",
                response.get("payload").get("snapshot").get("character").stringValue());
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void returnsInvalidCommandForEveryInvalidTypedPayload() throws Exception {
        TextListener listener = new TextListener();
        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("Authorization", "Ticket valid-ticket")
                .buildAsync(uri, listener)
                .join();

        socket.sendText("""
                {"type":"START_GAME","requestId":"invalid-start",\
                 "payload":{"characterId":" "}}
                """, true).join();
        socket.sendText("""
                {"type":"INPUT_CHANGED","requestId":"invalid-input",\
                 "payload":{"up":true,"down":false,"left":false,"commandSequence":0}}
                """, true).join();
        socket.sendText("""
                {"type":"CHOOSE_LEVEL_UP","requestId":"invalid-level",\
                 "payload":{"optionId":""}}
                """, true).join();
        socket.sendText("""
                {"type":"CHOOSE_CHEST_REWARD","requestId":"invalid-chest",\
                 "payload":{"optionId":null}}
                """, true).join();

        for (String requestId : new String[] {
                "invalid-start", "invalid-input", "invalid-level", "invalid-chest"
        }) {
            JsonNode response = objectMapper.readTree(listener.messages.poll(5, TimeUnit.SECONDS));
            assertEquals("ERROR", response.get("type").stringValue());
            assertEquals("INVALID_COMMAND", response.get("payload").get("code").stringValue());
            assertEquals(requestId, response.get("payload").get("requestId").stringValue());
        }
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    private static final class OneTimeTicketStore implements GameSocketTicketConsumer {
        private final AtomicBoolean available = new AtomicBoolean(true);

        @Override
        public Optional<Long> consume(String ticket) {
            if ("valid-ticket".equals(ticket) && available.compareAndSet(true, false)) {
                return Optional.of(99L);
            }
            return Optional.empty();
        }
    }

    private static final class TextListener implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder fragments = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                messages.add(fragments.toString());
                fragments.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
        }
    }
}
