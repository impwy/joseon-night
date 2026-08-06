package kr.joseonnight.adapter.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.joseonnight.application.member.required.AccessTokenBlocklist;
import kr.joseonnight.domain.member.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blockedJwtIdNeverCreatesAnAuthenticatedSecurityContext() throws Exception {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        AccessTokenBlocklist blocklist = mock(AccessTokenBlocklist.class);
        when(tokenService.verifyAccessToken("token")).thenReturn(new VerifiedAccessToken(
                new MemberPrincipal(42L, MemberRole.PLAYER),
                "blocked-jti",
                Instant.parse("2026-08-05T12:30:00Z")
        ));
        when(blocklist.isBlocked("blocked-jti")).thenReturn(true);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService, blocklist);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> continued.set(true)
        );

        assertThat(continued).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
