package kr.joseonnight.adapter.integration.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.playrecord.required.OutboxEventRepository;
import kr.joseonnight.domain.playrecord.OutboxEvent;
import kr.joseonnight.domain.playrecord.OutboxStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays committed outbox events to Kafka and marks them published in the same retryable flow.
 */
@Component
public class OutboxEventRelay {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final String topic;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed Kafka client is an injected collaborator")
    public OutboxEventRelay(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            @Value("${joseon-night.messaging.play-record-topic:game.play-completed.v1}") String topic
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.topic = topic;
    }

    @Scheduled(
            fixedDelayString = "${joseon-night.messaging.outbox-fixed-delay-millis:1000}",
            initialDelayString = "${joseon-night.messaging.outbox-initial-delay-millis:10000}"
    )
    @Transactional
    public void relayPending() throws Exception {
        List<OutboxEvent> events = outboxRepository.findTop50ByStatusOrderByOccurredAtAsc(
                OutboxStatus.PENDING
        );
        for (OutboxEvent event : events) {
            kafkaTemplate.send(topic, event.getId(), event.getPayload()).get(10, TimeUnit.SECONDS);
            event.markPublished(Instant.now(clock));
        }
    }
}
