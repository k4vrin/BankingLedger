package dev.kavrin.banking_ledger.audit.application.service;

import dev.kavrin.banking_ledger.audit.api.dto.AuditEventResponse;
import dev.kavrin.banking_ledger.audit.application.query.SearchAuditEventsQuery;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditEventQueryUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository auditEventRepository;

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> search(SearchAuditEventsQuery query) {
        validate(query);
        var size = query.size() == 0 ? DEFAULT_PAGE_SIZE : query.size();
        var pageable = PageRequest.of(
                query.page(),
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        return auditEventRepository.findAll(specification(query), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse getById(UUID auditEventId) {
        return auditEventRepository.findById(auditEventId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Audit event not found: " + auditEventId,
                        "Audit event not found."
                ));
    }

    private void validate(SearchAuditEventsQuery query) {
        if (query.page() < 0) {
            throw invalid("Page must be zero or greater.");
        }
        if (query.size() < 0) {
            throw invalid("Page size must be zero or greater.");
        }
        if (query.size() > MAX_PAGE_SIZE) {
            throw invalid("Page size must not exceed " + MAX_PAGE_SIZE + ".");
        }
        if (query.createdFrom() != null && query.createdTo() != null && query.createdFrom().isAfter(query.createdTo())) {
            throw invalid("createdFrom must be before or equal to createdTo.");
        }
    }

    private BadRequestException invalid(String message) {
        return new BadRequestException(ApiErrorCode.Validation.INVALID_REQUEST, message, message);
    }

    private Specification<AuditEventEntity> specification(SearchAuditEventsQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            equal(predicates, criteriaBuilder, root.get("eventType"), name(query.eventType()));
            equal(predicates, criteriaBuilder, root.get("entityType"), name(query.entityType()));
            equal(predicates, criteriaBuilder, root.get("entityId"), query.entityId());
            equal(predicates, criteriaBuilder, root.get("actorType"), query.actorType());
            equal(predicates, criteriaBuilder, root.get("actorRole"), query.actorRole());
            equal(predicates, criteriaBuilder, root.get("actorId"), query.actorId());
            equal(predicates, criteriaBuilder, root.get("correlationId"), query.correlationId());

            if (query.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), query.createdFrom()));
            }
            if (query.createdTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), query.createdTo()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private <T> void equal(
            ArrayList<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            jakarta.persistence.criteria.Path<T> path,
            T value
    ) {
        if (value != null) {
            predicates.add(criteriaBuilder.equal(path, value));
        }
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private AuditEventResponse toResponse(AuditEventEntity event) {
        return new AuditEventResponse(
                event.getId(),
                AuditEventType.valueOf(event.getEventType()),
                AuditEntityType.valueOf(event.getEntityType()),
                event.getEntityId(),
                event.getActorType(),
                event.getActorRole(),
                event.getActorId(),
                event.getChannel(),
                event.getCorrelationId(),
                event.getCreatedAt(),
                event.getEventPayload()
        );
    }
}
