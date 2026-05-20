package dev.kavrin.banking_ledger.adjustment.application.service;

import dev.kavrin.banking_ledger.adjustment.api.dto.AdjustmentResponse;
import dev.kavrin.banking_ledger.adjustment.application.command.CreateAdjustmentCommand;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentStatus;
import dev.kavrin.banking_ledger.adjustment.domain.policy.AdjustmentValidationPolicy;
import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestEntity;
import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestRepository;
import dev.kavrin.banking_ledger.adjustment.persistence.mapper.AdjustmentResponseMapper;
import dev.kavrin.banking_ledger.audit.application.command.WriteAuditEventCommand;
import dev.kavrin.banking_ledger.audit.application.service.AuditEventWriter;
import dev.kavrin.banking_ledger.audit.domain.model.AdjustmentPostedAuditPayload;
import dev.kavrin.banking_ledger.audit.domain.model.AuditChannel;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.service.PostLedgerTransactionUseCase;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.outbox.application.command.WriteOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.application.service.OutboxWriterService;
import dev.kavrin.banking_ledger.outbox.domain.model.AdjustmentPostedPayload;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxAggregateType;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxDestination;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class CreateAdjustmentUseCase {

    private final AdjustmentRequestRepository adjustmentRequestRepository;
    private final PostLedgerTransactionUseCase postLedgerTransactionUseCase;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final AdjustmentResponseMapper adjustmentResponseMapper;
    private final AuditEventWriter auditEventWriter;
    private final OutboxWriterService outboxWriterService;

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
                command.actorRole(),
                command.actorId(),
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

        writeAuditEvent(command, completedAdjustment);
        writeOutboxEvent(command, completedAdjustment);

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

    private void writeAuditEvent(CreateAdjustmentCommand command, AdjustmentRequestEntity adjustment) {
        auditEventWriter.write(new WriteAuditEventCommand(
                AuditEventType.ADJUSTMENT_POSTED,
                AuditEntityType.ADJUSTMENT,
                adjustment.getId(),
                command.actorType().toAuditActorType(),
                command.actorRole(),
                command.actorId(),
                command.correlationId(),
                AuditChannel.API,
                new AdjustmentPostedAuditPayload(
                        adjustment.getId(),
                        adjustment.getLedgerTransaction().getId(),
                        command.reasonCode().name()
                )
        ));
    }

    private void writeOutboxEvent(CreateAdjustmentCommand command, AdjustmentRequestEntity adjustment) {
        outboxWriterService.write(new WriteOutboxEventCommand(
                OutboxAggregateType.ADJUSTMENT.name(),
                adjustment.getId(),
                OutboxEventType.ADJUSTMENT_POSTED,
                OutboxDestination.LEDGER_EVENTS,
                command.correlationId(),
                new AdjustmentPostedPayload(
                        adjustment.getId(),
                        adjustment.getLedgerTransaction().getId(),
                        command.reasonCode().name(),
                        adjustment.getCompletedAt()
                )
        ));
    }
}
