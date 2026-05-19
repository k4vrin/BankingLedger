package dev.kavrin.banking_ledger.audit.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventWriterTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Test
    void writerPersistsNormalizedAuditEvent() {
        when(auditEventRepository.save(any(AuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var writer = new AuditEventWriter(auditEventRepository, new ObjectMapper());
        var entityId = UUID.randomUUID();

        writer.write(
                AuditEventType.LEDGER_TRANSACTION_POSTED,
                AuditEntityType.LEDGER_TRANSACTION,
                entityId,
                AuditActorType.EMPLOYEE,
                AuditActorRole.AUDITOR,
                "auditor-1",
                "corr-1",
                " API ",
                Map.of("transactionId", entityId.toString(), "amountMinor", 100L)
        );

        var captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(auditEventRepository).save(captor.capture());
        var event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo("LEDGER_TRANSACTION_POSTED");
        assertThat(event.getEntityType()).isEqualTo("LEDGER_TRANSACTION");
        assertThat(event.getEntityId()).isEqualTo(entityId);
        assertThat(event.getActorType()).isEqualTo(AuditActorType.EMPLOYEE);
        assertThat(event.getActorRole()).isEqualTo(AuditActorRole.AUDITOR);
        assertThat(event.getActorId()).isEqualTo("auditor-1");
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getChannel()).isEqualTo("API");
        assertThat(event.getEventPayload()).contains("\"amountMinor\":100");
    }

    @Test
    void writerRejectsSensitivePayloadKeys() {
        var writer = new AuditEventWriter(auditEventRepository, new ObjectMapper());

        assertThatThrownBy(() -> writer.write(
                AuditEventType.ACCOUNT_CREATED,
                AuditEntityType.ACCOUNT,
                UUID.randomUUID(),
                AuditActorType.SYSTEM,
                AuditActorRole.SYSTEM,
                "system",
                "corr-1",
                "API",
                Map.of("authorizationHeader", "Bearer secret")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensitive field");
    }
}
