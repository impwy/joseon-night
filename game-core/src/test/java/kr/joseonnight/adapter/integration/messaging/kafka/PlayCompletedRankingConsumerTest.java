package kr.joseonnight.adapter.integration.messaging.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.joseonnight.application.ranking.provided.RankingUpdater;
import kr.joseonnight.application.ranking.required.LeaderboardUnavailableException;
import kr.joseonnight.application.ranking.required.RankingEventReceiptStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PlayCompletedRankingConsumerTest {

    @Test
    void ignoresAnEventIdThatWasAlreadyProjected() throws Exception {
        RankingUpdater updater = mock(RankingUpdater.class);
        RankingEventReceiptStore receipts = mock(RankingEventReceiptStore.class);
        when(receipts.isProcessed("event-1")).thenReturn(false, true);
        PlayCompletedRankingConsumer consumer = new PlayCompletedRankingConsumer(
                new ObjectMapper(),
                updater,
                receipts
        );
        String payload = """
                {"eventId":"event-1","memberId":7,"durationMillis":300000,
                 "killCount":25,"rankingEligible":true}
                """;

        consumer.consume(payload);
        consumer.consume(payload);

        verify(updater).update(any());
        verify(receipts).markProcessed("event-1");
    }

    @Test
    void failedProjectionDoesNotRecordAReceiptSoKafkaCanRetry() {
        RankingUpdater updater = mock(RankingUpdater.class);
        RankingEventReceiptStore receipts = mock(RankingEventReceiptStore.class);
        doThrow(new LeaderboardUnavailableException(
                "Redis unavailable",
                new IllegalStateException("offline")
        )).when(updater).update(any());
        PlayCompletedRankingConsumer consumer = new PlayCompletedRankingConsumer(
                new ObjectMapper(),
                updater,
                receipts
        );

        Assertions.assertThrows(LeaderboardUnavailableException.class, () -> consumer.consume("""
                {"eventId":"event-2","memberId":8,"durationMillis":250000,
                 "killCount":20,"rankingEligible":true}
                """));

        verify(receipts, never()).markProcessed("event-2");
    }
}
