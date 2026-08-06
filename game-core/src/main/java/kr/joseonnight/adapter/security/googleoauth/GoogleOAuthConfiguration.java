package kr.joseonnight.adapter.security.googleoauth;

import kr.joseonnight.application.member.required.DesktopAuthorizationUriFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${joseon-night.security.google.client-id:}' != '' "
        + "&& '${joseon-night.security.google.client-secret:}' != '' "
        + "&& '${joseon-night.security.google.subject-hmac-secret:}' != ''")
public class GoogleOAuthConfiguration {

    @Bean
    GoogleSubjectHasher googleSubjectHasher(
            @Value("${joseon-night.security.google.subject-hmac-secret}") String secret
    ) {
        return new GoogleSubjectHasher(secret);
    }

    @Bean
    GoogleOAuthIdentityExtractor googleOAuthIdentityExtractor(GoogleSubjectHasher subjectHasher) {
        return new GoogleOAuthIdentityExtractor(subjectHasher);
    }

    @Bean
    ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${joseon-night.security.google.client-id}") String clientId,
            @Value("${joseon-night.security.google.client-secret}") String clientSecret,
            @Value("${joseon-night.security.google.redirect-uri}") String redirectUri
    ) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Google")
                .clientSettings(ClientRegistration.ClientSettings.builder()
                        .requireProofKey(true)
                        .build())
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    DesktopAuthorizationUriFactory desktopAuthorizationUriFactory(
            @Value("${joseon-night.security.google.desktop-authorization-uri:"
                    + "http://127.0.0.1:8080/api/v1/auth/desktop/authorize}") String authorizationUri
    ) {
        return browserLaunchToken -> UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("launchToken", browserLaunchToken)
                .build()
                .encode()
                .toUri();
    }
}
