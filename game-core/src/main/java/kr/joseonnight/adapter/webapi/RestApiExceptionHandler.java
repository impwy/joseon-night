package kr.joseonnight.adapter.webapi;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import kr.joseonnight.application.member.DuplicateMemberException;
import kr.joseonnight.application.member.MemberNotFoundException;
import kr.joseonnight.application.membersettings.DuplicateNicknameException;
import kr.joseonnight.application.membersettings.MemberSettingsNotFoundException;
import kr.joseonnight.adapter.security.authweb.AuthenticationConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public final class RestApiExceptionHandler {
    static final String AUTH_CONFIGURATION_MISSING = "AUTH_CONFIGURATION_MISSING";
    static final String AUTHENTICATION_UNAVAILABLE_MESSAGE =
            "현재 로그인 서비스를 사용할 수 없습니다.";
    private static final String BAD_REQUEST_MESSAGE = "요청 내용이 올바르지 않습니다.";
    private static final String NOT_FOUND_MESSAGE = "요청한 정보를 찾을 수 없습니다.";
    private static final String CONFLICT_MESSAGE = "이미 처리되었거나 사용 중인 정보입니다.";

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiError> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, null, BAD_REQUEST_MESSAGE, exception);
    }

    @ExceptionHandler({MemberNotFoundException.class, MemberSettingsNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, null, NOT_FOUND_MESSAGE, exception);
    }

    @ExceptionHandler({DuplicateMemberException.class, DuplicateNicknameException.class})
    public ResponseEntity<ApiError> conflict(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, null, CONFLICT_MESSAGE, exception);
    }

    @ExceptionHandler(AuthenticationConfigurationException.class)
    public ResponseEntity<ApiError> unavailable(AuthenticationConfigurationException exception) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                AUTH_CONFIGURATION_MISSING,
                AUTHENTICATION_UNAVAILABLE_MESSAGE,
                exception);
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String publicMessage,
            Exception exception
    ) {
        log.warn(
                "REST request failed: httpStatus={}, errorCode={}, exception={}",
                status.value(),
                code == null ? "none" : code,
                exception.getClass().getSimpleName());
        return ResponseEntity.status(status).body(new ApiError(
                status.value(),
                status.getReasonPhrase(),
                code,
                publicMessage,
                Instant.now()
        ));
    }

    public record ApiError(int status, String error, String code, String message, Instant timestamp) {
    }
}
