package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.Positive;

public interface GameSocketTicketIssuer {

    GameSocketTicket issue(@Positive Long memberId);
}
