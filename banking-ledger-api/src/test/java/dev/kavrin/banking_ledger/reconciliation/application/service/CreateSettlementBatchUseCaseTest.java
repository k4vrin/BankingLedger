package dev.kavrin.banking_ledger.reconciliation.application.service;

import dev.kavrin.banking_ledger.audit.application.service.AuditEventWriter;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.outbox.application.command.WriteOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.application.service.OutboxWriterService;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxEventType;
import dev.kavrin.banking_ledger.reconciliation.api.dto.CreateSettlementBatchRequest;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementBatchResponse;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementItemRequest;
import dev.kavrin.banking_ledger.reconciliation.application.command.CreateSettlementBatchCommand;
import dev.kavrin.banking_ledger.reconciliation.domain.model.*;
import dev.kavrin.banking_ledger.reconciliation.domain.policy.SettlementBatchValidationPolicy;
import dev.kavrin.banking_ledger.reconciliation.domain.policy.SettlementBatchValidationResult;
import dev.kavrin.banking_ledger.reconciliation.persistence.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateSettlementBatchUseCaseTest {

    private final SettlementBatchRepository batchRepository = mock(SettlementBatchRepository.class);
    private final SettlementItemRepository itemRepository = mock(SettlementItemRepository.class);
    private final ReconciliationResultRepository resultRepository = mock(ReconciliationResultRepository.class);
    private final SettlementBatchValidationPolicy validationPolicy = mock(SettlementBatchValidationPolicy.class);
    private final SettlementItemFactory itemFactory = mock(SettlementItemFactory.class);
    private final ReconciliationMatcher reconciliationMatcher = mock(ReconciliationMatcher.class);
    private final ReconciliationResponseMapper responseMapper = mock(ReconciliationResponseMapper.class);
    private final AuditEventWriter auditEventWriter = mock(AuditEventWriter.class);
    private final OutboxWriterService outboxWriterService = mock(OutboxWriterService.class);

    private final CreateSettlementBatchUseCase useCase = new CreateSettlementBatchUseCase(
            batchRepository,
            itemRepository,
            resultRepository,
            validationPolicy,
            itemFactory,
            reconciliationMatcher,
            responseMapper,
            auditEventWriter,
            outboxWriterService
    );

    @Test
    void handlePersistsBatchRunsReconciliationAndWritesAuditAndOutboxEvents() {
        UUID batchId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        var request = request();
        var command = new CreateSettlementBatchCommand(
                request,
                AuditActorType.EMPLOYEE,
                AuditActorRole.OPS_ADMIN,
                "ops-1",
                "corr-1"
        );
        var item = SettlementItemEntity.builder()
                .id(itemId)
                .externalTransactionReference("ext-1")
                .status(SettlementItemStatus.SETTLED)
                .build();
        var result = ReconciliationResultEntity.builder()
                .id(resultId)
                .item(item)
                .mismatchType(ReconciliationMismatchType.AMOUNT_MISMATCH)
                .severity(ReconciliationSeverity.CRITICAL)
                .status(ReconciliationResultStatus.OPEN)
                .createdAt(OffsetDateTime.now())
                .build();
        var response = new SettlementBatchResponse(
                batchId,
                "VISA",
                "settlement.csv",
                "ops-1",
                "corr-1",
                SettlementBatchStatus.COMPLETED,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1,
                0,
                1,
                List.of(),
                List.of()
        );

        when(validationPolicy.validate(request)).thenReturn(new SettlementBatchValidationResult(List.of()));
        when(batchRepository.save(any())).thenAnswer(invocation -> {
            SettlementBatchEntity batch = invocation.getArgument(0);
            batch.setId(batchId);
            return batch;
        });
        when(itemFactory.fromRequest(any(), any())).thenReturn(item);
        when(itemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reconciliationMatcher.match(any(), any())).thenReturn(List.of(result));
        when(resultRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(responseMapper.toResponse(any(), any(), any())).thenReturn(response);

        var actual = useCase.handle(command);

        assertThat(actual).isEqualTo(response);
        verify(auditEventWriter).write(org.mockito.ArgumentMatchers.argThat(audit ->
                audit.eventType().name().equals("RECONCILIATION_BATCH_IMPORTED")
        ));
        verify(auditEventWriter).write(org.mockito.ArgumentMatchers.argThat(audit ->
                audit.eventType().name().equals("RECONCILIATION_COMPLETED")
        ));

        ArgumentCaptor<WriteOutboxEventCommand> outboxCaptor = ArgumentCaptor.forClass(WriteOutboxEventCommand.class);
        verify(outboxWriterService, org.mockito.Mockito.times(2)).write(outboxCaptor.capture());
        assertThat(outboxCaptor.getAllValues())
                .extracting(WriteOutboxEventCommand::eventType)
                .containsExactlyInAnyOrder(
                        OutboxEventType.RECONCILIATION_MISMATCH_FOUND,
                        OutboxEventType.RECONCILIATION_COMPLETED
                );
    }

    private CreateSettlementBatchRequest request() {
        return new CreateSettlementBatchRequest(
                "VISA",
                "settlement.csv",
                "ops-1",
                null,
                List.of(new SettlementItemRequest(
                        "ext-1",
                        100L,
                        "USD",
                        "SETTLED",
                        LocalDate.of(2026, 5, 20),
                        Map.of()
                ))
        );
    }
}
