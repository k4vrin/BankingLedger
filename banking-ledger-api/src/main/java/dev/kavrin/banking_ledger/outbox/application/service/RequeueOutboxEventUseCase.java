package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.audit.application.command.WriteAuditEventCommand;
import dev.kavrin.banking_ledger.audit.application.service.AuditEventWriter;
import dev.kavrin.banking_ledger.audit.domain.model.AuditChannel;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.audit.domain.model.OutboxEventRequeuedAuditPayload;
import dev.kavrin.banking_ledger.outbox.api.dto.OutboxEventResponse;
import dev.kavrin.banking_ledger.outbox.application.command.RequeueOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequeueOutboxEventUseCase {

    private final OutboxEventRepository outboxEventRepository;
    private final AuditEventWriter auditEventWriter;
    private final OutboxResponseMapper mapper;

    @Transactional
    public OutboxEventResponse handle(RequeueOutboxEventCommand command) {
        var event = outboxEventRepository.findById(command.eventId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Outbox event not found: " + command.eventId(),
                        "Outbox event not found."
                ));

        var previousStatus = event.getStatus();
        validateCanRequeue(previousStatus, command.force());

        event.markPendingForReplay(command.resetRetryCount());

        auditEventWriter.write(new WriteAuditEventCommand(
                AuditEventType.OUTBOX_EVENT_REQUEUED,
                AuditEntityType.OUTBOX_EVENT,
                event.getId(),
                command.actorType(),
                command.actorRole(),
                command.actorId(),
                command.correlationId(),
                AuditChannel.API,
                new OutboxEventRequeuedAuditPayload(
                        event.getId(),
                        previousStatus.name(),
                        event.getStatus().name(),
                        command.force(),
                        command.resetRetryCount(),
                        event.getRetryCount()
                )
        ));

        return mapper.toResponse(event);
    }

    private void validateCanRequeue(OutboxStatus status, boolean force) {
        if (status == OutboxStatus.FAILED || status == OutboxStatus.DEAD_LETTER) {
            return;
        }
        if (status == OutboxStatus.PUBLISHED && force) {
            return;
        }
        throw new ConflictException(
                ApiErrorCode.Business.OUTBOX_EVENT_NOT_REQUEUEABLE,
                "Outbox event status cannot be requeued without force: " + status,
                "Outbox event cannot be requeued from its current status."
        );
    }
}
