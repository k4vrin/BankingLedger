package dev.kavrin.banking_ledger.adjustment.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.adjustment.api.dto.AdjustmentResponse;
import dev.kavrin.banking_ledger.adjustment.application.command.CreateAdjustmentCommand;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentStatus;
import dev.kavrin.banking_ledger.adjustment.domain.policy.AdjustmentValidationPolicy;
import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestEntity;
import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestRepository;
import dev.kavrin.banking_ledger.adjustment.persistence.mapper.AdjustmentResponseMapper;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.service.PostLedgerTransactionUseCase;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.outbox.OutboxAggregateType;
import dev.kavrin.banking_ledger.outbox.OutboxDestination;
import dev.kavrin.banking_ledger.outbox.OutboxEventType;
import dev.kavrin.banking_ledger.outbox.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreateAdjustmentUseCase {

    private final AdjustmentRequestRepository adjustmentRequestRepository;
    private final PostLedgerTransactionUseCase postLedgerTransactionUseCase;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final AdjustmentResponseMapper adjustmentResponseMapper;
    private final AuditEventRepository auditEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdjustmentResponse handle(CreateAdjustmentCommand command) {
        AdjustmentValidationPolicy.validateRequest(command);

        AdjustmentRequestEntity adjustment = adjustmentRequestRepository.save(
                AdjustmentRequestEntity.builder()
                        .reasonCode(command.reasonCode())
                        .reasonDetail(command.reasonDetail())
                        .requestedByActorType(command.actorType())
                        .requestedByActorRole(command.actorRole())
                        .requestedByActorId(command.actorId())
                        .correlationId(command.correlationId())
                        .status(AdjustmentStatus.PENDING)
                        .requestedAt(OffsetDateTime.now())
                        .build()
        );

        PostLedgerTransactionCommand ledgerCommand = new PostLedgerTransactionCommand(
                adjustmentExternalReference(adjustment),
                LedgerTransactionType.ADJUSTMENT,
                command.currencyCode(),
                command.amountMinor(),
                adjustmentDescription(command),
                command.actorType().toAuditActorType(),
                command.correlationId(),
                command.postingLines()
        );

        var postedLedgerTransaction = postLedgerTransactionUseCase.handle(ledgerCommand);

        adjustment.setLedgerTransaction(
                ledgerTransactionRepository.getReferenceById(
                        postedLedgerTransaction.ledgerTransactionId()
                )
        );
        adjustment.setStatus(AdjustmentStatus.COMPLETED);
        adjustment.setCompletedAt(postedLedgerTransaction.postedAt());

        AdjustmentRequestEntity completedAdjustment =
                adjustmentRequestRepository.save(adjustment);

        auditEventRepository.save(auditEvent(command, completedAdjustment));
        outboxEventRepository.save(outboxEvent(command, completedAdjustment));

        return adjustmentResponseMapper.toResponse(completedAdjustment);
    }

    private String adjustmentExternalReference(AdjustmentRequestEntity adjustment) {
        return "ADJUSTMENT-" + adjustment.getId();
    }

    private String adjustmentDescription(CreateAdjustmentCommand command) {
        if (command.reasonDetail() == null || command.reasonDetail().isBlank()) {
            return "Adjustment: " + command.reasonCode();
        }

        return "Adjustment: " + command.reasonCode() + " - " + command.reasonDetail();
    }

    private AuditEventEntity auditEvent(CreateAdjustmentCommand command, AdjustmentRequestEntity adjustment) {
        return AuditEventEntity.builder()
                .eventType(AuditEventType.ADJUSTMENT_POSTED.name())
                .entityType(AuditEntityType.ADJUSTMENT.name())
                .entityId(adjustment.getId())
                .actorType(command.actorType().toAuditActorType())
                .actorRole(command.actorRole())
                .actorId(command.actorId())
                .correlationId(command.correlationId())
                .eventPayload(toJson(Map.of(
                        "adjustmentId", adjustment.getId().toString(),
                        "ledgerTransactionId", adjustment.getLedgerTransaction().getId().toString(),
                        "reasonCode", command.reasonCode().name()
                )))
                .build();
    }

    private OutboxEventEntity outboxEvent(CreateAdjustmentCommand command, AdjustmentRequestEntity adjustment) {
        return OutboxEventEntity.builder()
                .aggregateType(OutboxAggregateType.ADJUSTMENT.name())
                .aggregateId(adjustment.getId())
                .eventType(OutboxEventType.ADJUSTMENT_POSTED.eventName())
                .destination(OutboxDestination.LEDGER_EVENTS.destinationName())
                .correlationId(command.correlationId())
                .eventPayload(toJson(Map.of(
                        "adjustmentId", adjustment.getId().toString(),
                        "ledgerTransactionId", adjustment.getLedgerTransaction().getId().toString(),
                        "reasonCode", command.reasonCode().name(),
                        "postedAt", adjustment.getCompletedAt().toString()
                )))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event payload", exception);
        }
    }
}
