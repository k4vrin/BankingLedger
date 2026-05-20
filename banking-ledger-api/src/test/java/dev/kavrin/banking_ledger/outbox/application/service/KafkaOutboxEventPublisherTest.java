package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishSendsPayloadToDestinationWithRequiredHeaders() {
        KafkaOutboxEventPublisher publisher = new KafkaOutboxEventPublisher(kafkaTemplate);
        OutboxEventEntity event = outboxEvent();
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);

        when(kafkaTemplate.send(recordCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("banking-ledger.ledger-events");
        assertThat(record.key()).isEqualTo(event.getId().toString());
        assertThat(record.value()).isEqualTo(event.getEventPayload());
        assertThat(headerValue(record, "outbox-event-id")).isEqualTo(event.getId().toString());
        assertThat(headerValue(record, "correlation-id")).isEqualTo("corr-123");
        assertThat(headerValue(record, "event-type")).isEqualTo("LedgerTransactionPosted");
        assertThat(headerValue(record, "schema-version")).isEqualTo("1");
    }

    @Test
    void publishWrapsKafkaFailureWithOutboxEventContext() {
        KafkaOutboxEventPublisher publisher = new KafkaOutboxEventPublisher(kafkaTemplate);
        OutboxEventEntity event = outboxEvent();
        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("send failed"));

        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class)))
                .thenReturn((CompletableFuture) failedFuture);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish outbox event " + event.getId());

        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.any(ProducerRecord.class));
    }

    private String headerValue(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private OutboxEventEntity outboxEvent() {
        return OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("LEDGER_TRANSACTION")
                .aggregateId(UUID.randomUUID())
                .eventType("LedgerTransactionPosted")
                .destination("banking-ledger.ledger-events")
                .correlationId("corr-123")
                .eventPayload("{\"amount\":\"10.00\"}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(OffsetDateTime.now())
                .version(2)
                .build();
    }
}
