package kr.vamsur.adapter.desktopapi;

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
import javax.sql.DataSource;
import kr.vamsur.GameCoreApplication;
import kr.vamsur.application.gameplay.provided.GameRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

class GameCoreSpringIntegrationTest {

    @Test
    void backendStartsWithItsInfrastructureAndServesTheDesktopApi() throws Exception {
        Server server;

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GameCoreApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--armeria.ports[0].address=127.0.0.1",
                        "--armeria.ports[0].port=0",
                        "--armeria.ports[0].protocols[0]=HTTP",
                        "--spring.main.banner-mode=off",
                        "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=jdbc:h2:mem:joseon-night-core;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.kafka.listener.auto-startup=false")) {
            server = context.getBean(Server.class);
            GameRunner gameRunner = context.getBean(GameRunner.class);

            assertThatThrownBy(() -> gameRunner.setInput(null))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> gameRunner.tick(0.0))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> gameRunner.chooseUpgrade(null))
                    .isInstanceOf(ConstraintViolationException.class);

            assertInfrastructureBeans(context);

            int port = server.activeLocalPort(SessionProtocol.HTTP);
            assertThat(port).isPositive();
            assertThat(server.activePort(SessionProtocol.HTTP).localAddress().getAddress().isLoopbackAddress())
                    .isTrue();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            URI baseUri = URI.create("http://127.0.0.1:" + port);

            assertThat(get(client, baseUri.resolve("/internal/healthcheck")).statusCode()).isEqualTo(200);

            HttpResponse<String> initialStatus = get(client, baseUri.resolve("/api/v1/game/status"));
            assertJsonResponse(initialStatus);
            assertThat(initialStatus.body())
                    .contains("\"phase\":\"LOBBY\"")
                    .contains("\"player\":")
                    .contains("\"enemies\":[]")
                    .contains("\"projectiles\":[]")
                    .contains("\"soulFlames\":[]")
                    .contains("\"upgradeChoices\":[]");

            HttpResponse<String> started = sendJson(
                    client,
                    baseUri.resolve("/api/v1/game/start"),
                    "POST",
                    "");
            assertJsonResponse(started);
            assertThat(started.body()).contains("\"phase\":\"RUNNING\"");

            HttpResponse<String> input = sendJson(
                    client,
                    baseUri.resolve("/api/v1/game/input"),
                    "PUT",
                    "{\"up\":true,\"down\":false,\"left\":false,\"right\":true}");
            assertJsonResponse(input);

            HttpResponse<String> tick = sendJson(
                    client,
                    baseUri.resolve("/api/v1/game/tick"),
                    "POST",
                    "{\"deltaSeconds\":0.016666666666666666,\"steps\":3}");
            assertJsonResponse(tick);
            assertThat(tick.body()).contains("\"elapsedSeconds\":0.05");

            HttpResponse<String> invalidTick = sendJson(
                    client,
                    baseUri.resolve("/api/v1/game/tick"),
                    "POST",
                    "{\"deltaSeconds\":0,\"steps\":1}");
            assertThat(invalidTick.statusCode()).isEqualTo(400);
            assertThat(invalidTick.body()).isEqualTo("{\"error\":\"validation_failed\"}");

            HttpResponse<String> tooManySteps = sendJson(
                    client,
                    baseUri.resolve("/api/v1/game/tick"),
                    "POST",
                    "{\"deltaSeconds\":0.016666666666666666,\"steps\":16}");
            assertThat(tooManySteps.statusCode()).isEqualTo(400);

            HttpResponse<String> invalidInput = sendJson(
                    client,
                    baseUri.resolve("/api/v1/game/input"),
                    "PUT",
                    "{\"up\":true}");
            assertThat(invalidInput.statusCode()).isEqualTo(400);

            HttpResponse<String> invalidUpgrade = sendJson(
                    client,
                    baseUri.resolve("/api/v1/game/upgrade"),
                    "POST",
                    "{\"upgradeType\":null}");
            assertThat(invalidUpgrade.statusCode()).isEqualTo(400);
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

    private static HttpResponse<String> sendJson(
            HttpClient client,
            URI uri,
            String method,
            String body
    ) throws Exception {
        HttpRequest.BodyPublisher publisher = body.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(3))
                .header("content-type", "application/json")
                .method(method, publisher)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
