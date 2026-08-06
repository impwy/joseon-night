package kr.joseonnight.adapter.security.googleoauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;

class GoogleOAuthAuthorizationRequestTest {

    @Test
    void springSecurityOwnsOpenidNonceStateAndPkceS256WithoutUserInfo() {
        GoogleOAuthConfiguration configuration = new GoogleOAuthConfiguration();
        ClientRegistrationRepository registrations = configuration.googleClientRegistrationRepository(
                "desktop-client",
                "server-only-secret",
                "http://127.0.0.1:8080/api/v1/auth/google/callback/google"
        );
        ClientRegistration google = registrations.findByRegistrationId("google");
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations,
                "/oauth2/authorization"
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/oauth2/authorization/google"
        );
        request.setServletPath("/oauth2/authorization/google");

        var authorizationRequest = resolver.resolve(request);

        assertThat(google.getScopes()).containsExactly("openid");
        assertThat(google.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(google.getProviderDetails().getUserInfoEndpoint().getUri()).isNull();
        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getState()).isNotBlank();
        assertThat(authorizationRequest.getAdditionalParameters())
                .containsKeys("nonce", "code_challenge", "code_challenge_method")
                .containsEntry("code_challenge_method", "S256");
        assertThat(authorizationRequest.getScopes()).containsExactly("openid");
        assertThat(authorizationRequest.getAuthorizationRequestUri())
                .doesNotContain("email")
                .doesNotContain("profile");
    }
}
