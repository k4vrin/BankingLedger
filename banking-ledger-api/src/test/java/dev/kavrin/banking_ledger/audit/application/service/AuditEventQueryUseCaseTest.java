package dev.kavrin.banking_ledger.audit.application.service;

import dev.kavrin.banking_ledger.audit.application.query.SearchAuditEventsQuery;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventQueryUseCaseTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Test
    void searchAppliesDefaultPaginationAndMapsResponse() {
        var eventId = UUID.randomUUID();
        var entityId = UUID.randomUUID();
        var createdAt = OffsetDateTime.now();
        var event = AuditEventEntity.builder()
                .id(eventId)
                .eventType(AuditEventType.ACCOUNT_CREATED.name())
                .entityType(AuditEntityType.ACCOUNT.name())
                .entityId(entityId)
                .actorType(AuditActorType.SYSTEM)
                .actorRole(AuditActorRole.SYSTEM)
                .actorId("system")
                .channel("API")
                .correlationId("corr-1")
                .eventPayload("{}")
                .createdAt(createdAt)
                .build();
        when(auditEventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        var useCase = new AuditEventQueryUseCase(auditEventRepository);

        var page = useCase.search(new SearchAuditEventsQuery(
                AuditEventType.ACCOUNT_CREATED,
                AuditEntityType.ACCOUNT,
                entityId,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0
        ));

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(page.getContent()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(eventId);
            assertThat(response.eventType()).isEqualTo(AuditEventType.ACCOUNT_CREATED);
            assertThat(response.entityType()).isEqualTo(AuditEntityType.ACCOUNT);
            assertThat(response.payload()).isEqualTo("{}");
        });
    }

    @Test
    void searchRejectsInvalidDateRangeAndOversizedPage() {
        var useCase = new AuditEventQueryUseCase(auditEventRepository);
        var now = OffsetDateTime.now();

        assertThatThrownBy(() -> useCase.search(new SearchAuditEventsQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now.minusSeconds(1),
                0,
                20
        ))).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> useCase.search(new SearchAuditEventsQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                101
        ))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void getByIdReturnsStructuredNotFound() {
        var auditEventId = UUID.randomUUID();
        when(auditEventRepository.findById(auditEventId)).thenReturn(Optional.empty());
        var useCase = new AuditEventQueryUseCase(auditEventRepository);

        assertThatThrownBy(() -> useCase.getById(auditEventId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Audit event not found");
    }
}
