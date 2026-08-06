package kr.joseonnight.adapter.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import kr.joseonnight.application.member.provided.IssuedAccessToken;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.AccessTokenIssuer;
import kr.joseonnight.domain.member.MemberRole;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

public final class JwtTokenService implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenTtl;

    public JwtTokenService(
            JwtEncoder encoder,
            JwtDecoder decoder,
            Clock clock,
            String issuer,
            String audience,
            Duration accessTokenTtl
    ) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.accessTokenTtl = Objects.requireNonNull(accessTokenTtl, "accessTokenTtl");
    }

    @Override
    public IssuedAccessToken issue(MemberView member) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(member.id().toString())
                .claim("role", member.role().name())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, "Bearer", expiresAt);
    }

    public MemberPrincipal verify(String token) {
        return verifyAccessToken(token).principal();
    }

    public VerifiedAccessToken verifyAccessToken(String token) {
        Jwt jwt = decoder.decode(token);
        String tokenIssuer = jwt.getClaimAsString("iss");
        if (!issuer.equals(tokenIssuer)) {
            throw new IllegalArgumentException("Unexpected JWT issuer");
        }
        List<String> tokenAudience = jwt.getAudience();
        if (tokenAudience == null || !tokenAudience.contains(audience)) {
            throw new IllegalArgumentException("Unexpected JWT audience");
        }
        String jwtId = jwt.getId();
        if (jwtId == null || jwtId.isBlank()) {
            throw new IllegalArgumentException("JWT ID is missing");
        }
        String subject = jwt.getSubject();
        String role = jwt.getClaimAsString("role");
        if (subject == null || role == null) {
            throw new IllegalArgumentException("JWT member claims are missing");
        }
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            throw new IllegalArgumentException("JWT expiration is missing");
        }
        return new VerifiedAccessToken(
                new MemberPrincipal(Long.valueOf(subject), MemberRole.valueOf(role)),
                jwtId,
                expiresAt
        );
    }
}
