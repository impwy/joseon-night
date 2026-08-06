package kr.joseonnight.application.member;

import java.time.Instant;
import kr.joseonnight.application.member.provided.MemberLogout;
import kr.joseonnight.application.member.required.AccessTokenBlocklist;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import lombok.RequiredArgsConstructor;

@ValidatedApplicationService
@RequiredArgsConstructor
public final class MemberLogoutService implements MemberLogout {

    private final AccessTokenBlocklist blocklist;

    @Override
    public void logout(String jwtId, Instant expiresAt) {
        blocklist.block(jwtId, expiresAt);
    }
}
