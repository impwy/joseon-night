package kr.vamsur.desktop.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.Server;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import kr.vamsur.GameCoreApplication;
import kr.vamsur.desktop.client.DesktopApiClient;
import kr.vamsur.desktop.gameplay.DesktopApiStatus;
import kr.vamsur.desktop.gameplay.GamePhase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

class DesktopCoreContractTest {

    @Test
    void actualDesktopClientCommunicatesWithActualGameCoreServer() throws Exception {
        try (ConfigurableApplicationContext core = startGameCore()) {
            Server server = core.getBean(Server.class);
            int port = server.activeLocalPort(SessionProtocol.HTTP);

            try (ClientFactory clientFactory = ClientFactory.builder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .build();
                 DesktopApiClient client = new DesktopApiClient(
                         WebClient.builder("http://127.0.0.1:" + port)
                                 .factory(clientFactory)
                                 .responseTimeout(Duration.ofSeconds(2))
                                 .build(),
                         new ObjectMapper())) {
                await(() -> client.status().state() == DesktopApiStatus.State.ONLINE);
                assertThat(client.snapshot().phase()).isEqualTo(GamePhase.LOBBY);

                client.startNewGame();
                await(() -> client.snapshot().phase() == GamePhase.RUNNING);

                assertThat(client.tick(1.0 / 60.0, 3)).isTrue();
                await(() -> client.snapshot().elapsedSeconds() >= 0.05);

                assertThat(client.snapshot().player()).isNotNull();
                assertThat(client.snapshot().remainingSeconds()).isLessThan(300.0);
            }
        }
    }

    private static ConfigurableApplicationContext startGameCore() {
        return new SpringApplicationBuilder(GameCoreApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.main.banner-mode=off",
                        "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=jdbc:h2:mem:joseon-night-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.kafka.listener.auto-startup=false",
                        "--armeria.ports[0].address=127.0.0.1",
                        "--armeria.ports[0].port=0",
                        "--armeria.ports[0].protocols[0]=HTTP");
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
