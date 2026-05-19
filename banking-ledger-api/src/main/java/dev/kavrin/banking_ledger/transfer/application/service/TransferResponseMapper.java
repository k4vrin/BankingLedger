package dev.kavrin.banking_ledger.transfer.application.service;

import dev.kavrin.banking_ledger.transfer.api.dto.TransferResponse;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestEntity;
import org.springframework.stereotype.Component;

@Component
class TransferResponseMapper {

    TransferResponse toResponse(TransferRequestEntity transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccount().getId(),
                transfer.getDestinationAccount().getId(),
                transfer.getStatus(),
                transfer.getCurrencyCode(),
                transfer.getAmountMinor(),
                transfer.getLedgerTransaction() == null ? null : transfer.getLedgerTransaction().getId(),
                transfer.getExternalReference(),
                transfer.getDescription(),
                transfer.getRequestedAt(),
                transfer.getCompletedAt(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt(),
                transfer.getFailureReasonCode(),
                transfer.getFailureReasonDetail()
        );
    }
}
