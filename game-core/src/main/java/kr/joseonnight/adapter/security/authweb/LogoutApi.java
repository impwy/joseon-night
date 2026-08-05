package kr.joseonnight.adapter.security.authweb;

import kr.joseonnight.adapter.security.jwt.VerifiedAccessToken;
import kr.joseonnight.application.member.provided.MemberLogout;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class LogoutApi {

    private final MemberLogout memberLogout;

    public LogoutApi(MemberLogout memberLogout) {
        this.memberLogout = memberLogout;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (authentication == null
                || !(authentication.getDetails() instanceof VerifiedAccessToken token)) {
            return ResponseEntity.status(401).build();
        }
        memberLogout.logout(token.jwtId(), token.expiresAt());
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
