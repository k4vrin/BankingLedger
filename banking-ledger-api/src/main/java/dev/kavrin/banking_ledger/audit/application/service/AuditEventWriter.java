package dev.kavrin.banking_ledger.audit.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
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

    private static final String DEFAULT_CHANNEL = "API";

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEventEntity write(
            AuditEventType eventType,
            AuditEntityType entityType,
            UUID entityId,
            AuditActorType actorType,
            AuditActorRole actorRole,
            String actorId,
            String correlationId,
            String channel,
            Map<String, ?> payload
    ) {
        var serializedPayload = serializePayload(payload);
        var event = AuditEventEntity.builder()
                .eventType(eventType.name())
                .entityType(entityType.name())
                .entityId(entityId)
                .actorType(actorType)
                .actorRole(actorRole)
                .actorId(actorId)
                .correlationId(resolveCorrelationId(correlationId))
                .channel(channel == null || channel.isBlank() ? DEFAULT_CHANNEL : channel.trim())
                .eventPayload(serializedPayload)
                .build();

        return auditEventRepository.save(event);
    }

    private String serializePayload(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        payload.keySet().forEach(this::rejectSensitivePayloadKey);
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
