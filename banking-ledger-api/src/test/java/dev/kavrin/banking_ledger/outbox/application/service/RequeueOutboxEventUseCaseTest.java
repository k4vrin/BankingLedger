package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.audit.application.command.WriteAuditEventCommand;
import dev.kavrin.banking_ledger.audit.application.service.AuditEventWriter;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.outbox.application.command.RequeueOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RequeueOutboxEventUseCaseTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final AuditEventWriter auditEventWriter = mock(AuditEventWriter.class);
    private final RequeueOutboxEventUseCase useCase = new RequeueOutboxEventUseCase(
            outboxEventRepository,
            auditEventWriter,
            new OutboxResponseMapper()
    );

    @Test
    void requeuesFailedEventAndPreservesRetryCountByDefault() {
        var event = event(OutboxStatus.FAILED);
        event.setRetryCount(3);
        event.setNextRetryAt(OffsetDateTime.now().plusMinutes(5));
        event.setLastErrorMessage("broker unavailable");
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        var response = useCase.handle(command(event.getId(), false, false));

        assertThat(response.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(response.retryCount()).isEqualTo(3);
        assertThat(event.getNextRetryAt()).isNull();
        assertThat(event.getLastErrorMessage()).isNull();

        ArgumentCaptor<WriteAuditEventCommand> auditCaptor = ArgumentCaptor.forClass(WriteAuditEventCommand.class);
        verify(auditEventWriter).write(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo(AuditEventType.OUTBOX_EVENT_REQUEUED);
        assertThat(auditCaptor.getValue().entityType()).isEqualTo(AuditEntityType.OUTBOX_EVENT);
        assertThat(auditCaptor.getValue().correlationId()).isEqualTo("corr-requeue");
    }

    @Test
    void requeuesDeadLetterAndResetsRetryCountWhenRequested() {
        var event = event(OutboxStatus.DEAD_LETTER);
        event.setRetryCount(5);
        event.setLastErrorMessage("poison");
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        var response = useCase.handle(command(event.getId(), false, true));

        assertThat(response.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(response.retryCount()).isZero();
    }

    @Test
    void rejectsPublishedEventWithoutForce() {
        var event = event(OutboxStatus.PUBLISHED);
        event.setPublishedAt(OffsetDateTime.now());
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.handle(command(event.getId(), false, false)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(auditEventWriter);
    }

    @Test
    void forceRequeuesPublishedEventAndClearsPublishedAt() {
        var event = event(OutboxStatus.PUBLISHED);
        event.setPublishedAt(OffsetDateTime.now());
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        var response = useCase.handle(command(event.getId(), true, false));

        assertThat(response.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(response.publishedAt()).isNull();
        verify(auditEventWriter).write(any(WriteAuditEventCommand.class));
    }

    @Test
    void rejectsPendingEvent() {
        var event = event(OutboxStatus.PENDING);
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.handle(command(event.getId(), false, false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownEventReturnsNotFound() {
        var eventId = UUID.randomUUID();
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.handle(command(eventId, false, false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private RequeueOutboxEventCommand command(UUID eventId, boolean force, boolean resetRetryCount) {
        return new RequeueOutboxEventCommand(
                eventId,
                force,
                resetRetryCount,
                AuditActorType.EMPLOYEE,
                AuditActorRole.OPS_ADMIN,
                "ops-1",
                "corr-requeue"
        );
    }

    private OutboxEventEntity event(OutboxStatus status) {
        return OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("LEDGER_TRANSACTION")
                .aggregateId(UUID.randomUUID())
                .eventType("LedgerTransactionPosted")
                .destination("banking-ledger.ledger-events")
                .correlationId("corr-original")
                .eventPayload("{\"ok\":true}")
                .status(status)
                .retryCount(0)
                .createdAt(OffsetDateTime.now())
                .version(1)
                .build();
    }
}
