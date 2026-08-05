package kr.joseonnight.adapter.security.authweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import kr.joseonnight.adapter.security.googleoauth.GoogleOAuthIdentityExtractor;
import kr.joseonnight.adapter.security.googleoauth.GoogleSubjectHasher;
import kr.joseonnight.application.member.provided.DesktopAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class OAuthLoginHandlerTest {

    @Test
    void successPassesOnlyHmacSubjectAndInvalidatesBrowserSession() throws Exception {
        DesktopAuthentication desktop = mock(DesktopAuthentication.class);
        GoogleSubjectHasher hasher = new GoogleSubjectHasher("hmac-secret-that-is-at-least-32-characters");
        OAuthLoginSuccessHandler handler = new OAuthLoginSuccessHandler(
                desktop,
                new GoogleOAuthIdentityExtractor(hasher)
        );
        UUID attemptId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithAttempt(attemptId);
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String rawSubject = "google-private-subject";
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        OidcIdToken idToken = new OidcIdToken(
                "not-persisted-id-token",
                now,
                now.plusSeconds(60),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "sub", rawSubject,
                        "aud", List.of("desktop-client"))
        );
        var user = new DefaultOidcUser(
                Set.of(new OidcUserAuthority(idToken)),
                idToken
        );
        var authentication = new OAuth2AuthenticationToken(
                user,
                user.getAuthorities(),
                "google"
        );

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(desktop).complete(eq(attemptId), eq(hasher.hash(rawSubject)));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getContentAsString()).doesNotContain(rawSubject);
    }

    @Test
    void failureStoresOnlyGenericStateAndInvalidatesBrowserSession() throws Exception {
        DesktopAuthentication desktop = mock(DesktopAuthentication.class);
        OAuthLoginFailureHandler handler = new OAuthLoginFailureHandler(desktop);
        UUID attemptId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithAttempt(attemptId);
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "private-provider-detail")
        );

        verify(desktop).fail(attemptId);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("private-provider-detail");
        assertThat(session.isInvalid()).isTrue();
    }

    private static MockHttpServletRequest requestWithAttempt(UUID attemptId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Objects.requireNonNull(request.getSession(true))
                .setAttribute(DesktopOAuthSession.ATTEMPT_ID, attemptId);
        return request;
    }
}
