package kr.joseonnight.application.member;

import java.util.Optional;
import kr.joseonnight.application.member.provided.GameSocketTicket;
import kr.joseonnight.application.member.provided.GameSocketTicketConsumer;
import kr.joseonnight.application.member.provided.GameSocketTicketIssuer;
import kr.joseonnight.application.member.required.GameSocketTicketStore;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import lombok.RequiredArgsConstructor;

@ValidatedApplicationService
@RequiredArgsConstructor
public final class GameSocketTicketService implements GameSocketTicketIssuer, GameSocketTicketConsumer {

    private final GameSocketTicketStore ticketStore;

    @Override
    public GameSocketTicket issue(Long memberId) {
        return ticketStore.issue(memberId);
    }

    @Override
    public Optional<Long> consume(String ticket) {
        return ticketStore.consume(ticket);
    }
}
