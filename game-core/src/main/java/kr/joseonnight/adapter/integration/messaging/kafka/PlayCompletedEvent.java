package kr.joseonnight.adapter.integration.messaging.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayCompletedEvent(
        String eventId,
        Long memberId,
        long durationMillis,
        int killCount,
        boolean rankingEligible
) {
}
