package kr.joseonnight.adapter.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import kr.joseonnight.application.member.required.AccessTokenBlocklist;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;
    private final AccessTokenBlocklist blocklist;

    public JwtAuthenticationFilter(
            JwtTokenService tokenService,
            AccessTokenBlocklist blocklist
    ) {
        this.tokenService = tokenService;
        this.blocklist = blocklist;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            try {
                VerifiedAccessToken verified = tokenService.verifyAccessToken(
                        authorization.substring(BEARER_PREFIX.length())
                );
                if (blocklist.isBlocked(verified.jwtId())) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                MemberPrincipal principal = verified.principal();
                var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(authority)
                );
                authentication.setDetails(verified);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
