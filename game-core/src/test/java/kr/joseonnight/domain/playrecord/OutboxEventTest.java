package kr.joseonnight.domain.playrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-05T12:05:00Z");

    @Test
    void pendingEventCanBePublishedOnce() {
        OutboxEvent event = pendingEvent();
        Instant publishedAt = OCCURRED_AT.plusSeconds(1);

        event.markPublished(publishedAt);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
        assertThatThrownBy(() -> event.markPublished(publishedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void publishedTimeCannotPrecedeOccurrence() {
        OutboxEvent event = pendingEvent();

        assertThatThrownBy(() -> event.markPublished(OCCURRED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();
    }

    private static OutboxEvent pendingEvent() {
        return OutboxEvent.pending(
                "24e0615e-43c9-4299-9233-196c4db7868f",
                "PlayRecord",
                "10",
                "game.play-completed.v1",
                "{}",
                OCCURRED_AT
        );
    }
}
