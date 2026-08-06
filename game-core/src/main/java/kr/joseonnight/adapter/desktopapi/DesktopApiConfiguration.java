package kr.joseonnight.adapter.desktopapi;

import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers only the server-authoritative desktop game socket on Armeria.
 *
 * <p>The client-driven tick API is intentionally not registered because a client must
 * never supply simulation time.</p>
 */
@Configuration(proxyBeanMethods = false)
public class DesktopApiConfiguration {

    @Bean
    ArmeriaServerConfigurator desktopApiConfigurator(GameWebSocketApi gameWebSocketApi) {
        return serverBuilder -> serverBuilder.service(
                GameWebSocketApi.PATH,
                gameWebSocketApi.service());
    }
}
