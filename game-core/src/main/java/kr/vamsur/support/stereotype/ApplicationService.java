package kr.vamsur.support.stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Service;

/**
 * Spring stereotype for an application service implementation.
 *
 * <p>Transactions are deliberately not included because real-time gameplay methods run at 60 Hz.
 * Persistence-oriented services can declare their own transaction boundary.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface ApplicationService {
}
