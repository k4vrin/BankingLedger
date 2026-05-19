package dev.kavrin.banking_ledger.ledger.api;

import dev.kavrin.banking_ledger.ledger.api.dto.LedgerTransactionInvestigationResponse;
import dev.kavrin.banking_ledger.ledger.application.service.LedgerTransactionInvestigationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ops/ledger/transactions")
@RequiredArgsConstructor
public class LedgerInvestigationController {

    private final LedgerTransactionInvestigationUseCase investigationUseCase;

    @GetMapping("/{transactionId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'OPS_ADMIN', 'SERVICE')")
    public LedgerTransactionInvestigationResponse getByTransactionId(@PathVariable UUID transactionId) {
        return investigationUseCase.getByTransactionId(transactionId);
    }
}
