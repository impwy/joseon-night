package kr.vamsur.adapter.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.Server;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import kr.vamsur.adapter.webapi.GameStatusPublisher;
import kr.vamsur.application.gameplay.provided.GameUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class DesktopSpringIntegrationTest {
    @Test
    void springContextStartsArmeriaOnRandomLoopbackPortAndServesStatus() throws Exception {
        Server server;

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(DesktopSpringConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--armeria.ports[0].address=127.0.0.1",
                        "--armeria.ports[0].port=0",
                        "--armeria.ports[0].protocols[0]=HTTP",
                        "--spring.main.banner-mode=off")) {
            server = context.getBean(Server.class);
            GameUseCase game = context.getBean(GameUseCase.class);
            GameStatusPublisher publisher = context.getBean(GameStatusPublisher.class);

            int port = server.activeLocalPort(SessionProtocol.HTTP);
            assertThat(port).isPositive();
            assertThat(server.activePort(SessionProtocol.HTTP).localAddress().getAddress().isLoopbackAddress())
                    .isTrue();

            game.startNewGame();
            publisher.publish(game.snapshot());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            URI baseUri = URI.create("http://127.0.0.1:" + port);

            HttpResponse<String> healthResponse = get(client, baseUri.resolve("/internal/healthcheck"));
            assertThat(healthResponse.statusCode()).isEqualTo(200);

            assertThat(get(client, baseUri.resolve("/internal/docs")).statusCode()).isEqualTo(404);
            assertThat(get(client, baseUri.resolve("/internal/metrics")).statusCode()).isEqualTo(404);

            HttpResponse<String> statusResponse = get(client, baseUri.resolve("/api/v1/game/status"));
            assertThat(statusResponse.statusCode()).isEqualTo(200);
            assertThat(statusResponse.headers().firstValue("content-type")).hasValueSatisfying(
                    value -> assertThat(value).startsWith("application/json"));
            assertThat(statusResponse.body())
                    .contains("\"phase\":\"RUNNING\"")
                    .contains("\"remainingSeconds\":")
                    .contains("\"level\":1")
                    .contains("\"enemyCount\":")
                    .contains("\"killCount\":0");
        }

        assertThat(server.isClosed()).isTrue();
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
