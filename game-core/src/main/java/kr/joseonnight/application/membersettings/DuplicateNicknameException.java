package kr.joseonnight.application.membersettings;

public final class DuplicateNicknameException extends RuntimeException {

    public DuplicateNicknameException(String nickname) {
        super("Nickname is already in use: " + nickname);
    }
}
