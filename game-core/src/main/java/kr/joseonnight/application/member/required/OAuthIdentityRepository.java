package kr.joseonnight.application.member.required;

import java.util.Optional;
import kr.joseonnight.domain.member.OAuthIdentity;
import kr.joseonnight.domain.member.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {

    Optional<OAuthIdentity> findByProviderAndSubjectHmac(
            OAuthProvider provider,
            String subjectHmac
    );

    boolean existsByProviderAndSubjectHmac(OAuthProvider provider, String subjectHmac);
}
