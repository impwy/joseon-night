package kr.joseonnight.adapter.security.authweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import kr.joseonnight.adapter.security.jwt.MemberPrincipal;
import kr.joseonnight.adapter.security.jwt.VerifiedAccessToken;
import kr.joseonnight.application.member.provided.MemberLogout;
import kr.joseonnight.domain.member.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class LogoutApiTest {

    @Test
    void authenticatedLogoutBlocksCurrentJwtUntilItsExpiration() {
        MemberLogout memberLogout = mock(MemberLogout.class);
        LogoutApi api = new LogoutApi(memberLogout);
        Instant expiresAt = Instant.parse("2026-08-05T12:30:00Z");
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new MemberPrincipal(42L, MemberRole.PLAYER),
                null,
                List.of()
        );
        authentication.setDetails(new VerifiedAccessToken(
                new MemberPrincipal(42L, MemberRole.PLAYER),
                "jwt-id",
                expiresAt
        ));

        var response = api.logout(authentication);

        verify(memberLogout).logout("jwt-id", expiresAt);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
}
