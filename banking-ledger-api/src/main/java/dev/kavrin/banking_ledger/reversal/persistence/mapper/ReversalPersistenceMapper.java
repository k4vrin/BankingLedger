package dev.kavrin.banking_ledger.reversal.persistence.mapper;

import dev.kavrin.banking_ledger.reversal.api.dto.ReversalResponse;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalEntity;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class ReversalPersistenceMapper {

    public ReversalResponse toResponse(ReversalEntity reversal) {
        return new ReversalResponse(
                reversal.getId(),
                reversal.getOriginalTransfer().getId(),
                reversal.getOriginalLedgerTransaction().getId(),
                reversal.getReversalLedgerTransaction() == null
                        ? null
                        : reversal.getReversalLedgerTransaction().getId(),
                reversal.getStatus(),
                reversal.getReasonCode(),
                reversal.getReasonDetail(),
                reversal.getRequestedByActorType(),
                reversal.getRequestedByActorRole() == null
                        ? null
                        : reversal.getRequestedByActorRole().name(),
                reversal.getRequestedByActorId(),
                reversal.getCorrelationId(),
                reversal.getRequestedAt(),
                reversal.getCompletedAt(),
                reversal.getFailureReasonCode(),
                reversal.getFailureReasonDetail(),
                reversal.getCreatedAt(),
                reversal.getUpdatedAt()
        );
    }

}
