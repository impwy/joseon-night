package kr.joseonnight.adapter.security.jwt;

import kr.joseonnight.domain.member.MemberRole;

public record MemberPrincipal(Long memberId, MemberRole role) {
}
