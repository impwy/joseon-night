package kr.joseonnight.adapter.security.authweb;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import kr.joseonnight.application.member.provided.DesktopAuthentication;
import kr.joseonnight.application.member.provided.DesktopLoginExchange;
import kr.joseonnight.application.member.provided.DesktopLoginStart;
import kr.joseonnight.application.member.provided.DesktopRegistrationResult;
import kr.joseonnight.application.member.provided.IssuedAccessToken;
import kr.joseonnight.application.member.required.DesktopLoginStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class DesktopAuthController {
    private final ObjectProvider<DesktopAuthentication> authenticationProvider;

    @PostMapping("/desktop/attempts")
    public ResponseEntity<LoginAttemptResponse> createAttempt() {
        DesktopLoginStart started = authentication().start();
        return ResponseEntity.status(HttpStatus.CREATED).body(new LoginAttemptResponse(
                started.attemptId(),
                started.pollToken(),
                started.authorizationUri(),
                started.expiresAt()
        ));
    }

    @PostMapping("/desktop/attempts/{attemptId}/exchange")
    public ResponseEntity<LoginExchangeResponse> exchange(
            @PathVariable UUID attemptId,
            @Valid @RequestBody LoginExchangeRequest request
    ) {
        DesktopLoginExchange exchanged = authentication().exchange(attemptId, request.pollToken());
        LoginExchangeResponse response = LoginExchangeResponse.from(exchanged);
        return exchanged.status() == DesktopLoginStatus.PENDING
                ? ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
                : ResponseEntity.ok(response);
    }

    @PostMapping("/desktop/registrations")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request
    ) {
        DesktopRegistrationResult registered = authentication().register(
                request.registrationTicket(),
                request.nickname()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(RegistrationResponse.from(registered));
    }

    private DesktopAuthentication authentication() {
        DesktopAuthentication authentication = authenticationProvider.getIfAvailable();
        if (authentication == null) {
            log.warn(
                    "Desktop authentication unavailable: required Google OAuth or JWT configuration is missing");
            throw new AuthenticationConfigurationException();
        }
        return authentication;
    }

    public record LoginAttemptResponse(
            UUID attemptId,
            String pollToken,
            URI authorizationUri,
            Instant expiresAt
    ) {
    }

    public record LoginExchangeRequest(@NotBlank String pollToken) {
    }

    public record LoginExchangeResponse(
            String status,
            String accessToken,
            String tokenType,
            Instant expiresAt,
            String registrationTicket
    ) {
        static LoginExchangeResponse from(DesktopLoginExchange exchange) {
            IssuedAccessToken token = exchange.accessToken();
            String status = exchange.status() == DesktopLoginStatus.NICKNAME_REQUIRED
                    ? "REGISTRATION_REQUIRED"
                    : exchange.status().name();
            return new LoginExchangeResponse(
                    status,
                    token == null ? null : token.value(),
                    token == null ? null : token.tokenType(),
                    token == null ? null : token.expiresAt(),
                    exchange.registrationToken()
            );
        }
    }

    public record RegistrationRequest(
            @NotBlank String registrationTicket,
            @NotBlank String nickname
    ) {
    }

    public record RegistrationResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt
    ) {
        static RegistrationResponse from(DesktopRegistrationResult result) {
            IssuedAccessToken token = result.accessToken();
            return new RegistrationResponse(token.value(), token.tokenType(), token.expiresAt());
        }
    }
}
