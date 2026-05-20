package dev.kavrin.banking_ledger.outbox.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.outbox.application.command.WriteOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.shared.error.OutboxPayloadSerializationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxWriterService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventEntity write(WriteOutboxEventCommand command) {
        String payloadJson = serializePayload(command.payload());

        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType(command.aggregateType())
                .aggregateId(command.aggregateId())
                .eventType(command.eventType().eventName())
                .destination(command.destination().destinationName())
                .correlationId(command.correlationId())
                .eventPayload(payloadJson)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();

        return outboxEventRepository.save(event);
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new OutboxPayloadSerializationException(
                    "Failed to serialize outbox payload",
                    ex
            );
        }
    }
}
