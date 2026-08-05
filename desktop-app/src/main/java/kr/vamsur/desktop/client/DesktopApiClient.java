package kr.vamsur.desktop.client;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.MediaType;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kr.vamsur.desktop.gameplay.DesktopApiStatus;
import kr.vamsur.desktop.gameplay.GameSnapshot;
import kr.vamsur.desktop.gameplay.InputState;
import kr.vamsur.desktop.gameplay.UpgradeType;
import tools.jackson.databind.ObjectMapper;

/**
 * Non-blocking client for the game-core API used by the JavaFX desktop.
 *
 * <p>All network calls run on one background thread. The JavaFX thread only reads the most
 * recent immutable snapshot, so a slow or stopped backend cannot freeze the window.</p>
 */
public final class DesktopApiClient implements AutoCloseable {
    private static final String STATUS_PATH = "/api/v1/game/status";
    private static final String START_PATH = "/api/v1/game/start";
    private static final String INPUT_PATH = "/api/v1/game/input";
    private static final String TICK_PATH = "/api/v1/game/tick";
    private static final String UPGRADE_PATH = "/api/v1/game/upgrade";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService requests;
    private final AtomicReference<GameSnapshot> latestSnapshot = new AtomicReference<>(GameSnapshot.lobby());
    private final AtomicReference<DesktopApiStatus> status =
            new AtomicReference<>(DesktopApiStatus.connecting());
    private final AtomicReference<InputState> requestedInput = new AtomicReference<>(InputState.idle());
    private final AtomicBoolean tickInFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile InputState sentInput;
    private volatile Runnable stateListener = () -> { };

    public DesktopApiClient(
            WebClient webClient,
            ObjectMapper objectMapper) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        requests = Executors.newSingleThreadExecutor(task -> Thread.ofPlatform()
                .daemon()
                .name("desktop-api-client")
                .unstarted(task));
        refresh();
    }

    public GameSnapshot snapshot() {
        return latestSnapshot.get();
    }

    public DesktopApiStatus status() {
        return status.get();
    }

    /**
     * Registers the single JavaFX view listener. The callback runs on the calling network thread.
     */
    public void setStateListener(Runnable listener) {
        stateListener = Objects.requireNonNull(listener, "listener");
        notifyStateChanged();
    }

    public void refresh() {
        submit(() -> request("GET", STATUS_PATH, null));
    }

    public void startNewGame() {
        updateStatus(DesktopApiStatus.connecting());
        sentInput = null;
        requestedInput.set(InputState.idle());
        submit(() -> request("POST", START_PATH, null));
    }

    public void setInput(InputState input) {
        requestedInput.set(Objects.requireNonNull(input, "input"));
    }

    /**
     * Requests one simulation step. A second tick is discarded until the current one completes.
     */
    public boolean tick(double deltaSeconds, int steps) {
        if (!(deltaSeconds > 0.0) || !Double.isFinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be finite and positive");
        }
        if (steps < 1 || steps > 15) {
            throw new IllegalArgumentException("steps must be between 1 and 15");
        }
        if (closed.get() || !tickInFlight.compareAndSet(false, true)) {
            return false;
        }

        execute(() -> {
            try {
                InputState input = requestedInput.get();
                if (!input.equals(sentInput)) {
                    apply(request("PUT", INPUT_PATH, input));
                    sentInput = input;
                }
                apply(request("POST", TICK_PATH, new TickRequest(deltaSeconds, steps)));
            } finally {
                tickInFlight.set(false);
            }
        });
        return true;
    }

    public void chooseUpgrade(UpgradeType upgradeType) {
        UpgradeType chosen = Objects.requireNonNull(upgradeType, "upgradeType");
        submit(() -> request("POST", UPGRADE_PATH, new UpgradeRequest(chosen.name())));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            requests.shutdownNow();
        }
    }

    private void submit(RequestOperation operation) {
        if (closed.get()) {
            return;
        }
        execute(() -> apply(operation.execute()));
    }

    private void execute(ThrowingRunnable operation) {
        if (closed.get()) {
            return;
        }
        requests.execute(() -> {
            try {
                operation.run();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException exception) {
                updateStatus(DesktopApiStatus.offline(connectionMessage(exception)));
            }
        });
    }

    private GameSnapshot request(String method, String path, Object body) throws IOException, InterruptedException {
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        HttpRequest request = body == null
                ? HttpRequest.of(httpMethod, path)
                : HttpRequest.of(httpMethod, path, MediaType.JSON_UTF_8, objectMapper.writeValueAsString(body));

        AggregatedHttpResponse response;
        try {
            response = webClient.execute(request).aggregate().get();
        } catch (ExecutionException exception) {
            throw new IOException("game-core request failed", exception.getCause());
        }
        if (response.status().code() != 200) {
            throw new IOException("game-core returned HTTP " + response.status().code());
        }
        return objectMapper.readValue(response.contentUtf8(), GameSnapshot.class);
    }

    private void apply(GameSnapshot snapshot) {
        latestSnapshot.set(Objects.requireNonNull(snapshot, "snapshot"));
        updateStatus(DesktopApiStatus.online());
    }

    private void updateStatus(DesktopApiStatus newStatus) {
        status.set(Objects.requireNonNull(newStatus, "newStatus"));
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        try {
            stateListener.run();
        } catch (RuntimeException ignored) {
            // A display callback must never stop the API worker.
        }
    }

    private static String connectionMessage(Exception exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.getClass().getSimpleName();
        }
        return "game-core 연결 실패: " + detail;
    }

    @FunctionalInterface
    private interface RequestOperation {
        GameSnapshot execute() throws IOException, InterruptedException;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws IOException, InterruptedException;
    }

    private record TickRequest(double deltaSeconds, int steps) {
    }

    private record UpgradeRequest(String upgradeType) {
    }
}
