package kr.joseonnight.adapter.security.authweb;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import kr.joseonnight.GameCoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import tools.jackson.databind.ObjectMapper;

class OAuthSecurityIntegrationTest {

    @Test
    void browserChainRedirectsWithOnlyOpenidNonceStateAndPkceWithoutCallingGoogle() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

        try (var context = new SpringApplicationBuilder(GameCoreApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--armeria.ports[0].address=127.0.0.1",
                        "--armeria.ports[0].port=0",
                        "--armeria.ports[0].protocols[0]=HTTP",
                        "--spring.main.banner-mode=off",
                        "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=jdbc:h2:mem:oauth-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.kafka.listener.auto-startup=false",
                        "--joseon-night.messaging.outbox-initial-delay-millis=3600000",
                        "--joseon-night.security.google.client-id=desktop-client",
                        "--joseon-night.security.google.client-secret=server-only-secret",
                        "--joseon-night.security.google.subject-hmac-secret=hmac-secret-that-is-at-least-32-characters",
                        "--joseon-night.security.jwt.public-key-base64=" + publicKey,
                        "--joseon-night.security.jwt.private-key-base64=" + privateKey)) {
            var webContext = (ServletWebServerApplicationContext) context;
            int port = Objects.requireNonNull(webContext.getWebServer()).getPort();
            URI baseUri = URI.create("http://127.0.0.1:" + port);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            HttpResponse<String> attemptResponse = client.send(
                    HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/desktop/attempts"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertThat(attemptResponse.statusCode()).isEqualTo(201);
            URI returnedAuthorizationUri = URI.create(
                    mapper.readTree(attemptResponse.body()).get("authorizationUri").stringValue());
            assertThat(returnedAuthorizationUri.getQuery())
                    .startsWith("launchToken=")
                    .doesNotContain("pollToken");
            URI localAuthorizationUri = baseUri.resolve(
                    returnedAuthorizationUri.getRawPath() + "?" + returnedAuthorizationUri.getRawQuery());

            HttpResponse<String> launchResponse = client.send(
                    HttpRequest.newBuilder(localAuthorizationUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertThat(launchResponse.statusCode()).isEqualTo(302);
            assertThat(launchResponse.headers().firstValue("location"))
                    .hasValueSatisfying(location ->
                            assertThat(location).contains("/oauth2/authorization/google"));
            String sessionCookie = launchResponse.headers().firstValue("set-cookie")
                    .orElseThrow()
                    .split(";", 2)[0];

            HttpResponse<String> googleRedirect = client.send(
                    HttpRequest.newBuilder(baseUri.resolve("/oauth2/authorization/google"))
                            .header("Cookie", sessionCookie)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertThat(googleRedirect.statusCode()).isEqualTo(302);
            String location = googleRedirect.headers().firstValue("location").orElseThrow();
            assertThat(location)
                    .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                    .contains("scope=openid")
                    .contains("state=")
                    .contains("nonce=")
                    .contains("code_challenge=")
                    .contains("code_challenge_method=S256")
                    .doesNotContain("email")
                    .doesNotContain("profile");
        }
    }
}
