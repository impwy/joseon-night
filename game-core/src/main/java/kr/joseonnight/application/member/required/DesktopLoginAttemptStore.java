package kr.joseonnight.application.member.required;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface DesktopLoginAttemptStore {

    void save(DesktopLoginAttempt attempt);

    Optional<DesktopLoginAttempt> find(UUID attemptId);

    Optional<DesktopLoginAttempt> consumeBrowserLaunchTokenHash(String browserLaunchTokenHash);

    Optional<DesktopLoginAttempt> findByRegistrationToken(String registrationToken);

    Duration ttl();
}
