package dev.kavrin.banking_ledger.reconciliation.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementItemRequest;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementItemStatus;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementBatchEntity;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class SettlementItemFactory {

    private final ObjectMapper objectMapper;

    public SettlementItemEntity fromRequest(SettlementBatchEntity batch, SettlementItemRequest request) {
        var status = parseStatus(request.status());
        var currencyCode = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        var externalReference = request.externalTransactionReference().trim();
        var metadataJson = serializeMetadata(request.metadata());

        return SettlementItemEntity.builder()
                .batch(batch)
                .source(batch.getSource())
                .externalTransactionReference(externalReference)
                .amountMinor(request.amountMinor())
                .currencyCode(currencyCode)
                .status(status)
                .settlementDate(request.settlementDate())
                .rawLineHash(canonicalHash(externalReference, request.amountMinor(), currencyCode, status, request, metadataJson))
                .metadataJson(metadataJson)
                .build();
    }

    public String canonicalHash(SettlementItemRequest request) {
        var status = parseStatus(request.status());
        var currencyCode = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        var externalReference = request.externalTransactionReference().trim();
        var metadataJson = serializeMetadata(request.metadata());
        return canonicalHash(externalReference, request.amountMinor(), currencyCode, status, request, metadataJson);
    }

    private SettlementItemStatus parseStatus(String rawStatus) {
        return SettlementItemStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
    }

    private String canonicalHash(
            String externalReference,
            long amountMinor,
            String currencyCode,
            SettlementItemStatus status,
            SettlementItemRequest request,
            String metadataJson
    ) {
        var canonicalLine = externalReference + "|"
                + amountMinor + "|"
                + currencyCode + "|"
                + status.name() + "|"
                + request.settlementDate() + "|"
                + (metadataJson == null ? "" : metadataJson);
        return DigestUtils.md5DigestAsHex(canonicalLine.getBytes(StandardCharsets.UTF_8));
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(metadata));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Settlement item metadata could not be serialized.", exception);
        }
    }
}
