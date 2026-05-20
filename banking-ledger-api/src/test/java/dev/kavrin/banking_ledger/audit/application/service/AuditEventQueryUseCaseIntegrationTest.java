package dev.kavrin.banking_ledger.audit.application.service;

import dev.kavrin.banking_ledger.audit.application.query.SearchAuditEventsQuery;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditEventQueryUseCaseIntegrationTest {

    private final ArrayList<UUID> auditEventIds = new ArrayList<>();
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private AuditEventQueryUseCase auditEventQueryUseCase;

    @AfterEach
    void cleanup() {
        auditEventRepository.deleteAllById(auditEventIds);
    }

    @Test
    void searchAppliesComposedFiltersWithAndSemantics() {
        var entityId = UUID.randomUUID();
        var correlationId = "audit-query-" + UUID.randomUUID();
        var matching = saveEvent(
                AuditEventType.TRANSFER_REVERSED,
                AuditEntityType.TRANSFER,
                entityId,
                AuditActorType.EMPLOYEE,
                AuditActorRole.OPS_ADMIN,
                "ops-query-1",
                correlationId
        );
        saveEvent(
                AuditEventType.ACCOUNT_CREATED,
                AuditEntityType.ACCOUNT,
                UUID.randomUUID(),
                AuditActorType.SYSTEM,
                AuditActorRole.SYSTEM,
                "system",
                correlationId + "-other"
        );

        var page = auditEventQueryUseCase.search(new SearchAuditEventsQuery(
                AuditEventType.TRANSFER_REVERSED,
                AuditEntityType.TRANSFER,
                entityId,
                AuditActorType.EMPLOYEE,
                AuditActorRole.OPS_ADMIN,
                "ops-query-1",
                correlationId,
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusMinutes(1),
                0,
                20
        ));

        assertThat(page.getContent()).singleElement()
                .satisfies(response -> assertThat(response.id()).isEqualTo(matching.getId()));
    }

    @Test
    void searchReturnsEmptyPageWhenCorrelationIdDoesNotMatch() {
        var correlationId = "audit-empty-" + UUID.randomUUID();
        saveEvent(
                AuditEventType.ACCOUNT_CREATED,
                AuditEntityType.ACCOUNT,
                UUID.randomUUID(),
                AuditActorType.SYSTEM,
                AuditActorRole.SYSTEM,
                "system",
                correlationId
        );

        var page = auditEventQueryUseCase.search(new SearchAuditEventsQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                correlationId + "-missing",
                null,
                null,
                0,
                20
        ));

        assertThat(page.getContent()).isEmpty();
    }

    private AuditEventEntity saveEvent(
            AuditEventType eventType,
            AuditEntityType entityType,
            UUID entityId,
            AuditActorType actorType,
            AuditActorRole actorRole,
            String actorId,
            String correlationId
    ) {
        var event = auditEventRepository.save(AuditEventEntity.builder()
                .eventType(eventType.name())
                .entityType(entityType.name())
                .entityId(entityId)
                .actorType(actorType)
                .actorRole(actorRole)
                .actorId(actorId)
                .channel("API")
                .correlationId(correlationId)
                .eventPayload("{}")
                .build());
        auditEventIds.add(event.getId());
        return event;
    }
}
