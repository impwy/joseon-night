package kr.vamsur.adapter.configuration;

import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import kr.vamsur.adapter.webapi.GameStatusHttpService;
import kr.vamsur.adapter.webapi.GameStatusPublisher;
import kr.vamsur.application.gameplay.GameService;
import kr.vamsur.application.gameplay.provided.GameUseCase;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the local desktop process. Deliberately does not enable a servlet web server.
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class DesktopSpringConfiguration {
    @Bean
    GameUseCase gameUseCase() {
        return GameService.defaultGame();
    }

    @Bean
    GameStatusPublisher gameStatusPublisher(GameUseCase gameUseCase) {
        return new GameStatusPublisher(gameUseCase.snapshot());
    }

    @Bean
    ArmeriaServerConfigurator gameApiConfigurator(GameStatusPublisher statusPublisher) {
        return serverBuilder -> serverBuilder
                .service("/api/v1/game/status", new GameStatusHttpService(statusPublisher));
    }
}
