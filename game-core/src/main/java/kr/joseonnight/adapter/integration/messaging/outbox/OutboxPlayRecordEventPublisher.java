package kr.joseonnight.adapter.integration.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.playrecord.provided.PlayRecordView;
import kr.joseonnight.application.playrecord.required.PlayRecordEventPublisher;
import kr.joseonnight.application.playrecord.required.OutboxEventRepository;
import kr.joseonnight.domain.playrecord.OutboxEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class OutboxPlayRecordEventPublisher implements PlayRecordEventPublisher {

    static final String EVENT_TYPE = "game.play-completed.v1";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed JSON mapper is an injected collaborator")
    public OutboxPlayRecordEventPublisher(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publish(PlayRecordView playRecord) {
        Instant occurredAt = Instant.now(clock);
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", EVENT_TYPE);
        payload.put("gameSession", playRecord.gameSession());
        payload.put("playRecordId", playRecord.id());
        payload.put("memberId", playRecord.memberId());
        payload.put("characterId", playRecord.characterId());
        payload.put("score", playRecord.score());
        payload.put("killCount", playRecord.killCount());
        payload.put("level", playRecord.level());
        payload.put("outcome", playRecord.outcome().name());
        payload.put("durationMillis", playRecord.durationMillis());
        payload.put("finalBuild", playRecord.finalBuild());
        payload.put("endedAt", playRecord.endedAt());
        payload.put("rankingEligible", playRecord.rankingEligible());
        try {
            outboxRepository.save(OutboxEvent.pending(
                    eventId,
                    "PlayRecord",
                    playRecord.id().toString(),
                    EVENT_TYPE,
                    objectMapper.writeValueAsString(payload),
                    occurredAt
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize the play-completed event", exception);
        }
    }
}
