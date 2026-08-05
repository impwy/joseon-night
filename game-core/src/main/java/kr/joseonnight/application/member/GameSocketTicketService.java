package kr.joseonnight.application.member;

import java.util.Optional;
import kr.joseonnight.application.member.provided.GameSocketTicket;
import kr.joseonnight.application.member.provided.GameSocketTicketConsumer;
import kr.joseonnight.application.member.provided.GameSocketTicketIssuer;
import kr.joseonnight.application.member.required.GameSocketTicketStore;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;

@ValidatedApplicationService
public final class GameSocketTicketService implements GameSocketTicketIssuer, GameSocketTicketConsumer {

    private final GameSocketTicketStore ticketStore;

    public GameSocketTicketService(GameSocketTicketStore ticketStore) {
        this.ticketStore = ticketStore;
    }

    @Override
    public GameSocketTicket issue(Long memberId) {
        return ticketStore.issue(memberId);
    }

    @Override
    public Optional<Long> consume(String ticket) {
        return ticketStore.consume(ticket);
    }
}
