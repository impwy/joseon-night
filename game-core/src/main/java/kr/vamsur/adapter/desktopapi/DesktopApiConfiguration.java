package kr.vamsur.adapter.desktopapi;

import com.linecorp.armeria.server.annotation.JacksonRequestConverterFunction;
import com.linecorp.armeria.server.annotation.JacksonResponseConverterFunction;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the desktop-facing Armeria adapter without Spring Web or Tomcat.
 */
@Configuration(proxyBeanMethods = false)
public class DesktopApiConfiguration {

    @Bean
    ArmeriaServerConfigurator desktopApiConfigurator(DesktopApi desktopApi) {
        return serverBuilder -> serverBuilder.annotatedService(
                desktopApi,
                new JacksonRequestConverterFunction(),
                new JacksonResponseConverterFunction(),
                new DesktopApiExceptionHandler()
        );
    }
}
