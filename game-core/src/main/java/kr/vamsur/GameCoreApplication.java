package kr.vamsur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend process that owns the game rules and infrastructure adapters.
 */
@SpringBootApplication(scanBasePackages = {
        "kr.vamsur.adapter",
        "kr.vamsur.application",
        "kr.vamsur.domain",
        "kr.vamsur.support"
})
public class GameCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameCoreApplication.class, args);
    }
}
