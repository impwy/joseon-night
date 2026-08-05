package kr.joseonnight.adapter.security.googleoauth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class GoogleSubjectHasher {

    private final SecretKeySpec secretKey;

    public GoogleSubjectHasher(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Google subject HMAC secret must have at least 32 characters");
        }
        secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String hash(String subject) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate the provider subject HMAC", exception);
        }
    }
}
