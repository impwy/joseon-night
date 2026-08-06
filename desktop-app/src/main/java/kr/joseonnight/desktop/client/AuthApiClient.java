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
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Browser-based desktop login client for the Tomcat REST API.
 *
 * <p>All blocking aggregation and polling is confined to one daemon worker; callbacks never run on
 * the JavaFX application thread unless the view explicitly dispatches them there.</p>
 */
@Slf4j
public final class AuthApiClient implements AutoCloseable {
    private static final String AUTH_CONFIGURATION_MISSING = "AUTH_CONFIGURATION_MISSING";
    private static final String LOGIN_UNAVAILABLE_MESSAGE =
            "현재 로그인할 수 없습니다. 잠시 후 다시 시도해 주세요.";
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
        AuthState startingState = new AuthState(
                AuthPhase.STARTING_ATTEMPT,
                "로그인 페이지를 준비하고 있습니다…",
                null,
                null,
                null);
        updateState(startingState);
        worker.execute(() -> createAttempt(startingState));
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

    private void createAttempt(AuthState expectedStartingState) {
        if (closed.get() || state.get() != expectedStartingState) {
            return;
        }
        long startedNanos = System.nanoTime();
        log.info("Authentication operation started: operation=create-attempt");
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
            AuthState waitingState = new AuthState(
                    AuthPhase.WAITING_FOR_BROWSER,
                    "브라우저에서 Google 로그인을 완료해 주세요.",
                    attempt.authorizationUri(),
                    null,
                    null);
            if (!replaceState(expectedStartingState, waitingState)) {
                if (currentAttempt == attempt) {
                    currentAttempt = null;
                }
                return;
            }
            log.info(
                    "Authentication operation completed: operation=create-attempt, elapsedMs={}",
                    elapsedMillis(startedNanos));
            scheduleNextPoll(attempt);
        } catch (Exception exception) {
            handleFailure("create-attempt", startedNanos, exception, expectedStartingState);
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
        long startedNanos = System.nanoTime();
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
                case "FAILED" -> failAttempt();
                case "EXPIRED", "EXCHANGED" -> expireAttempt();
                default -> throw new IOException("unsupported authentication status: " + exchangeStatus);
            }
        } catch (IOException | InterruptedException | RuntimeException exception) {
            handleFailure("exchange-attempt", startedNanos, exception);
        }
    }

    private void completeRegistration(String registrationTicket, String nickname) {
        long startedNanos = System.nanoTime();
        log.info("Authentication operation started: operation=register-nickname");
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
            log.info(
                    "Authentication operation completed: operation=register-nickname, elapsedMs={}",
                    elapsedMillis(startedNanos));
        } catch (Exception exception) {
            handleFailure("register-nickname", startedNanos, exception);
        }
    }

    private void revokeAccessToken(String accessToken) {
        long startedNanos = System.nanoTime();
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
        } catch (IOException exception) {
            logFailure("revoke-access-token", startedNanos, exception);
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
            throw new AuthenticationTransportException(exception.getCause());
        }
    }

    private void handleFailure(String operation, long startedNanos, Exception exception) {
        handleFailure(operation, startedNanos, exception, null);
    }

    private void handleFailure(
            String operation,
            long startedNanos,
            Exception exception,
            AuthState expectedState
    ) {
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            if (closed.get()) {
                return;
            }
        }
        AuthPhase phase;
        String userMessage;
        if (exception instanceof RestStatusException statusException) {
            phase = switch (statusException.statusCode()) {
                case 401 -> AuthPhase.EXPIRED;
                case 403 -> AuthPhase.FORBIDDEN;
                default -> AuthPhase.FAILED;
            };
            if (AUTH_CONFIGURATION_MISSING.equals(statusException.errorCode())) {
                userMessage = LOGIN_UNAVAILABLE_MESSAGE;
            } else {
                userMessage = switch (phase) {
                    case EXPIRED -> "로그인 시간이 만료되었습니다. 다시 시도해 주세요.";
                    case FORBIDDEN -> "이 계정으로는 게임에 접속할 수 없습니다.";
                    default -> LOGIN_UNAVAILABLE_MESSAGE;
                };
            }
        } else if (exception instanceof AuthenticationTransportException) {
            phase = AuthPhase.OFFLINE;
            userMessage = "로그인 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
        } else {
            phase = AuthPhase.FAILED;
            userMessage = LOGIN_UNAVAILABLE_MESSAGE;
        }
        AuthState failureState = new AuthState(phase, userMessage, null, null, null);
        if (expectedState != null) {
            if (!replaceState(expectedState, failureState)) {
                return;
            }
            logFailure(operation, startedNanos, exception);
            return;
        }
        logFailure(operation, startedNanos, exception);
        currentAttempt = null;
        cancelPolling();
        updateState(failureState);
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

    private void failAttempt() {
        currentAttempt = null;
        cancelPolling();
        updateState(new AuthState(
                AuthPhase.FAILED,
                "Google 로그인을 완료하지 못했습니다. 다시 시도해 주세요.",
                null,
                null,
                null));
    }

    private void updateState(AuthState newState) {
        AuthState next = Objects.requireNonNull(newState, "newState");
        AuthState previous = state.getAndSet(next);
        publishStateChange(previous, next);
    }

    private boolean replaceState(AuthState expectedState, AuthState newState) {
        AuthState expected = Objects.requireNonNull(expectedState, "expectedState");
        AuthState next = Objects.requireNonNull(newState, "newState");
        if (!state.compareAndSet(expected, next)) {
            return false;
        }
        publishStateChange(expected, next);
        return true;
    }

    private void publishStateChange(AuthState previous, AuthState next) {
        if (previous.phase() != next.phase()) {
            log.info("Authentication phase changed: {} -> {}", previous.phase(), next.phase());
        }
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        try {
            stateListener.run();
        } catch (RuntimeException exception) {
            log.warn(
                    "Authentication state listener failed: exception={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void cancelPolling() {
        ScheduledFuture<?> poll = scheduledPoll;
        scheduledPoll = null;
        if (poll != null) {
            poll.cancel(false);
        }
    }

    private void requireStatus(AggregatedHttpResponse response, int... acceptedStatuses)
            throws RestStatusException {
        int actual = response.status().code();
        for (int accepted : acceptedStatuses) {
            if (actual == accepted) {
                return;
            }
        }
        throw new RestStatusException(actual, responseErrorCode(response));
    }

    private String responseErrorCode(AggregatedHttpResponse response) {
        try {
            ErrorResponse errorResponse = objectMapper.readValue(
                    response.contentUtf8(), ErrorResponse.class);
            return errorResponse == null ? null : errorResponse.code();
        } catch (JacksonException exception) {
            return null;
        }
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

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static void logFailure(String operation, long startedNanos, Exception exception) {
        if (exception instanceof RestStatusException statusException) {
            log.warn(
                    "Authentication operation failed: operation={}, httpStatus={}, errorCode={},"
                            + " elapsedMs={}, exception={}",
                    operation,
                    statusException.statusCode(),
                    safeErrorCode(statusException.errorCode()),
                    elapsedMillis(startedNanos),
                    exception.getClass().getSimpleName());
            return;
        }
        log.warn(
                "Authentication operation failed: operation={}, elapsedMs={}, exception={}, rootCause={}",
                operation,
                elapsedMillis(startedNanos),
                exception.getClass().getSimpleName(),
                deepestCauseType(exception));
    }

    private static String safeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank() || errorCode.length() > 64) {
            return "none";
        }
        for (int index = 0; index < errorCode.length(); index++) {
            char value = errorCode.charAt(index);
            if (!(value == '_' || value == '-' || Character.isLetterOrDigit(value))) {
                return "invalid";
            }
        }
        return errorCode;
    }

    private static String deepestCauseType(Throwable throwable) {
        Throwable deepest = throwable;
        for (int depth = 0; depth < 16 && deepest.getCause() != null; depth++) {
            deepest = deepest.getCause();
        }
        return deepest == throwable ? "none" : deepest.getClass().getSimpleName();
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorResponse(String code) {
    }

    private static final class RestStatusException extends IOException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;
        private final String errorCode;

        private RestStatusException(int statusCode, String errorCode) {
            super("authentication API returned HTTP " + statusCode);
            this.statusCode = statusCode;
            this.errorCode = errorCode;
        }

        private int statusCode() {
            return statusCode;
        }

        private String errorCode() {
            return errorCode;
        }
    }

    private static final class AuthenticationTransportException extends IOException {
        private static final long serialVersionUID = 1L;

        private AuthenticationTransportException(Throwable cause) {
            super("authentication API transport failed", cause);
        }
    }
}
