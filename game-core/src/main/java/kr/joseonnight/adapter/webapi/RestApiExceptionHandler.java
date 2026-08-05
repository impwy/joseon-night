package kr.joseonnight.adapter.webapi;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import kr.joseonnight.application.member.DuplicateMemberException;
import kr.joseonnight.application.member.MemberNotFoundException;
import kr.joseonnight.application.membersettings.DuplicateNicknameException;
import kr.joseonnight.application.membersettings.MemberSettingsNotFoundException;
import kr.joseonnight.adapter.security.authweb.AuthenticationConfigurationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class RestApiExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiError> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler({MemberNotFoundException.class, MemberSettingsNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler({DuplicateMemberException.class, DuplicateNicknameException.class})
    public ResponseEntity<ApiError> conflict(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler(AuthenticationConfigurationException.class)
    public ResponseEntity<ApiError> unavailable(AuthenticationConfigurationException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, Exception exception) {
        return ResponseEntity.status(status).body(new ApiError(
                status.value(),
                status.getReasonPhrase(),
                exception.getMessage(),
                Instant.now()
        ));
    }

    public record ApiError(int status, String error, String message, Instant timestamp) {
    }
}
