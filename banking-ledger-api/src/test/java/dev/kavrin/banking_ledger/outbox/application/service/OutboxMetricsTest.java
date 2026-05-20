package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final OutboxMetrics metrics = new OutboxMetrics(outboxEventRepository, meterRegistry);

    @Test
    void gaugesExposePendingDeadLetterAndOldestPendingAge() {
        when(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(3L);
        when(outboxEventRepository.countByStatus(OutboxStatus.DEAD_LETTER)).thenReturn(2L);
        when(outboxEventRepository.findOldestPendingCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusSeconds(30)));

        assertThat(meterRegistry.get("banking_ledger_outbox_pending_count").gauge().value()).isEqualTo(3.0);
        assertThat(meterRegistry.get("banking_ledger_outbox_dead_letter_count").gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.get("banking_ledger_outbox_oldest_pending_age_seconds").gauge().value())
                .isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void countersIncrementForPublishOutcomes() {
        metrics.recordPublishSuccess();
        metrics.recordPublishFailure();
        metrics.recordDeadLetter();

        assertThat(meterRegistry.get("banking_ledger_outbox_publish_success_total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("banking_ledger_outbox_publish_failure_total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("banking_ledger_outbox_dead_letter_total").counter().count()).isEqualTo(1.0);
    }
}
