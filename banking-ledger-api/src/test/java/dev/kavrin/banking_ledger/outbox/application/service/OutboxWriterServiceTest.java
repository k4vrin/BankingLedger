package dev.kavrin.banking_ledger.outbox.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.outbox.application.command.WriteOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxAggregateType;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxDestination;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxEventType;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.shared.error.OutboxPayloadSerializationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OutboxWriterServiceTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxWriterService writer = new OutboxWriterService(outboxEventRepository, new ObjectMapper());

    @Test
    void writePersistsPendingEventWithSerializedPayloadAndMetadata() {
        ArgumentCaptor<OutboxEventEntity> eventCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        when(outboxEventRepository.save(eventCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        var aggregateId = UUID.randomUUID();

        writer.write(new WriteOutboxEventCommand(
                OutboxAggregateType.LEDGER_TRANSACTION.name(),
                aggregateId,
                OutboxEventType.LEDGER_TRANSACTION_POSTED,
                OutboxDestination.LEDGER_EVENTS,
                "corr-1",
                new Payload("value")
        ));

        var event = eventCaptor.getValue();
        assertThat(event.getAggregateType()).isEqualTo("LEDGER_TRANSACTION");
        assertThat(event.getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getEventType()).isEqualTo("LedgerTransactionPosted");
        assertThat(event.getDestination()).isEqualTo("banking-ledger.ledger-events");
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getEventPayload()).contains("\"name\":\"value\"");
    }

    @Test
    void writeRejectsUnserializablePayload() {
        var payload = new Object() {
            public Object getSelf() {
                return this;
            }
        };

        assertThatThrownBy(() -> writer.write(new WriteOutboxEventCommand(
                OutboxAggregateType.LEDGER_TRANSACTION.name(),
                UUID.randomUUID(),
                OutboxEventType.LEDGER_TRANSACTION_POSTED,
                OutboxDestination.LEDGER_EVENTS,
                "corr-1",
                payload
        )))
                .isInstanceOf(OutboxPayloadSerializationException.class);

        verify(outboxEventRepository, never()).save(any());
    }

    private record Payload(String name) {
    }
}
