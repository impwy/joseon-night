package kr.joseonnight.desktop.configuration;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import java.net.URI;
import java.time.Duration;
import kr.joseonnight.desktop.client.AuthApiClient;
import kr.joseonnight.desktop.client.DesktopApiClient;
import kr.joseonnight.desktop.client.MemberApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * JavaFX desktop client wiring.
 */
@Configuration(proxyBeanMethods = false)
public class DesktopSpringConfiguration {
    @Bean(destroyMethod = "close")
    ClientFactory desktopClientFactory(
            @Value("${joseon-night.desktop-api.connect-timeout}") Duration connectTimeout) {
        return ClientFactory.builder()
                .connectTimeout(connectTimeout)
                .build();
    }

    @Bean
    WebClient desktopWebClient(
            ClientFactory desktopClientFactory,
            @Value("${joseon-night.desktop-api.base-url}") URI baseUri,
            @Value("${joseon-night.desktop-api.request-timeout}") Duration requestTimeout) {
        return WebClient.builder(baseUri)
                .factory(desktopClientFactory)
                .responseTimeout(requestTimeout)
                .build();
    }

    @Bean(destroyMethod = "close")
    AuthApiClient authApiClient(
            WebClient desktopWebClient,
            ObjectMapper objectMapper,
            @Value("${joseon-night.authentication.poll-interval}") Duration pollInterval,
            @Value("${joseon-night.authentication.maximum-poll-duration}") Duration maximumPollDuration) {
        return new AuthApiClient(
                desktopWebClient,
                objectMapper,
                pollInterval,
                maximumPollDuration);
    }

    @Bean(destroyMethod = "close")
    MemberApiClient memberApiClient(WebClient desktopWebClient, ObjectMapper objectMapper) {
        return new MemberApiClient(desktopWebClient, objectMapper);
    }

    @Bean(destroyMethod = "close")
    DesktopApiClient desktopApiClient(
            WebClient desktopWebClient,
            ClientFactory desktopClientFactory,
            ObjectMapper objectMapper) {
        return new DesktopApiClient(
                desktopWebClient,
                desktopClientFactory,
                objectMapper);
    }
}
