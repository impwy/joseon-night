package kr.joseonnight.application.member;

import java.time.Instant;
import kr.joseonnight.application.member.provided.MemberLogout;
import kr.joseonnight.application.member.required.AccessTokenBlocklist;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;

@ValidatedApplicationService
public final class MemberLogoutService implements MemberLogout {

    private final AccessTokenBlocklist blocklist;

    public MemberLogoutService(AccessTokenBlocklist blocklist) {
        this.blocklist = blocklist;
    }

    @Override
    public void logout(String jwtId, Instant expiresAt) {
        blocklist.block(jwtId, expiresAt);
    }
}
