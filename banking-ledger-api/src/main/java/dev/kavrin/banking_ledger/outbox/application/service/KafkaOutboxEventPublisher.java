package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

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
        record.headers().add("schema-version", String.valueOf(event.getVersion()).getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to publish outbox event " + event.getId(), ex);
        }
    }
}

