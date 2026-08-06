package kr.joseonnight.application.member.provided;

public record MemberAuthentication(boolean registrationRequired, MemberView member) {

    public static MemberAuthentication requiresRegistration() {
        return new MemberAuthentication(true, null);
    }

    public static MemberAuthentication authenticated(MemberView member) {
        return new MemberAuthentication(false, member);
    }
}
