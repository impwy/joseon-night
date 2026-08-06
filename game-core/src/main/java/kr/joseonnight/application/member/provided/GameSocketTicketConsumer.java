package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.NotBlank;
import java.util.Optional;

/** Consumes one short-lived desktop socket ticket and returns its authenticated member ID. */
public interface GameSocketTicketConsumer {

    Optional<Long> consume(@NotBlank String ticket);
}
