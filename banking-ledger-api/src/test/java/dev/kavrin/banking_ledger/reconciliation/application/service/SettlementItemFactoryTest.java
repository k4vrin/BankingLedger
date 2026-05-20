package dev.kavrin.banking_ledger.reconciliation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementItemRequest;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementItemStatus;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementBatchEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementItemFactoryTest {

    private final SettlementItemFactory factory = new SettlementItemFactory(new ObjectMapper());

    @Test
    void canonicalHashIsStableForEquivalentMetadataOrder() {
        var firstMetadata = new LinkedHashMap<String, Object>();
        firstMetadata.put("merchant", "demo");
        firstMetadata.put("terminal", "t1");

        var secondMetadata = new LinkedHashMap<String, Object>();
        secondMetadata.put("terminal", "t1");
        secondMetadata.put("merchant", "demo");

        var first = request(firstMetadata);
        var second = request(secondMetadata);

        assertThat(factory.canonicalHash(first)).isEqualTo(factory.canonicalHash(second));
    }

    @Test
    void fromRequestNormalizesFieldsAndStoresHash() {
        var batch = SettlementBatchEntity.builder()
                .id(UUID.randomUUID())
                .source("VISA")
                .referenceName("settlement.csv")
                .importedByActor("ops-1")
                .status(SettlementBatchStatus.IMPORTED)
                .build();

        var item = factory.fromRequest(batch, new SettlementItemRequest(
                " ext-1 ",
                100L,
                "usd",
                "settled",
                LocalDate.of(2026, 5, 20),
                Map.of("merchant", "demo")
        ));

        assertThat(item.getBatch()).isEqualTo(batch);
        assertThat(item.getSource()).isEqualTo("VISA");
        assertThat(item.getExternalTransactionReference()).isEqualTo("ext-1");
        assertThat(item.getCurrencyCode()).isEqualTo("USD");
        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.SETTLED);
        assertThat(item.getRawLineHash()).isNotBlank();
        assertThat(item.getMetadataJson()).contains("merchant");
    }

    private SettlementItemRequest request(Map<String, Object> metadata) {
        return new SettlementItemRequest(
                "ext-1",
                100L,
                "USD",
                "SETTLED",
                LocalDate.of(2026, 5, 20),
                metadata
        );
    }
}
