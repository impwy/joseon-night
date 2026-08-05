package kr.vamsur.desktop;

import kr.vamsur.desktop.configuration.DesktopLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Spring Boot composition root for the desktop game process.
 */
@SpringBootApplication
public class DesktopApplication {

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = SpringApplication.run(DesktopApplication.class, args)) {
            DesktopLauncher.launch(context, args);
        }
    }
}
