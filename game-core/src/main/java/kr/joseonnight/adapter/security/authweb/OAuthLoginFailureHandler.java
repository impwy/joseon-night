package kr.joseonnight.adapter.security.authweb;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import kr.joseonnight.application.member.provided.DesktopAuthentication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@RequiredArgsConstructor
@Slf4j
public final class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    private static final byte[] FAILURE_HTML = """
            <!doctype html><html lang="ko"><head><meta charset="utf-8">
            <title>조선 야행 로그인</title></head>
            <body><p>로그인을 완료하지 못했습니다. 게임에서 다시 시도해 주세요.</p></body></html>
            """.getBytes(StandardCharsets.UTF_8);

    private final DesktopAuthentication desktopAuthentication;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        HttpSession session = request.getSession(false);
        try {
            log.warn(
                    "Google OAuth login failed: errorCode={}, exception={}",
                    safeErrorCode(exception),
                    exception.getClass().getSimpleName());
            Object value = session == null ? null : session.getAttribute(DesktopOAuthSession.ATTEMPT_ID);
            if (value instanceof java.util.UUID attemptId) {
                desktopAuthentication.fail(attemptId);
            }
            OAuthLoginSuccessHandler.writeHtml(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    FAILURE_HTML
            );
        } finally {
            if (session != null) {
                session.invalidate();
            }
        }
    }

    private static String safeErrorCode(AuthenticationException exception) {
        if (!(exception instanceof OAuth2AuthenticationException oauthException)) {
            return "unknown";
        }
        String errorCode = oauthException.getError().getErrorCode();
        if (errorCode == null || errorCode.isBlank() || errorCode.length() > 64) {
            return "unknown";
        }
        for (int index = 0; index < errorCode.length(); index++) {
            char value = errorCode.charAt(index);
            if (!(value == '_' || value == '-' || value == '.' || Character.isLetterOrDigit(value))) {
                return "invalid";
            }
        }
        return errorCode;
    }
}
