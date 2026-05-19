package dev.kavrin.banking_ledger.audit.api;

import dev.kavrin.banking_ledger.audit.api.dto.AuditEventResponse;
import dev.kavrin.banking_ledger.audit.application.query.SearchAuditEventsQuery;
import dev.kavrin.banking_ledger.audit.application.service.AuditEventQueryUseCase;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit/events")
@RequiredArgsConstructor
public class AuditEventController {

    private final AuditEventQueryUseCase auditEventQueryUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR', 'OPS_ADMIN')")
    public Page<AuditEventResponse> search(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) AuditActorType actorType,
            @RequestParam(required = false) AuditActorRole actorRole,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return auditEventQueryUseCase.search(new SearchAuditEventsQuery(
                eventType,
                entityType,
                entityId,
                actorType,
                actorRole,
                actorId,
                correlationId,
                createdFrom,
                createdTo,
                page,
                size
        ));
    }

    @GetMapping("/{auditEventId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'OPS_ADMIN')")
    public AuditEventResponse getById(@PathVariable UUID auditEventId) {
        return auditEventQueryUseCase.getById(auditEventId);
    }
}
