package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public interface MemberLogout {

    void logout(@NotBlank String jwtId, @NotNull Instant expiresAt);
}
