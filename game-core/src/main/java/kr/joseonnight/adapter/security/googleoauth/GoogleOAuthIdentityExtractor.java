package kr.joseonnight.adapter.security.googleoauth;

import java.util.Objects;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class GoogleOAuthIdentityExtractor {

    private final GoogleSubjectHasher subjectHasher;

    public GoogleOAuthIdentityExtractor(GoogleSubjectHasher subjectHasher) {
        this.subjectHasher = Objects.requireNonNull(subjectHasher, "subjectHasher");
    }

    public String extractSubjectHmac(OidcUser user) {
        String subject = Objects.requireNonNull(user, "user").getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Google OpenID subject is missing");
        }
        return subjectHasher.hash(subject);
    }
}
