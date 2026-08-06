package kr.joseonnight.adapter.security.jwt;

import org.springframework.beans.factory.ObjectProvider;
import kr.joseonnight.application.member.required.AccessTokenBlocklist;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfiguration {

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtTokenService> tokenServiceProvider,
            AccessTokenBlocklist blocklist
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"message\":\"Authentication is required\"}");
                }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/desktop/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rankings/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated());
        JwtTokenService tokenService = tokenServiceProvider.getIfAvailable();
        if (tokenService != null) {
            http.addFilterBefore(
                    new JwtAuthenticationFilter(tokenService, blocklist),
                    UsernamePasswordAuthenticationFilter.class
            );
        }
        return http.build();
    }
}
