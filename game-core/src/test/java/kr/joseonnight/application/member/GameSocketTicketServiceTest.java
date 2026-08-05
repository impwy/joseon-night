package kr.joseonnight.application.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import kr.joseonnight.application.member.provided.GameSocketTicket;
import kr.joseonnight.application.member.required.GameSocketTicketStore;
import org.junit.jupiter.api.Test;

class GameSocketTicketServiceTest {

    @Test
    void exposesTicketIssueAndConsumptionThroughProvidedPorts() {
        GameSocketTicket expected = new GameSocketTicket("ticket-1", Instant.parse("2099-01-01T00:00:00Z"));
        GameSocketTicketService service = new GameSocketTicketService(new GameSocketTicketStore() {
            @Override
            public GameSocketTicket issue(Long memberId) {
                assertEquals(7L, memberId);
                return expected;
            }

            @Override
            public Optional<Long> consume(String ticket) {
                return "ticket-1".equals(ticket) ? Optional.of(7L) : Optional.empty();
            }
        });

        assertEquals(expected, service.issue(7L));
        assertEquals(Optional.of(7L), service.consume("ticket-1"));
        assertTrue(service.consume("used-ticket").isEmpty());
    }
}
