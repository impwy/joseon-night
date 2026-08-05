package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.Positive;

public interface MemberFinder {

    MemberView find(@Positive Long memberId);
}
