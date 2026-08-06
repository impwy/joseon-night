package kr.joseonnight.application.playrecord.required;

import jakarta.persistence.LockModeType;
import java.util.List;
import kr.joseonnight.domain.playrecord.OutboxEvent;
import kr.joseonnight.domain.playrecord.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findTop50ByStatusOrderByOccurredAtAsc(OutboxStatus status);
}
