package kr.joseonnight.support.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.docker.compose.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:application-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=validate",
                "armeria.ports[0].address=127.0.0.1",
                "armeria.ports[0].port=0",
                "armeria.ports[0].protocols[0]=HTTP",
                "spring.kafka.listener.auto-startup=false",
                "joseon-night.messaging.outbox-initial-delay-millis=3600000"
        }
)
@Transactional
public @interface ApplicationServiceTest {
}
