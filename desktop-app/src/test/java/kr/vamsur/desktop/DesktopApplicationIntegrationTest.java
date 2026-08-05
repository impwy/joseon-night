package kr.vamsur.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServiceRequestContext;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import kr.vamsur.desktop.client.DesktopApiClient;
import kr.vamsur.desktop.gameplay.DesktopApiStatus;
import kr.vamsur.desktop.gameplay.GamePhase;
import kr.vamsur.desktop.gameplay.InputState;
import kr.vamsur.desktop.gameplay.UpgradeType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

class DesktopApplicationIntegrationTest {
    @Test
    void desktopStartsAsAnApiClientAndNeverBlocksOnASecondTick() throws Exception {
        try (MockDesktopApi backend = new MockDesktopApi()) {
            DesktopApiClient apiClient;

            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(DesktopApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(
                            "--spring.main.banner-mode=off",
                            "--joseon-night.desktop-api.base-url=" + backend.baseUrl(),
                            "--joseon-night.desktop-api.connect-timeout=1s",
                            "--joseon-night.desktop-api.request-timeout=2s")) {
                apiClient = context.getBean(DesktopApiClient.class);

                await(() -> apiClient.status().state() == DesktopApiStatus.State.ONLINE);
                assertThat(apiClient.snapshot().phase()).isEqualTo(GamePhase.LOBBY);
                assertThat(context.getBean(ClientFactory.class)).isNotNull();
                assertThat(context.getBean(WebClient.class)).isNotNull();
                assertThat(context.getBeansOfType(Server.class)).isEmpty();
                assertThat(ClassUtils.isPresent(
                        "org.springframework.data.redis.connection.RedisConnectionFactory",
                        context.getClassLoader())).isFalse();
                assertThat(ClassUtils.isPresent(
                        "jakarta.persistence.EntityManagerFactory", context.getClassLoader())).isFalse();
                assertThat(ClassUtils.isPresent(
                        "org.springframework.kafka.core.KafkaTemplate", context.getClassLoader())).isFalse();

                apiClient.startNewGame();
                await(() -> apiClient.snapshot().phase() == GamePhase.RUNNING);

                apiClient.setInput(new InputState(true, false, true, false));
                assertThat(apiClient.tick(1.0 / 60.0, 3)).isTrue();
                assertThat(backend.tickStarted.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(apiClient.tick(1.0 / 60.0, 1)).isFalse();
                backend.releaseTick.countDown();
                await(() -> apiClient.snapshot().elapsedSeconds() > 0.0);

                apiClient.chooseUpgrade(UpgradeType.MOVEMENT_SPEED);
                await(() -> backend.hasRequest("POST", "/api/v1/game/upgrade"));

                assertThat(backend.requests).anySatisfy(request -> {
                    assertThat(request.method()).isEqualTo("PUT");
                    assertThat(request.path()).isEqualTo("/api/v1/game/input");
                    assertThat(request.body())
                            .contains("\"up\":true")
                            .contains("\"left\":true");
                });
                assertThat(backend.requests).anySatisfy(request -> {
                    assertThat(request.method()).isEqualTo("POST");
                    assertThat(request.path()).isEqualTo("/api/v1/game/tick");
                    assertThat(request.body())
                            .contains("\"deltaSeconds\":")
                            .contains("\"steps\":3");
                });
                assertThat(backend.requests).anySatisfy(request -> {
                    assertThat(request.path()).isEqualTo("/api/v1/game/upgrade");
                    assertThat(request.body()).contains("\"upgradeType\":\"MOVEMENT_SPEED\"");
                });

                assertThatThrownBy(() -> apiClient.tick(0.0, 1))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> apiClient.tick(1.0 / 60.0, 16))
                        .isInstanceOf(IllegalArgumentException.class);

                backend.failRequests.set(true);
                apiClient.refresh();
                await(() -> apiClient.status().state() == DesktopApiStatus.State.OFFLINE);
                assertThat(apiClient.status().message()).contains("game-core 연결 실패");
            }

            assertThat(apiClient.tick(1.0 / 60.0, 1)).isFalse();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static final class MockDesktopApi implements AutoCloseable {
        private final Server server;
        private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
        private final CountDownLatch tickStarted = new CountDownLatch(1);
        private final CountDownLatch releaseTick = new CountDownLatch(1);
        private final AtomicBoolean failRequests = new AtomicBoolean();

        private MockDesktopApi() {
            server = Server.builder()
                    .http(0)
                    .serviceUnder("/", (context, request) -> {
                        CompletionStage<HttpResponse> response = request.aggregate()
                                .thenApply(aggregated -> handle(context, aggregated));
                        return HttpResponse.of(response);
                    })
                    .build();
            server.start().join();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.activeLocalPort(SessionProtocol.HTTP);
        }

        private boolean hasRequest(String method, String path) {
            return requests.stream().anyMatch(request ->
                    request.method().equals(method) && request.path().equals(path));
        }

        private HttpResponse handle(ServiceRequestContext context, AggregatedHttpRequest request) {
            String path = context.path();
            String body = request.contentUtf8();
            requests.add(new RecordedRequest(request.method().name(), path, body));

            if (failRequests.get()) {
                return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE);
            }

            if ("/api/v1/game/tick".equals(path)) {
                tickStarted.countDown();
                try {
                    releaseTick.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE);
                }
            }

            String phase = "/api/v1/game/status".equals(path) ? "LOBBY" : "RUNNING";
            double elapsedSeconds = "/api/v1/game/tick".equals(path) ? 0.017 : 0.0;
            return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, snapshotJson(phase, elapsedSeconds));
        }

        @Override
        public void close() {
            releaseTick.countDown();
            server.stop().join();
        }

        private static String snapshotJson(String phase, double elapsedSeconds) {
            return """
                    {
                      "phase":"$PHASE",
                      "elapsedSeconds":$ELAPSED_SECONDS,
                      "remainingSeconds":299.983,
                      "level":1,
                      "experience":0,
                      "experienceToNextLevel":5,
                      "killCount":0,
                      "player":{"id":1,"x":0.0,"y":0.0,"radius":18.0,"rotationDegrees":0.0},
                      "enemies":[],
                      "projectiles":[],
                      "soulFlames":[],
                      "upgradeChoices":["MOVEMENT_SPEED","SOUL_MAGNET","TALISMAN_DAMAGE"]
                    }
                    """
                    .replace("$PHASE", phase)
                    .replace("$ELAPSED_SECONDS", Double.toString(elapsedSeconds));
        }
    }

    private record RecordedRequest(String method, String path, String body) {
    }
}
