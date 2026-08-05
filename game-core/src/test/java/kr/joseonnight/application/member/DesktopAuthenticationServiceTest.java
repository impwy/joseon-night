package kr.joseonnight.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.joseonnight.application.member.provided.DesktopLoginStart;
import kr.joseonnight.application.member.provided.MemberAuthentication;
import kr.joseonnight.application.member.provided.MemberAuthenticator;
import kr.joseonnight.application.member.provided.MemberFinder;
import kr.joseonnight.application.member.provided.MemberRegister;
import kr.joseonnight.application.member.required.AccessTokenIssuer;
import kr.joseonnight.application.member.required.DesktopAuthorizationUriFactory;
import kr.joseonnight.application.member.required.DesktopLoginAttempt;
import kr.joseonnight.application.member.required.DesktopLoginAttemptStore;
import kr.joseonnight.application.membersettings.provided.MemberSettingsFinder;
import org.junit.jupiter.api.Test;

class DesktopAuthenticationServiceTest {

    @Test
    void startCreatesSeparatePollAndOneTimeBrowserTokensForFiveMinutes() {
        InMemoryAttemptStore store = new InMemoryAttemptStore();
        DesktopAuthenticationService service = service(store, mock(MemberAuthenticator.class));

        DesktopLoginStart started = service.start();
        DesktopLoginAttempt stored = store.find(started.attemptId()).orElseThrow();
        String launchToken = started.authorizationUri().getQuery().substring("launchToken=".length());

        assertThat(started.pollToken()).hasSize(43).isNotEqualTo(launchToken);
        assertThat(launchToken).hasSize(43);
        assertThat(stored.pollTokenHash()).isEqualTo(DesktopAuthenticationService.hash(started.pollToken()));
        assertThat(stored.browserLaunchTokenHash()).isEqualTo(DesktopAuthenticationService.hash(launchToken));
        assertThat(stored.expiresAt()).isEqualTo(Instant.parse("2026-08-05T12:05:00Z"));

        assertThat(service.beginBrowserLogin(launchToken)).isEqualTo(started.attemptId());
        assertThatThrownBy(() -> service.beginBrowserLogin(launchToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("used browser login token");
    }

    @Test
    void verifiedSubjectHmacCanOnlyCompletePendingAttemptOnce() {
        InMemoryAttemptStore store = new InMemoryAttemptStore();
        MemberAuthenticator authenticator = mock(MemberAuthenticator.class);
        when(authenticator.authenticate("c".repeat(64)))
                .thenReturn(MemberAuthentication.requiresRegistration());
        DesktopAuthenticationService service = service(store, authenticator);
        DesktopLoginStart started = service.start();
        String launchToken = started.authorizationUri().getQuery().substring("launchToken=".length());
        service.beginBrowserLogin(launchToken);

        service.complete(started.attemptId(), "c".repeat(64));

        assertThat(store.find(started.attemptId()).orElseThrow().status().name())
                .isEqualTo("NICKNAME_REQUIRED");
        assertThatThrownBy(() -> service.complete(started.attemptId(), "c".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not ready");
    }

    private static DesktopAuthenticationService service(
            InMemoryAttemptStore store,
            MemberAuthenticator authenticator
    ) {
        DesktopAuthorizationUriFactory uriFactory = token -> URI.create(
                "http://127.0.0.1:8080/api/v1/auth/desktop/authorize?launchToken=" + token);
        return new DesktopAuthenticationService(
                store,
                uriFactory,
                authenticator,
                mock(MemberRegister.class),
                mock(MemberFinder.class),
                mock(MemberSettingsFinder.class),
                mock(AccessTokenIssuer.class),
                Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static final class InMemoryAttemptStore implements DesktopLoginAttemptStore {
        private final Map<UUID, DesktopLoginAttempt> attempts = new ConcurrentHashMap<>();

        @Override
        public void save(DesktopLoginAttempt attempt) { attempts.put(attempt.id(), attempt); }

        @Override
        public Optional<DesktopLoginAttempt> find(UUID attemptId) {
            return Optional.ofNullable(attempts.get(attemptId));
        }

        @Override
        public synchronized Optional<DesktopLoginAttempt> consumeBrowserLaunchTokenHash(String hash) {
            return attempts.values().stream()
                    .filter(attempt -> hash.equals(attempt.browserLaunchTokenHash()))
                    .findFirst()
                    .map(attempt -> {
                        DesktopLoginAttempt consumed = attempt.withoutBrowserLaunchToken();
                        attempts.put(consumed.id(), consumed);
                        return consumed;
                    });
        }

        @Override
        public Optional<DesktopLoginAttempt> findByRegistrationToken(String registrationToken) {
            return attempts.values().stream()
                    .filter(attempt -> registrationToken.equals(attempt.registrationToken()))
                    .findFirst();
        }

        @Override
        public Duration ttl() { return Duration.ofMinutes(5); }
    }
}
