package kr.joseonnight.adapter.persistence.redis;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RedisAdapterConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
