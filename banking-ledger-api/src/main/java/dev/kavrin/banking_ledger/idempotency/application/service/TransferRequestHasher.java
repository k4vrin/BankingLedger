package dev.kavrin.banking_ledger.idempotency.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class TransferRequestHasher {

    private final ObjectMapper objectMapper;

    public String hash(CreateTransferCommand command) {
        try {
            var normalized = getTreeMap(command);

            byte[] json = objectMapper.writeValueAsBytes(normalized);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json);

            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash transfer request", ex);
        }
    }

    private TreeMap<String, Object> getTreeMap(CreateTransferCommand command) {
        var normalized = new TreeMap<String, Object>();

        normalized.put("sourceAccountId", command.sourceAccountId().toString());
        normalized.put("destinationAccountId", command.destinationAccountId().toString());
        normalized.put("currencyCode", command.currencyCode().value().trim().toUpperCase());
        normalized.put("amountMinor", command.amountMinor());
        normalized.put("externalReference", normalizeNullable(command.externalReference()));
        normalized.put("description", normalizeNullable(command.description()));
        normalized.put("actorType", command.actorType().name());
        return normalized;
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
