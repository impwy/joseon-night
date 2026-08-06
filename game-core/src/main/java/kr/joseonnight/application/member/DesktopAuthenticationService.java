package kr.joseonnight.application.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import kr.joseonnight.application.member.provided.AuthenticatedMember;
import kr.joseonnight.application.member.provided.DesktopAuthentication;
import kr.joseonnight.application.member.provided.DesktopLoginExchange;
import kr.joseonnight.application.member.provided.DesktopLoginStart;
import kr.joseonnight.application.member.provided.DesktopRegistrationResult;
import kr.joseonnight.application.member.provided.IssuedAccessToken;
import kr.joseonnight.application.member.provided.MemberAuthentication;
import kr.joseonnight.application.member.provided.MemberAuthenticator;
import kr.joseonnight.application.member.provided.MemberFinder;
import kr.joseonnight.application.member.provided.MemberRegister;
import kr.joseonnight.application.member.provided.MemberRegistrationInfo;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.AccessTokenIssuer;
import kr.joseonnight.application.member.required.DesktopAuthorizationUriFactory;
import kr.joseonnight.application.member.required.DesktopLoginAttempt;
import kr.joseonnight.application.member.required.DesktopLoginAttemptStore;
import kr.joseonnight.application.member.required.DesktopLoginStatus;
import kr.joseonnight.application.membersettings.provided.MemberSettingsFinder;
import kr.joseonnight.application.membersettings.provided.MemberSettingsView;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@ValidatedApplicationService
@ConditionalOnBean({DesktopAuthorizationUriFactory.class, AccessTokenIssuer.class})
@RequiredArgsConstructor
public final class DesktopAuthenticationService implements DesktopAuthentication {

    private final DesktopLoginAttemptStore attemptStore;
    private final DesktopAuthorizationUriFactory authorizationUriFactory;
    private final MemberAuthenticator memberAuthenticator;
    private final MemberRegister memberRegister;
    private final MemberFinder memberFinder;
    private final MemberSettingsFinder settingsFinder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Override
    public DesktopLoginStart start() {
        UUID attemptId = UUID.randomUUID();
        String pollToken = randomToken();
        String browserLaunchToken = randomToken();
        Instant expiresAt = Instant.now(clock).plus(attemptStore.ttl());
        attemptStore.save(new DesktopLoginAttempt(
                attemptId,
                hash(pollToken),
                hash(browserLaunchToken),
                expiresAt,
                DesktopLoginStatus.PENDING,
                null,
                null,
                null,
                null
        ));
        return new DesktopLoginStart(
                attemptId,
                pollToken,
                authorizationUriFactory.create(browserLaunchToken),
                expiresAt
        );
    }

    @Override
    public UUID beginBrowserLogin(String browserLaunchToken) {
        return attemptStore.consumeBrowserLaunchTokenHash(hash(browserLaunchToken))
                .filter(attempt -> attempt.status() == DesktopLoginStatus.PENDING)
                .map(DesktopLoginAttempt::id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown, expired, or used browser login token"));
    }

    @Override
    public void complete(UUID attemptId, String providerSubjectHmac) {
        DesktopLoginAttempt attempt = pendingAttempt(attemptId);
        MemberAuthentication authentication = memberAuthenticator.authenticate(providerSubjectHmac);
        if (authentication.registrationRequired()) {
            attemptStore.save(attempt.withRegistration(providerSubjectHmac, randomToken()));
            return;
        }
        attemptStore.save(authenticated(attempt, authentication.member()));
    }

    @Override
    public void fail(UUID attemptId) {
        attemptStore.find(attemptId)
                .filter(attempt -> attempt.status() == DesktopLoginStatus.PENDING)
                .ifPresent(attempt -> attemptStore.save(attempt.withFailure()));
    }

    @Override
    public synchronized DesktopLoginExchange exchange(UUID attemptId, String pollToken) {
        DesktopLoginAttempt attempt = attemptStore.find(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or expired login attempt"));
        if (!constantTimeEquals(attempt.pollTokenHash(), hash(pollToken))) {
            throw new IllegalArgumentException("Invalid login polling token");
        }
        AuthenticatedMember member = attempt.memberId() == null
                ? null
                : new AuthenticatedMember(attempt.memberId(), attempt.nickname());
        IssuedAccessToken accessToken = null;
        if (attempt.status() == DesktopLoginStatus.AUTHENTICATED) {
            accessToken = accessTokenIssuer.issue(memberFinder.find(attempt.memberId()));
            attemptStore.save(attempt.withExchanged());
        }
        return new DesktopLoginExchange(
                attempt.status(), attempt.registrationToken(), accessToken, member
        );
    }

    @Override
    public DesktopRegistrationResult register(String registrationToken, String nickname) {
        DesktopLoginAttempt attempt = attemptStore.findByRegistrationToken(registrationToken)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown or expired registration token"));
        if (attempt.status() != DesktopLoginStatus.NICKNAME_REQUIRED) {
            throw new IllegalArgumentException("Registration token has already been used");
        }
        MemberView member = memberRegister.register(new MemberRegistrationInfo(
                attempt.providerSubjectHmac(), nickname
        ));
        DesktopLoginAttempt authenticated = authenticated(attempt, member);
        IssuedAccessToken accessToken = accessTokenIssuer.issue(member);
        attemptStore.save(authenticated.withExchanged());
        return new DesktopRegistrationResult(
                accessToken,
                new AuthenticatedMember(authenticated.memberId(), authenticated.nickname())
        );
    }

    private DesktopLoginAttempt pendingAttempt(UUID attemptId) {
        DesktopLoginAttempt attempt = attemptStore.find(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or expired login attempt"));
        if (attempt.status() != DesktopLoginStatus.PENDING
                || attempt.browserLaunchTokenHash() != null) {
            throw new IllegalArgumentException("Login attempt is not ready for completion");
        }
        return attempt;
    }

    private DesktopLoginAttempt authenticated(DesktopLoginAttempt attempt, MemberView member) {
        MemberSettingsView settings = settingsFinder.find(member.id());
        return attempt.withAuthentication(member.id(), settings.nickname());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
