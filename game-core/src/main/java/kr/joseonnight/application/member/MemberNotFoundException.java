package kr.joseonnight.application.member;

public final class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(Long memberId) {
        super("Member not found: " + memberId);
    }
}
