package kr.joseonnight.adapter.security.jwt;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${joseon-night.security.jwt.public-key-base64:}' != '' "
        + "&& '${joseon-night.security.jwt.private-key-base64:}' != ''")
public class JwtKeyConfiguration {

    @Bean
    JwtTokenService jwtTokenService(
            Clock clock,
            @Value("${joseon-night.security.jwt.issuer:joseon-night}") String issuer,
            @Value("${joseon-night.security.jwt.audience:joseon-night-desktop}") String audience,
            @Value("${joseon-night.security.jwt.public-key-base64}") String publicKeyBase64,
            @Value("${joseon-night.security.jwt.private-key-base64}") String privateKeyBase64,
            @Value("${joseon-night.security.jwt.access-token-seconds:1800}") long accessTokenSeconds
    ) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
        );
        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64))
        );
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(rsaKey));
        JwtEncoder encoder = new NimbusJwtEncoder(keySource);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        var audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                audiences -> audiences != null && audiences.contains(audience)
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator
        ));
        return new JwtTokenService(
                encoder,
                decoder,
                clock,
                issuer,
                audience,
                Duration.ofSeconds(accessTokenSeconds)
        );
    }
}
