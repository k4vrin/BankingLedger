package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.config.OutboxPublisherProperties;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherWorkerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private OutboxMetrics outboxMetrics;

    @Test
    void publishBatchMarksEventPublishedAfterSuccessfulPublish() {
        OutboxEventEntity event = pendingEvent();
        OutboxPublisherWorker worker = worker(properties(true, 5));

        when(outboxEventRepository.findPublishableEventsForUpdate(any(), any(Pageable.class)))
                .thenReturn(List.of(event));

        OffsetDateTime beforePublish = OffsetDateTime.now();

        worker.publishBatch();

        verify(outboxEventPublisher).publish(event);
        verify(outboxMetrics).recordPublishSuccess();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isAfterOrEqualTo(beforePublish);
        assertThat(event.getNextRetryAt()).isNull();
        assertThat(event.getLastErrorMessage()).isNull();
    }

    @Test
    void publishBatchMarksEventFailedWithBoundedBackoffAfterPublishFailure() {
        OutboxEventEntity event = pendingEvent();
        OutboxPublisherWorker worker = worker(properties(true, 5));

        when(outboxEventRepository.findPublishableEventsForUpdate(any(), any(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("broker unavailable"))
                .when(outboxEventPublisher)
                .publish(event);

        OffsetDateTime beforePublish = OffsetDateTime.now();

        worker.publishBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxMetrics).recordPublishFailure();
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastErrorMessage()).isEqualTo("broker unavailable");
        assertThat(event.getNextRetryAt()).isAfterOrEqualTo(beforePublish.plusSeconds(30));
        assertThat(event.getNextRetryAt()).isBefore(OffsetDateTime.now().plusSeconds(35));
    }

    @Test
    void publishBatchDeadLettersEventAfterMaxAttemptsAndTruncatesErrorMessage() {
        OutboxEventEntity event = pendingEvent();
        event.setRetryCount(4);
        OutboxPublisherWorker worker = worker(properties(true, 5));
        String longErrorMessage = "x".repeat(1_100);

        when(outboxEventRepository.findPublishableEventsForUpdate(any(), any(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException(longErrorMessage))
                .when(outboxEventPublisher)
                .publish(event);

        worker.publishBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        verify(outboxMetrics).recordPublishFailure();
        verify(outboxMetrics).recordDeadLetter();
        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getNextRetryAt()).isNull();
        assertThat(event.getLastErrorMessage()).hasSize(1_000);
    }

    @Test
    void publishBatchDoesNothingWhenWorkerIsDisabled() {
        OutboxPublisherWorker worker = worker(properties(false, 5));

        worker.publishBatch();

        verify(outboxEventRepository, never()).findPublishableEventsForUpdate(any(), any());
        verify(outboxEventPublisher, never()).publish(any());
        verifyNoInteractions(outboxMetrics);
    }

    @Test
    void publishBatchRequestsConfiguredBatchSize() {
        OutboxPublisherWorker worker = worker(properties(true, 7));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(outboxEventRepository.findPublishableEventsForUpdate(any(), any(Pageable.class)))
                .thenReturn(List.of());

        worker.publishBatch();

        verify(outboxEventRepository).findPublishableEventsForUpdate(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(7);
    }

    private OutboxPublisherWorker worker(OutboxPublisherProperties properties) {
        return new OutboxPublisherWorker(outboxEventRepository, outboxEventPublisher, properties, outboxMetrics);
    }

    private OutboxPublisherProperties properties(boolean enabled, int batchSize) {
        return new OutboxPublisherProperties(
                enabled,
                batchSize,
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10)
        );
    }

    private OutboxEventEntity pendingEvent() {
        return OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("LEDGER_TRANSACTION")
                .aggregateId(UUID.randomUUID())
                .eventType("LedgerTransactionPosted")
                .destination("banking-ledger.ledger-events")
                .correlationId("corr-123")
                .eventPayload("{\"ok\":true}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(OffsetDateTime.now())
                .version(1)
                .build();
    }
}
