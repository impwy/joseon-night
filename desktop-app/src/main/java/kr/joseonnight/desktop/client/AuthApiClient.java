package kr.joseonnight.desktop.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.RequestHeadersBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kr.joseonnight.desktop.authentication.AuthPhase;
import kr.joseonnight.desktop.authentication.AuthSession;
import kr.joseonnight.desktop.authentication.AuthState;
import tools.jackson.databind.ObjectMapper;

/**
 * Browser-based desktop login client for the Tomcat REST API.
 *
 * <p>All blocking aggregation and polling is confined to one daemon worker; callbacks never run on
 * the JavaFX application thread unless the view explicitly dispatches them there.</p>
 */
public final class AuthApiClient implements AutoCloseable {
    static final String ATTEMPTS_PATH = "/api/v1/auth/desktop/attempts";
    static final String REGISTRATIONS_PATH = "/api/v1/auth/desktop/registrations";
    static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration pollInterval;
    private final Duration maximumPollDuration;
    private final ScheduledExecutorService worker;
    private final AtomicReference<AuthState> state = new AtomicReference<>(AuthState.signedOut());
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile Runnable stateListener = () -> { };
    private volatile LoginAttempt currentAttempt;
    private volatile ScheduledFuture<?> scheduledPoll;

    public AuthApiClient(
            WebClient webClient,
            ObjectMapper objectMapper,
            Duration pollInterval,
            Duration maximumPollDuration) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.pollInterval = requirePositive(pollInterval, "pollInterval");
        this.maximumPollDuration = requirePositive(maximumPollDuration, "maximumPollDuration");
        worker = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform()
                .daemon()
                .name("desktop-auth-client")
                .unstarted(task));
    }

    public AuthState state() {
        return state.get();
    }

    public void setStateListener(Runnable listener) {
        stateListener = Objects.requireNonNull(listener, "listener");
        notifyStateChanged();
    }

    public void beginLogin() {
        if (closed.get()) {
            return;
        }
        cancelPolling();
        currentAttempt = null;
        updateState(new AuthState(
                AuthPhase.STARTING_ATTEMPT,
                "로그인 페이지를 준비하고 있습니다…",
                null,
                null,
                null));
        worker.execute(this::createAttempt);
    }

    public void registerNickname(String nickname) {
        String normalized = Objects.requireNonNull(nickname, "nickname").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("nickname must not be blank");
        }
        AuthState current = state.get();
        if (current.phase() != AuthPhase.REGISTRATION_REQUIRED
                || current.registrationTicket() == null) {
            throw new IllegalStateException("nickname registration is not pending");
        }
        updateState(new AuthState(
                AuthPhase.STARTING_ATTEMPT,
                "닉네임을 등록하고 있습니다…",
                null,
                current.registrationTicket(),
                null));
        worker.execute(() -> completeRegistration(current.registrationTicket(), normalized));
    }

    public void logout() {
        AuthSession authenticated = state.get().session();
        cancelPolling();
        currentAttempt = null;
        updateState(AuthState.signedOut());
        if (authenticated != null && !closed.get()) {
            worker.execute(() -> revokeAccessToken(authenticated.accessToken()));
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cancelPolling();
            currentAttempt = null;
            state.set(AuthState.signedOut());
            worker.shutdownNow();
        }
    }

    private void createAttempt() {
        try {
            AggregatedHttpResponse response = request(HttpMethod.POST, ATTEMPTS_PATH, null, null);
            requireStatus(response, 200, 201);
            AttemptResponse body = objectMapper.readValue(response.contentUtf8(), AttemptResponse.class);
            Instant now = Instant.now();
            Instant serverExpiry = parseInstant(body.expiresAt());
            Instant localDeadline = now.plus(maximumPollDuration);
            Instant deadline = serverExpiry != null && serverExpiry.isBefore(localDeadline)
                    ? serverExpiry
                    : localDeadline;
            LoginAttempt attempt = new LoginAttempt(
                    requireText(body.attemptId(), "attemptId"),
                    requireText(body.pollToken(), "pollToken"),
                    URI.create(requireText(body.authorizationUri(), "authorizationUri")),
                    deadline);
            currentAttempt = attempt;
            updateState(new AuthState(
                    AuthPhase.WAITING_FOR_BROWSER,
                    "Google 로그인을 마치면 자동으로 계속됩니다.",
                    attempt.authorizationUri(),
                    null,
                    null));
            scheduleNextPoll(attempt);
        } catch (Exception exception) {
            handleFailure(exception);
        }
    }

    private void scheduleNextPoll(LoginAttempt attempt) {
        if (closed.get() || currentAttempt != attempt) {
            return;
        }
        if (!Instant.now().isBefore(attempt.deadline())) {
            expireAttempt();
            return;
        }
        scheduledPoll = worker.schedule(
                () -> exchangeAttempt(attempt),
                pollInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void exchangeAttempt(LoginAttempt attempt) {
        if (closed.get() || currentAttempt != attempt) {
            return;
        }
        if (!Instant.now().isBefore(attempt.deadline())) {
            expireAttempt();
            return;
        }
        try {
            String path = ATTEMPTS_PATH + "/" + encodePath(attempt.attemptId()) + "/exchange";
            AggregatedHttpResponse response = request(
                    HttpMethod.POST,
                    path,
                    new ExchangeRequest(attempt.pollToken()),
                    null);
            if (response.status().code() == 202) {
                scheduleNextPoll(attempt);
                return;
            }
            requireStatus(response, 200);
            ExchangeResponse body = objectMapper.readValue(response.contentUtf8(), ExchangeResponse.class);
            String exchangeStatus = requireText(body.status(), "status");
            switch (exchangeStatus) {
                case "PENDING" -> scheduleNextPoll(attempt);
                case "AUTHENTICATED" -> authenticate(body.accessToken(), body.expiresAt());
                case "REGISTRATION_REQUIRED" -> {
                    currentAttempt = null;
                    updateState(new AuthState(
                            AuthPhase.REGISTRATION_REQUIRED,
                            "게임에서 사용할 닉네임을 정해 주세요.",
                            null,
                            requireText(body.registrationTicket(), "registrationTicket"),
                            null));
                }
                default -> throw new IOException("unsupported authentication status: " + exchangeStatus);
            }
        } catch (Exception exception) {
            handleFailure(exception);
        }
    }

    private void completeRegistration(String registrationTicket, String nickname) {
        try {
            AggregatedHttpResponse response = request(
                    HttpMethod.POST,
                    REGISTRATIONS_PATH,
                    new RegistrationRequest(registrationTicket, nickname),
                    null);
            requireStatus(response, 200, 201);
            RegistrationResponse body = objectMapper.readValue(
                    response.contentUtf8(), RegistrationResponse.class);
            authenticate(body.accessToken(), body.expiresAt());
        } catch (Exception exception) {
            handleFailure(exception);
        }
    }

    private void revokeAccessToken(String accessToken) {
        try {
            AggregatedHttpResponse response = request(
                    HttpMethod.POST,
                    LOGOUT_PATH,
                    null,
                    accessToken
            );
            requireStatus(response, 200, 204);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // Local logout is immediate; a failed best-effort revocation expires with the short JWT.
        }
    }

    private void authenticate(String accessToken, String expiresAt) {
        currentAttempt = null;
        cancelPolling();
        AuthSession session = new AuthSession(requireText(accessToken, "accessToken"), parseInstant(expiresAt));
        updateState(new AuthState(
                AuthPhase.AUTHENTICATED,
                "로그인되었습니다.",
                null,
                null,
                session));
    }

    private AggregatedHttpResponse request(
            HttpMethod method,
            String path,
            Object body,
            String bearerToken) throws IOException, InterruptedException {
        RequestHeadersBuilder headers = RequestHeaders.builder(method, path);
        HttpData content = HttpData.empty();
        if (body != null) {
            headers.contentType(MediaType.JSON_UTF_8);
            content = HttpData.ofUtf8(objectMapper.writeValueAsString(body));
        }
        if (bearerToken != null) {
            headers.add(HttpHeaderNames.AUTHORIZATION, "Bearer " + bearerToken);
        }
        try {
            return webClient.execute(HttpRequest.of(headers.build(), content)).aggregate().get();
        } catch (ExecutionException exception) {
            throw new IOException("authentication API request failed", exception.getCause());
        }
    }

    private void handleFailure(Exception exception) {
        currentAttempt = null;
        cancelPolling();
        AuthPhase phase;
        String message;
        if (exception instanceof RestStatusException statusException) {
            phase = switch (statusException.statusCode()) {
                case 401 -> AuthPhase.EXPIRED;
                case 403 -> AuthPhase.FORBIDDEN;
                default -> AuthPhase.FAILED;
            };
            message = switch (phase) {
                case EXPIRED -> "로그인 시간이 만료되었습니다. 다시 시도해 주세요.";
                case FORBIDDEN -> "이 계정으로는 게임에 접속할 수 없습니다.";
                default -> "로그인 서버가 요청을 처리하지 못했습니다.";
            };
        } else if (exception instanceof IOException) {
            phase = AuthPhase.OFFLINE;
            message = "로그인 서버에 연결할 수 없습니다.";
        } else {
            phase = AuthPhase.FAILED;
            message = "로그인을 완료하지 못했습니다.";
        }
        updateState(new AuthState(phase, message, null, null, null));
    }

    private void expireAttempt() {
        currentAttempt = null;
        cancelPolling();
        updateState(new AuthState(
                AuthPhase.EXPIRED,
                "로그인 시간이 만료되었습니다. 다시 시도해 주세요.",
                null,
                null,
                null));
    }

    private void updateState(AuthState newState) {
        state.set(Objects.requireNonNull(newState, "newState"));
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        try {
            stateListener.run();
        } catch (RuntimeException ignored) {
            // A presentation callback must never stop authentication polling.
        }
    }

    private void cancelPolling() {
        ScheduledFuture<?> poll = scheduledPoll;
        scheduledPoll = null;
        if (poll != null) {
            poll.cancel(false);
        }
    }

    private static void requireStatus(AggregatedHttpResponse response, int... acceptedStatuses)
            throws RestStatusException {
        int actual = response.status().code();
        for (int accepted : acceptedStatuses) {
            if (actual == accepted) {
                return;
            }
        }
        throw new RestStatusException(actual);
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record LoginAttempt(
            String attemptId,
            String pollToken,
            URI authorizationUri,
            Instant deadline) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AttemptResponse(
            String attemptId,
            String pollToken,
            String authorizationUri,
            String expiresAt) {
    }

    private record ExchangeRequest(String pollToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExchangeResponse(
            String status,
            String accessToken,
            String expiresAt,
            String registrationTicket) {
    }

    private record RegistrationRequest(String registrationTicket, String nickname) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RegistrationResponse(String accessToken, String expiresAt) {
    }

    private static final class RestStatusException extends IOException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;

        private RestStatusException(int statusCode) {
            super("authentication API returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
