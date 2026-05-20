package dev.kavrin.banking_ledger.audit.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.audit.application.command.WriteAuditEventCommand;
import dev.kavrin.banking_ledger.audit.domain.model.*;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.shared.correlation.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditEventWriter {

    private static final String SYSTEM_ACTOR_ID = "system";
    private static final TypeReference<Map<String, Object>> PAYLOAD_MAP_TYPE = new TypeReference<>() {
    };

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEventEntity write(WriteAuditEventCommand command) {
        var actorContext = normalizeActor(command.actorType(), command.actorRole(), command.actorId(), command.channel());
        var serializedPayload = serializePayload(command.payload());
        var event = AuditEventEntity.builder()
                .eventType(command.eventType().name())
                .entityType(command.entityType().name())
                .entityId(command.entityId())
                .actorType(actorContext.actorType())
                .actorRole(actorContext.actorRole())
                .actorId(actorContext.actorId())
                .correlationId(resolveCorrelationId(command.correlationId()))
                .channel(resolveChannel(command.channel()).name())
                .eventPayload(serializedPayload)
                .build();

        return auditEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEventEntity write(
            AuditEventType eventType,
            AuditEntityType entityType,
            UUID entityId,
            AuditActorType actorType,
            AuditActorRole actorRole,
            String actorId,
            String correlationId,
            AuditChannel channel,
            Map<String, ?> payload
    ) {
        return write(new WriteAuditEventCommand(
                eventType,
                entityType,
                entityId,
                actorType,
                actorRole,
                actorId,
                correlationId,
                channel,
                payload
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEventEntity write(
            AuditEventType eventType,
            AuditEntityType entityType,
            UUID entityId,
            AuditRequestContext context,
            Map<String, ?> payload
    ) {
        return write(
                eventType,
                entityType,
                entityId,
                context.actorType(),
                context.actorRole(),
                context.actorId(),
                context.correlationId(),
                context.channel(),
                payload
        );
    }

    private ActorContext normalizeActor(
            AuditActorType actorType,
            AuditActorRole actorRole,
            String actorId,
            AuditChannel channel
    ) {
        var resolvedChannel = resolveChannel(channel);
        if (resolvedChannel != AuditChannel.API || actorType == null || actorType == AuditActorType.SYSTEM) {
            return new ActorContext(AuditActorType.SYSTEM, AuditActorRole.SYSTEM, SYSTEM_ACTOR_ID);
        }

        if (actorRole == null) {
            throw new IllegalArgumentException("Audit actor role is required for API audit events.");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("Audit actor id is required for API audit events.");
        }

        return new ActorContext(actorType, actorRole, actorId.trim());
    }

    private AuditChannel resolveChannel(AuditChannel channel) {
        return channel == null ? AuditChannel.API : channel;
    }

    private record ActorContext(AuditActorType actorType, AuditActorRole actorRole, String actorId) {
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return "{}";
        }
        var payloadMap = objectMapper.convertValue(payload, PAYLOAD_MAP_TYPE);
        if (payloadMap.isEmpty()) {
            return "{}";
        }
        payloadMap.keySet().forEach(this::rejectSensitivePayloadKey);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit payload could not be serialized.", exception);
        }
    }

    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId.trim();
        }
        return CorrelationIdFilter.currentCorrelationId();
    }

    private void rejectSensitivePayloadKey(String key) {
        var normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalized.contains("authorization")
                || normalized.contains("bearer")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("signing")
                || normalized.contains("verificationcode")) {
            throw new IllegalArgumentException("Audit payload contains sensitive field: " + key);
        }
    }
}
