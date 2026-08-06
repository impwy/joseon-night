package kr.joseonnight.adapter.security.authweb;

import kr.joseonnight.adapter.security.googleoauth.GoogleOAuthIdentityExtractor;
import kr.joseonnight.adapter.security.googleoauth.IdTokenOnlyOidcUserService;
import kr.joseonnight.application.member.provided.DesktopAuthentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${joseon-night.security.google.client-id:}' != '' "
        + "&& '${joseon-night.security.google.client-secret:}' != '' "
        + "&& '${joseon-night.security.google.subject-hmac-secret:}' != '' "
        + "&& '${joseon-night.security.jwt.public-key-base64:}' != '' "
        + "&& '${joseon-night.security.jwt.private-key-base64:}' != ''")
public class SecurityWebConfiguration {

    @Bean
    OAuthLoginSuccessHandler oauthLoginSuccessHandler(
            DesktopAuthentication desktopAuthentication,
            GoogleOAuthIdentityExtractor identityExtractor
    ) {
        return new OAuthLoginSuccessHandler(desktopAuthentication, identityExtractor);
    }

    @Bean
    OAuthLoginFailureHandler oauthLoginFailureHandler(
            DesktopAuthentication desktopAuthentication
    ) {
        return new OAuthLoginFailureHandler(desktopAuthentication);
    }

    @Bean
    OAuth2AuthorizedClientRepository nonPersistingOAuth2AuthorizedClientRepository() {
        return new NonPersistingOAuth2AuthorizedClientRepository();
    }

    @Bean
    @Order(1)
    SecurityFilterChain oauthBrowserSecurityFilterChain(
            HttpSecurity http,
            OAuthLoginSuccessHandler successHandler,
            OAuthLoginFailureHandler failureHandler,
            OAuth2AuthorizedClientRepository authorizedClientRepository
    ) throws Exception {
        http
                .securityMatcher(
                        "/api/v1/auth/desktop/authorize",
                        "/oauth2/authorization/**",
                        "/api/v1/auth/google/callback/**")
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.securityContextRepository(
                        new RequestAttributeSecurityContextRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .authorizedClientRepository(authorizedClientRepository)
                        .redirectionEndpoint(endpoint -> endpoint.baseUri(
                                "/api/v1/auth/google/callback/*"))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(
                                new IdTokenOnlyOidcUserService()))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }
}
