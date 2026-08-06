package kr.joseonnight.application.member.required;

import kr.joseonnight.application.member.provided.IssuedAccessToken;
import kr.joseonnight.application.member.provided.MemberView;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(MemberView member);
}
