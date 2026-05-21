package dev.kavrin.banking_ledger.reconciliation.application.service;

import dev.kavrin.banking_ledger.audit.application.command.WriteAuditEventCommand;
import dev.kavrin.banking_ledger.audit.application.service.AuditEventWriter;
import dev.kavrin.banking_ledger.audit.domain.model.*;
import dev.kavrin.banking_ledger.outbox.application.command.WriteOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.application.service.OutboxWriterService;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxAggregateType;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxDestination;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxEventType;
import dev.kavrin.banking_ledger.outbox.domain.model.ReconciliationMismatchFoundPayload;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementBatchResponse;
import dev.kavrin.banking_ledger.reconciliation.application.command.CreateSettlementBatchCommand;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationSeverity;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.policy.SettlementBatchValidationPolicy;
import dev.kavrin.banking_ledger.reconciliation.persistence.*;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreateSettlementBatchUseCase {

    private final SettlementBatchRepository batchRepository;
    private final SettlementItemRepository itemRepository;
    private final ReconciliationResultRepository resultRepository;
    private final SettlementBatchValidationPolicy validationPolicy;
    private final SettlementItemFactory itemFactory;
    private final ReconciliationMatcher reconciliationMatcher;
    private final ReconciliationResponseMapper responseMapper;
    private final AuditEventWriter auditEventWriter;
    private final OutboxWriterService outboxWriterService;

    @Transactional
    public SettlementBatchResponse handle(CreateSettlementBatchCommand command) {
        var validation = validationPolicy.validate(command.request());
        if (validation.hasErrors()) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.VALIDATION_ERROR,
                    validation.errors().toString(),
                    "Settlement batch validation failed."
            );
        }

        var request = command.request();
        var now = OffsetDateTime.now();
        var batch = batchRepository.save(SettlementBatchEntity.builder()
                .source(request.source().trim())
                .referenceName(request.referenceName().trim())
                .importedByActor(command.actorId())
                .correlationId(command.correlationId())
                .status(SettlementBatchStatus.IMPORTED)
                .importedAt(now)
                .itemCount(request.items().size())
                .matchedCount(0)
                .mismatchCount(0)
                .build());
        batchRepository.flush();

        var items = request.items().stream()
                .map(item -> itemFactory.fromRequest(batch, item))
                .toList();
        var savedItems = itemRepository.saveAll(items);

        var results = resultRepository.saveAll(reconciliationMatcher.match(batch, savedItems));
        long matchedCount = results.stream().filter(result -> result.getSeverity() == ReconciliationSeverity.INFO).count();
        int mismatchCount = Math.toIntExact(results.size() - matchedCount);

        batch.setStatus(SettlementBatchStatus.COMPLETED);
        batch.setMatchedCount(Math.toIntExact(matchedCount));
        batch.setMismatchCount(mismatchCount);
        batch.setCompletedAt(OffsetDateTime.now());

        writeImportAudit(command, batch);
        writeCompletedAudit(command, batch);
        writeOutboxEvents(command, batch, savedItems, results);

        return responseMapper.toResponse(batch, savedItems, results);
    }

    private void writeImportAudit(CreateSettlementBatchCommand command, SettlementBatchEntity batch) {
        auditEventWriter.write(new WriteAuditEventCommand(
                AuditEventType.RECONCILIATION_BATCH_IMPORTED,
                AuditEntityType.RECONCILIATION_BATCH,
                batch.getId(),
                command.actorType(),
                command.actorRole(),
                command.actorId(),
                command.correlationId(),
                AuditChannel.API,
                importedAuditPayload(batch)
        ));
    }

    private void writeCompletedAudit(CreateSettlementBatchCommand command, SettlementBatchEntity batch) {
        auditEventWriter.write(new WriteAuditEventCommand(
                AuditEventType.RECONCILIATION_COMPLETED,
                AuditEntityType.RECONCILIATION_BATCH,
                batch.getId(),
                command.actorType(),
                command.actorRole(),
                command.actorId(),
                command.correlationId(),
                AuditChannel.API,
                completedAuditPayload(batch)
        ));
    }

    private ReconciliationBatchImportedAuditPayload importedAuditPayload(SettlementBatchEntity batch) {
        return new ReconciliationBatchImportedAuditPayload(
                batch.getId(),
                batch.getSource(),
                batch.getItemCount(),
                batch.getMatchedCount(),
                batch.getMismatchCount(),
                batch.getCorrelationId()
        );
    }

    private ReconciliationCompletedAuditPayload completedAuditPayload(SettlementBatchEntity batch) {
        return new ReconciliationCompletedAuditPayload(
                batch.getId(),
                batch.getSource(),
                batch.getItemCount(),
                batch.getMatchedCount(),
                batch.getMismatchCount(),
                batch.getCorrelationId()
        );
    }

    private void writeOutboxEvents(
            CreateSettlementBatchCommand command,
            SettlementBatchEntity batch,
            java.util.List<SettlementItemEntity> items,
            java.util.List<dev.kavrin.banking_ledger.reconciliation.persistence.ReconciliationResultEntity> results
    ) {
        var itemsById = items.stream().collect(java.util.stream.Collectors.toMap(SettlementItemEntity::getId, item -> item));
        for (var result : results) {
            if (result.getSeverity() != ReconciliationSeverity.CRITICAL) {
                continue;
            }
            var item = result.getItem() == null ? null : itemsById.get(result.getItem().getId());
            outboxWriterService.write(new WriteOutboxEventCommand(
                    OutboxAggregateType.RECONCILIATION_BATCH.name(),
                    batch.getId(),
                    OutboxEventType.RECONCILIATION_MISMATCH_FOUND,
                    OutboxDestination.RECONCILIATION_EVENTS,
                    command.correlationId(),
                    new ReconciliationMismatchFoundPayload(
                            result.getId(),
                            batch.getId(),
                            result.getMismatchType().name(),
                            item == null ? null : item.getExternalTransactionReference(),
                            result.getCreatedAt() == null ? OffsetDateTime.now() : result.getCreatedAt()
                    )
            ));
        }

        outboxWriterService.write(new WriteOutboxEventCommand(
                OutboxAggregateType.RECONCILIATION_BATCH.name(),
                batch.getId(),
                OutboxEventType.RECONCILIATION_COMPLETED,
                OutboxDestination.RECONCILIATION_EVENTS,
                command.correlationId(),
                Map.of(
                        "batchId", batch.getId().toString(),
                        "source", batch.getSource(),
                        "itemCount", batch.getItemCount(),
                        "matchedCount", batch.getMatchedCount(),
                        "mismatchCount", batch.getMismatchCount()
                )
        ));
    }
}
