package kr.joseonnight.application.membersettings;

public final class MemberSettingsNotFoundException extends RuntimeException {

    public MemberSettingsNotFoundException(Long memberId) {
        super("Member settings not found: " + memberId);
    }
}
