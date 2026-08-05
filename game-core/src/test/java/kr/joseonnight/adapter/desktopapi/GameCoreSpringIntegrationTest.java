package kr.joseonnight.adapter.desktopapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.Server;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import javax.sql.DataSource;
import kr.joseonnight.GameCoreApplication;
import kr.joseonnight.application.gameplay.provided.GameRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

class GameCoreSpringIntegrationTest {

    @Test
    void backendStartsWithItsInfrastructureAndServesTheDesktopApi() throws Exception {
        Server server;

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GameCoreApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--armeria.ports[0].address=127.0.0.1",
                        "--armeria.ports[0].port=0",
                        "--armeria.ports[0].protocols[0]=HTTP",
                        "--spring.main.banner-mode=off",
                        "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=jdbc:h2:mem:joseon-night-core;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.kafka.listener.auto-startup=false",
                        "--joseon-night.messaging.outbox-initial-delay-millis=3600000")) {
            server = context.getBean(Server.class);
            GameRunner gameRunner = context.getBean(GameRunner.class);

            assertThatThrownBy(() -> gameRunner.setInput(null))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> gameRunner.tick(0.0))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> gameRunner.chooseUpgrade(null))
                    .isInstanceOf(ConstraintViolationException.class);

            assertInfrastructureBeans(context);

            int gamePort = server.activeLocalPort(SessionProtocol.HTTP);
            var webServer = Objects.requireNonNull(
                    ((ServletWebServerApplicationContext) context).getWebServer()
            );
            int restPort = webServer.getPort();
            assertThat(gamePort).isPositive().isNotEqualTo(restPort);
            assertThat(restPort).isPositive();
            var activePort = Objects.requireNonNull(server.activePort(SessionProtocol.HTTP));
            var boundAddress = Objects.requireNonNull(activePort.localAddress().getAddress());
            assertThat(boundAddress.isLoopbackAddress()).isTrue();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            URI gameBaseUri = URI.create("http://127.0.0.1:" + gamePort);
            URI restBaseUri = URI.create("http://127.0.0.1:" + restPort);

            assertThat(get(client, gameBaseUri.resolve("/internal/healthcheck")).statusCode())
                    .isEqualTo(200);
            assertThat(get(client, gameBaseUri.resolve("/api/v1/game/ws")).statusCode())
                    .isEqualTo(401);

            HttpResponse<String> ranking = get(
                    client,
                    restBaseUri.resolve("/api/v1/rankings/survival?limit=5")
            );
            assertJsonResponse(ranking);
            assertThat(ranking.body()).isEqualTo("[]");
            assertThat(get(client, restBaseUri.resolve("/api/v1/members/me/settings")).statusCode())
                    .isEqualTo(401);
            assertThat(post(client, restBaseUri.resolve("/api/v1/game/socket-tickets")).statusCode())
                    .isEqualTo(401);
        }

        assertThat(server.isClosed()).isTrue();
    }

    private static void assertInfrastructureBeans(ConfigurableApplicationContext context) throws Exception {
        DataSource dataSource = context.getBean(DataSource.class);
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
        assertThat(context.getBean(EntityManagerFactory.class)).isNotNull();
        assertThat(context.getBean(RedisConnectionFactory.class)).isNotNull();
        assertThat(context.getBean(KafkaTemplate.class)).isNotNull();
        assertThat(Class.forName("org.springframework.security.crypto.password.PasswordEncoder")).isNotNull();
    }

    private static void assertJsonResponse(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient client, URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(3))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

}
