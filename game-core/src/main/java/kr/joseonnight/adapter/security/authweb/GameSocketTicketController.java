package kr.joseonnight.adapter.security.authweb;

import java.time.Instant;
import kr.joseonnight.adapter.security.jwt.MemberPrincipal;
import kr.joseonnight.application.member.provided.GameSocketTicket;
import kr.joseonnight.application.member.provided.GameSocketTicketIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/game/socket-tickets")
public final class GameSocketTicketController {

    private final GameSocketTicketIssuer ticketIssuer;
    private final String webSocketUri;

    public GameSocketTicketController(
            GameSocketTicketIssuer ticketIssuer,
            @Value("${joseon-night.game.websocket-uri:ws://127.0.0.1:8081/api/v1/game/ws}")
            String webSocketUri
    ) {
        this.ticketIssuer = ticketIssuer;
        this.webSocketUri = webSocketUri;
    }

    @PostMapping
    public ResponseEntity<SocketTicketResponse> issue(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GameSocketTicket ticket = ticketIssuer.issue(principal.memberId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SocketTicketResponse(
                ticket.value(),
                webSocketUri,
                ticket.expiresAt()
        ));
    }

    public record SocketTicketResponse(String ticket, String webSocketUri, Instant expiresAt) {
    }
}
