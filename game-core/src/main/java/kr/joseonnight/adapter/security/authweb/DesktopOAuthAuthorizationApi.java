package kr.joseonnight.adapter.security.authweb;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.UUID;
import java.util.Objects;
import kr.joseonnight.application.member.provided.DesktopAuthentication;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/auth/desktop")
public class DesktopOAuthAuthorizationApi {

    private final ObjectProvider<DesktopAuthentication> authenticationProvider;

    public DesktopOAuthAuthorizationApi(
            ObjectProvider<DesktopAuthentication> authenticationProvider
    ) {
        this.authenticationProvider = authenticationProvider;
    }

    @GetMapping("/authorize")
    public void authorize(
            @RequestParam @NotBlank String launchToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        DesktopAuthentication authentication = authenticationProvider.getIfAvailable();
        if (authentication == null) {
            throw new AuthenticationConfigurationException();
        }
        UUID attemptId = authentication.beginBrowserLogin(launchToken);
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        Objects.requireNonNull(request.getSession(true))
                .setAttribute(DesktopOAuthSession.ATTEMPT_ID, attemptId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.sendRedirect("/oauth2/authorization/google");
    }
}
