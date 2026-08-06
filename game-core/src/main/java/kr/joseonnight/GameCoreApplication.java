package kr.joseonnight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend process that owns the game rules and infrastructure adapters.
 */
@SpringBootApplication(scanBasePackages = {
        "kr.joseonnight.adapter",
        "kr.joseonnight.application",
        "kr.joseonnight.domain",
        "kr.joseonnight.support"
})
public class GameCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameCoreApplication.class, args);
    }
}
