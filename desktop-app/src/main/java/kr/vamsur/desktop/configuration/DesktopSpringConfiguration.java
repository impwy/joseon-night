package kr.vamsur.desktop.configuration;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import java.net.URI;
import java.time.Duration;
import kr.vamsur.desktop.client.DesktopApiClient;
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
    DesktopApiClient desktopApiClient(WebClient desktopWebClient, ObjectMapper objectMapper) {
        return new DesktopApiClient(desktopWebClient, objectMapper);
    }
}
