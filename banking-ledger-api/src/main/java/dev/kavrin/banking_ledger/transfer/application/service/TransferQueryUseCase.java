package dev.kavrin.banking_ledger.transfer.application.service;

import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.transfer.api.dto.TransferResponse;
import dev.kavrin.banking_ledger.transfer.application.query.GetTransferByIdQuery;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferQueryUseCase {

    private final TransferRequestRepository transferRequestRepository;
    private final TransferResponseMapper transferResponseMapper;

    @Transactional(readOnly = true)
    public TransferResponse getById(GetTransferByIdQuery query) {
        return transferRequestRepository.findById(query.transferId())
                .map(transferResponseMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Transfer not found: " + query.transferId(),
                        "Transfer not found."
                ));
    }
}
