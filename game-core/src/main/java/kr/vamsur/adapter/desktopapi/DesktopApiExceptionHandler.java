package kr.vamsur.adapter.desktopapi;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import jakarta.validation.ConstraintViolationException;

/**
 * Converts adapter-boundary validation failures into a stable client response.
 */
public final class DesktopApiExceptionHandler implements ExceptionHandlerFunction {

    @Override
    public HttpResponse handleException(
            ServiceRequestContext context,
            HttpRequest request,
            Throwable cause
    ) {
        if (hasConstraintViolation(cause)) {
            return HttpResponse.of(
                    HttpStatus.BAD_REQUEST,
                    MediaType.JSON_UTF_8,
                    "{\"error\":\"validation_failed\"}"
            );
        }
        return ExceptionHandlerFunction.fallthrough();
    }

    private static boolean hasConstraintViolation(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof ConstraintViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
