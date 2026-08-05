package kr.joseonnight.adapter.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.joseonnight.application.member.provided.IssuedAccessToken;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.domain.member.MemberRole;
import kr.joseonnight.domain.member.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenServiceTest {

    @Test
    void issuesAndVerifiesAnRs256TokenWithThirtyMinuteLifetime() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("joseon-night-test"));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JwtTokenService service = new JwtTokenService(
                encoder,
                decoder,
                Clock.fixed(now, ZoneOffset.UTC),
                "joseon-night-test",
                "joseon-night-desktop",
                Duration.ofMinutes(30)
        );
        MemberView member = new MemberView(
                42L,
                MemberRole.PLAYER,
                MemberStatus.ACTIVE,
                now.minusSeconds(60),
                now
        );

        IssuedAccessToken issued = service.issue(member);
        MemberPrincipal principal = service.verify(issued.value());

        assertThat(issued.tokenType()).isEqualTo("Bearer");
        assertThat(issued.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(principal).isEqualTo(new MemberPrincipal(42L, MemberRole.PLAYER));
        var decoded = decoder.decode(issued.value());
        assertThat(decoded.getAudience()).containsExactly("joseon-night-desktop");
        assertThat(decoded.getId()).isNotBlank();
    }
}
