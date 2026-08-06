package kr.joseonnight.adapter.webapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.joseonnight.adapter.security.authweb.AuthenticationConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RestApiExceptionHandlerTest {

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.of(
                List.of(new FailureApi()),
                builder -> builder
                        .setControllerAdvice(new RestApiExceptionHandler())
                        .build()
        );
    }

    @Test
    void missingAuthenticationConfigurationHasAStableErrorCode() {
        var result = mvc.get().uri("/test/authentication-configuration").exchange();

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.code")
                .asString()
                .isEqualTo(RestApiExceptionHandler.AUTH_CONFIGURATION_MISSING);
        json.extractingPath("$.message")
                .asString()
                .isEqualTo(RestApiExceptionHandler.AUTHENTICATION_UNAVAILABLE_MESSAGE);
    }

    @Test
    void badRequestDoesNotExposeTheInternalExceptionMessage() {
        var result = mvc.get().uri("/test/bad-request").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.message")
                .asString()
                .isEqualTo("요청 내용이 올바르지 않습니다.");
    }

    @RestController
    private static final class FailureApi {

        @GetMapping("/test/authentication-configuration")
        void authenticationConfiguration() {
            throw new AuthenticationConfigurationException();
        }

        @GetMapping("/test/bad-request")
        void badRequest() {
            throw new IllegalArgumentException("private-internal-detail");
        }
    }
}
