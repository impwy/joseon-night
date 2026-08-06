package kr.joseonnight.application.member;

public final class DuplicateMemberException extends RuntimeException {

    public DuplicateMemberException() {
        super("The external identity is already registered");
    }
}
