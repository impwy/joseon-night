package kr.joseonnight.adapter.security.googleoauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GoogleSubjectHasherTest {

    @Test
    void transformsTheProviderSubjectWithoutRetainingItsPlainValue() {
        GoogleSubjectHasher hasher = new GoogleSubjectHasher(
                "test-only-hmac-secret-that-is-at-least-32-characters"
        );
        String plainSubject = "google-provider-subject-1234";

        String hashed = hasher.hash(plainSubject);

        assertThat(hashed).matches("[0-9a-f]{64}");
        assertThat(hashed).doesNotContain(plainSubject);
        assertThat(hasher.hash(plainSubject)).isEqualTo(hashed);
    }
}
