package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static dev.kavrin.banking_ledger.outbox.domain.model.OutboxEventSchemaVersions.*;

@Component
@RequiredArgsConstructor
public class KafkaOutboxEventPublisher implements OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(OutboxEventEntity event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getDestination(),
                event.getId().toString(),
                event.getEventPayload()
        );

        record.headers().add("outbox-event-id", event.getId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("correlation-id", event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-type", event.getEventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("schema-version", String.valueOf(schemaVersion(event)).getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to publish outbox event " + event.getId(), ex);
        }
    }

    private int schemaVersion(OutboxEventEntity event) {
        return switch (event.getEventType()) {
            case "LedgerTransactionPosted" -> LEDGER_TRANSACTION_POSTED_V1;
            case "LedgerTransactionReversed" -> LEDGER_TRANSACTION_REVERSED_V1;
            case "AdjustmentPosted" -> ADJUSTMENT_POSTED_V1;
            case "AccountBalanceChanged" -> ACCOUNT_BALANCE_CHANGED_V1;
            case "ReconciliationMismatchFound" -> RECONCILIATION_MISMATCH_FOUND_V1;
            case "ReconciliationCompleted" -> RECONCILIATION_COMPLETED_V1;
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + event.getEventType());
        };
    }
}
