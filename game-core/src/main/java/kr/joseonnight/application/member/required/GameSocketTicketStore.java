package kr.joseonnight.application.member.required;

import java.util.Optional;
import kr.joseonnight.application.member.provided.GameSocketTicket;

public interface GameSocketTicketStore {

    GameSocketTicket issue(Long memberId);

    Optional<Long> consume(String ticket);
}
