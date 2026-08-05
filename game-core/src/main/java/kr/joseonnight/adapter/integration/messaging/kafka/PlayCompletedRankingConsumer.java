package kr.joseonnight.adapter.integration.messaging.kafka;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.ranking.provided.RankingUpdate;
import kr.joseonnight.application.ranking.provided.RankingUpdater;
import kr.joseonnight.application.ranking.required.RankingEventReceiptStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class PlayCompletedRankingConsumer {

    private final ObjectMapper objectMapper;
    private final RankingUpdater rankingUpdater;
    private final RankingEventReceiptStore receiptStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed JSON mapper is an injected collaborator")
    public PlayCompletedRankingConsumer(
            ObjectMapper objectMapper,
            RankingUpdater rankingUpdater,
            RankingEventReceiptStore receiptStore
    ) {
        this.objectMapper = objectMapper;
        this.rankingUpdater = rankingUpdater;
        this.receiptStore = receiptStore;
    }

    @KafkaListener(
            topics = "${joseon-night.messaging.play-record-topic:game.play-completed.v1}",
            groupId = "${spring.kafka.consumer.group-id:joseon-night-game-core}"
    )
    public void consume(String payload) throws Exception {
        PlayCompletedEvent event = objectMapper.readValue(payload, PlayCompletedEvent.class);
        if (receiptStore.isProcessed(event.eventId())) {
            return;
        }
        rankingUpdater.update(new RankingUpdate(
                event.eventId(),
                event.memberId(),
                event.durationMillis(),
                event.killCount(),
                event.rankingEligible()
        ));
        receiptStore.markProcessed(event.eventId());
    }
}
