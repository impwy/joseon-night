package kr.joseonnight.adapter.security.authweb;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import kr.joseonnight.adapter.security.googleoauth.GoogleOAuthIdentityExtractor;
import kr.joseonnight.application.member.provided.DesktopAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@RequiredArgsConstructor
public final class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final byte[] SUCCESS_HTML = """
            <!doctype html><html lang="ko"><head><meta charset="utf-8">
            <title>조선 야행 로그인</title></head>
            <body><p>로그인이 완료되었습니다. 이 창을 닫고 게임으로 돌아가세요.</p></body></html>
            """.getBytes(StandardCharsets.UTF_8);

    private final DesktopAuthentication desktopAuthentication;
    private final GoogleOAuthIdentityExtractor identityExtractor;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        try {
            UUID attemptId = requireAttemptId(session);
            if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
                throw new ServletException("OpenID authentication principal is missing");
            }
            desktopAuthentication.complete(attemptId, identityExtractor.extractSubjectHmac(oidcUser));
            writeHtml(response, HttpServletResponse.SC_OK, SUCCESS_HTML);
        } finally {
            if (session != null) {
                session.invalidate();
            }
        }
    }

    static UUID requireAttemptId(HttpSession session) throws ServletException {
        Object value = session == null ? null : session.getAttribute(DesktopOAuthSession.ATTEMPT_ID);
        if (value instanceof UUID attemptId) {
            return attemptId;
        }
        throw new ServletException("Desktop login correlation is missing");
    }

    static void writeHtml(HttpServletResponse response, int status, byte[] content) throws IOException {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }
}
