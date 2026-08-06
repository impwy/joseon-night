package kr.joseonnight.application.member.provided;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface MemberRegister {

    MemberView register(@Valid @NotNull MemberRegistrationInfo registrationInfo);
}
