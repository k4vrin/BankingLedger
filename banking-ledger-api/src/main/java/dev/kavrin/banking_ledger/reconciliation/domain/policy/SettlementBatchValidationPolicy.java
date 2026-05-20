package dev.kavrin.banking_ledger.reconciliation.domain.policy;

import dev.kavrin.banking_ledger.reconciliation.api.dto.CreateSettlementBatchRequest;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementItemRequest;
import dev.kavrin.banking_ledger.reconciliation.config.ReconciliationImportProperties;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementItemStatus;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SettlementBatchValidationPolicy {

    private final ReconciliationImportProperties properties;

    public SettlementBatchValidationResult validate(CreateSettlementBatchRequest request) {
        var errors = new ArrayList<SettlementBatchValidationError>();

        if (request == null) {
            errors.add(new SettlementBatchValidationError("request", "request is required"));
            return new SettlementBatchValidationResult(errors);
        }

        validateSource(request, errors);
        validateItems(request, errors);

        return new SettlementBatchValidationResult(errors);
    }

    private void validateSource(
            CreateSettlementBatchRequest request,
            ArrayList<SettlementBatchValidationError> errors
    ) {
        if (request.source() == null || request.source().isBlank()) {
            errors.add(new SettlementBatchValidationError("source", "source is required"));
        }
    }

    private void validateItems(
            CreateSettlementBatchRequest request,
            ArrayList<SettlementBatchValidationError> errors
    ) {
        if (request.items() == null || request.items().isEmpty()) {
            errors.add(new SettlementBatchValidationError("items", "items must not be empty"));
            return;
        }

        if (request.items().size() > properties.maxBatchSize()) {
            errors.add(new SettlementBatchValidationError(
                    "items",
                    "items must contain at most " + properties.maxBatchSize() + " entries"
            ));
        }

        var references = new HashSet<String>();

        for (int index = 0; index < request.items().size(); index++) {
            SettlementItemRequest item = request.items().get(index);
            if (item == null) {
                errors.add(new SettlementBatchValidationError("items[" + index + "]", "item is required"));
                continue;
            }

            validateExternalReference(item, index, references, errors);
            validateCurrency(item, index, errors);
            validateStatusAndAmount(item, index, errors);
        }
    }

    private void validateExternalReference(
            SettlementItemRequest item,
            int index,
            HashSet<String> references,
            ArrayList<SettlementBatchValidationError> errors
    ) {
        if (item.externalTransactionReference() == null || item.externalTransactionReference().isBlank()) {
            errors.add(new SettlementBatchValidationError(
                    "items[" + index + "].externalTransactionReference",
                    "externalTransactionReference is required"
            ));
            return;
        }

        String normalizedReference = item.externalTransactionReference().trim().toUpperCase(Locale.ROOT);
        if (!references.add(normalizedReference)) {
            errors.add(new SettlementBatchValidationError(
                    "items[" + index + "].externalTransactionReference",
                    "externalTransactionReference must be unique within the batch"
            ));
        }
    }

    private void validateCurrency(
            SettlementItemRequest item,
            int index,
            ArrayList<SettlementBatchValidationError> errors
    ) {
        try {
            CurrencyCode.of(item.currencyCode());
        } catch (IllegalArgumentException exception) {
            errors.add(new SettlementBatchValidationError(
                    "items[" + index + "].currencyCode",
                    "currencyCode must be exactly 3 uppercase letters"
            ));
        }
    }

    private void validateStatusAndAmount(
            SettlementItemRequest item,
            int index,
            ArrayList<SettlementBatchValidationError> errors
    ) {
        if (item.status() == null || item.status().isBlank()) {
            errors.add(new SettlementBatchValidationError(
                    "items[" + index + "].status",
                    "status is required"
            ));
            return;
        }

        SettlementItemStatus status;
        try {
            status = SettlementItemStatus.valueOf(item.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            errors.add(new SettlementBatchValidationError(
                    "items[" + index + "].status",
                    "status is not supported"
            ));
            return;
        }

        if (status.requiresPositiveAmount()
                && (item.amountMinor() == null || item.amountMinor() <= 0)) {
            errors.add(new SettlementBatchValidationError(
                    "items[" + index + "].amountMinor",
                    "amountMinor must be positive for " + status + " settlement items"
            ));
        }
    }
}
