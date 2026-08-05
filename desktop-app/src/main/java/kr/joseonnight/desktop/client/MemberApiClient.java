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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import kr.joseonnight.desktop.member.MemberBootstrap;
import kr.joseonnight.desktop.settings.AudioSettings;
import tools.jackson.databind.ObjectMapper;

/** Authenticated member/bootstrap/settings REST client. */
public final class MemberApiClient implements AutoCloseable {
    static final String BOOTSTRAP_PATH = "/api/v1/members/me/bootstrap";
    static final String SETTINGS_PATH = "/api/v1/members/me/settings";
    static final long SETTINGS_DEBOUNCE_MILLIS = 500L;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService worker;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong settingsVersion = new AtomicLong();

    private volatile ScheduledFuture<?> pendingSettingsSave;
    private volatile Consumer<String> failureListener = ignored -> { };

    public MemberApiClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        worker = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform()
                .daemon()
                .name("desktop-member-client")
                .unstarted(task));
    }

    public void setFailureListener(Consumer<String> listener) {
        failureListener = Objects.requireNonNull(listener, "listener");
    }

    public CompletableFuture<MemberBootstrap> loadBootstrap(String accessToken) {
        String token = requireToken(accessToken);
        return supplyAsync(() -> {
            AggregatedHttpResponse response = request(HttpMethod.GET, BOOTSTRAP_PATH, null, token);
            requireSuccess(response);
            BootstrapResponse body = objectMapper.readValue(response.contentUtf8(), BootstrapResponse.class);
            String nickname = body.nickname();
            if ((nickname == null || nickname.isBlank()) && body.member() != null) {
                nickname = body.member().nickname();
            }
            List<MemberBootstrap.CharacterOption> characters = body.characters() == null
                    ? List.of()
                    : body.characters().stream()
                            .map(character -> new MemberBootstrap.CharacterOption(
                                    character.characterId(), character.displayName()))
                            .toList();
            return new MemberBootstrap(nickname, characters);
        });
    }

    public CompletableFuture<AudioSettings> loadSettings(String accessToken) {
        String token = requireToken(accessToken);
        return supplyAsync(() -> {
            AggregatedHttpResponse response = request(HttpMethod.GET, SETTINGS_PATH, null, token);
            requireSuccess(response);
            SettingsResponse body = objectMapper.readValue(response.contentUtf8(), SettingsResponse.class);
            return new AudioSettings(body.muted(), body.masterVolume());
        });
    }

    /**
     * Saves only the newest value after 500 ms without another change.
     */
    public void saveSettingsDebounced(String accessToken, AudioSettings settings) {
        String token = requireToken(accessToken);
        AudioSettings value = Objects.requireNonNull(settings, "settings");
        if (closed.get()) {
            return;
        }
        long version = settingsVersion.incrementAndGet();
        ScheduledFuture<?> previous = pendingSettingsSave;
        if (previous != null) {
            previous.cancel(false);
        }
        pendingSettingsSave = worker.schedule(
                () -> persistSettings(token, value, version),
                SETTINGS_DEBOUNCE_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            ScheduledFuture<?> pending = pendingSettingsSave;
            if (pending != null) {
                pending.cancel(false);
            }
            worker.shutdownNow();
        }
    }

    private void persistSettings(String accessToken, AudioSettings settings, long version) {
        if (closed.get() || version != settingsVersion.get()) {
            return;
        }
        try {
            AggregatedHttpResponse response = request(
                    HttpMethod.PATCH,
                    SETTINGS_PATH,
                    new SettingsRequest(settings.muted(), settings.masterVolume()),
                    accessToken);
            requireSuccess(response);
        } catch (Exception exception) {
            notifyFailure(settingsMessage(exception));
        }
    }

    private <T> CompletableFuture<T> supplyAsync(ThrowingSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed.get()) {
            future.completeExceptionally(new IllegalStateException("member client is closed"));
            return future;
        }
        worker.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private AggregatedHttpResponse request(
            HttpMethod method,
            String path,
            Object body,
            String accessToken) throws IOException, InterruptedException {
        RequestHeadersBuilder headers = RequestHeaders.builder(method, path)
                .add(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken);
        HttpData content = HttpData.empty();
        if (body != null) {
            headers.contentType(MediaType.JSON_UTF_8);
            content = HttpData.ofUtf8(objectMapper.writeValueAsString(body));
        }
        try {
            return webClient.execute(HttpRequest.of(headers.build(), content)).aggregate().get();
        } catch (ExecutionException exception) {
            throw new IOException("member API request failed", exception.getCause());
        }
    }

    private static void requireSuccess(AggregatedHttpResponse response) throws IOException {
        int status = response.status().code();
        if (status < 200 || status >= 300) {
            throw new MemberApiException(status);
        }
    }

    private void notifyFailure(String message) {
        try {
            failureListener.accept(message);
        } catch (RuntimeException ignored) {
            // A presentation callback must not stop the settings worker.
        }
    }

    private static String settingsMessage(Exception exception) {
        if (exception instanceof MemberApiException apiException) {
            return switch (apiException.statusCode()) {
                case 401 -> "로그인이 만료되어 설정을 저장하지 못했습니다.";
                case 403 -> "이 계정에는 설정을 바꿀 권한이 없습니다.";
                default -> "설정 서버가 저장 요청을 처리하지 못했습니다.";
            };
        }
        return "네트워크 연결 문제로 설정을 저장하지 못했습니다.";
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        return token;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BootstrapResponse(
            String nickname,
            MemberResponse member,
            List<CharacterResponse> characters) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MemberResponse(String nickname) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CharacterResponse(String characterId, String displayName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SettingsResponse(boolean muted, int masterVolume) {
    }

    private record SettingsRequest(boolean muted, int masterVolume) {
    }

    private static final class MemberApiException extends IOException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;

        private MemberApiException(int statusCode) {
            super("member API returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
